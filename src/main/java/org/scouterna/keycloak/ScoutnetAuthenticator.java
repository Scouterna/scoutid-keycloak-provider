package org.scouterna.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.scouterna.keycloak.client.ScoutnetClient;
import org.scouterna.keycloak.client.dto.*; // Added rich profile DTOs

/**
 * Keycloak Authenticator that validates credentials against an external Scoutnet API.
 * It uses a two-step process: authenticate to get a token, then use the token to fetch the full user profile.
 * It then creates or updates the Keycloak user with the rich profile data (addresses, memberships, etc.).
 */
public class ScoutnetAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(ScoutnetAuthenticator.class);
    private final ScoutnetClient scoutnetClient;

    // Initialize the client via the constructor (from the user_creation branch)
    public ScoutnetAuthenticator() {
        this.scoutnetClient = new ScoutnetClient();
    }

    /**
     * This method is called when the authenticator is first executed in the flow.
     * Its ONLY job is to display the username and password form to the user.
     * (Retained from the main branch)
     */
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        log.info("Displaying login form for Scoutnet authentication.");
        // This will render the standard login.ftl template
        context.challenge(context.form().createLoginUsernamePassword());
    }

    /**
     * This method is called ONLY after the user has submitted the form.
     * Contains the full authentication and user creation/update logic.
     * (Refactored logic from the user_creation branch into the correct 'action' method)
     */
    @Override
    public void action(AuthenticationFlowContext context) {
        log.info("Processing submitted login form for Scoutnet authentication.");
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        // Basic validation
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            failAuthentication(context, username, "Please provide both a username and a password.");
            return;
        }

        // Step 1: Authenticate and get the token
        AuthResponse authResponse = scoutnetClient.authenticate(username, password);
        if (authResponse == null || authResponse.getToken() == null || authResponse.getToken().isEmpty()) {
            failAuthentication(context, username, "Invalid username or password.");
            return;
        }

        // Step 2: Use the token to fetch the full profile
        Profile profile = scoutnetClient.getProfile(authResponse.getToken());
        if (profile == null) {
            failAuthentication(context, username, "Could not retrieve user profile from Scoutnet after successful login.");
            return;
        }

        // Step 3: Find or create the Keycloak user
        String keycloakUsername = "scoutnet-" + profile.getMemberNo();
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), keycloakUsername);

        if (user == null) {
            log.infof("First time login for Scoutnet user: %d. Creating new Keycloak user: %s.", profile.getMemberNo(), keycloakUsername);
            user = context.getSession().users().addUser(context.getRealm(), keycloakUsername);
            user.setEnabled(true);
        } else {
            log.infof("Updating existing Keycloak user: %s from Scoutnet profile.", keycloakUsername);
        }

        // Step 4: Update the user with the rich profile data
        updateUserFromProfile(user, profile);

        context.setUser(user);
        context.success();
    }

    /**
     * Populates the Keycloak UserModel with rich data from the Scoutnet Profile.
     * (Helper method imported from the user_creation branch)
     */
    private void updateUserFromProfile(UserModel user, Profile profile) {
        // --- Basic Info ---
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        user.setEmail(profile.getEmail());

        // --- Store extra data as custom attributes ---
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(profile.getMemberNo()));
        user.setSingleAttribute("scoutnet_dob", profile.getDob());
        // user.setSingleAttribute("scouternet_sex", profile.getSex());

        // --- Process and store primary address ---
        if (profile.getAddresses() != null) {
            profile.getAddresses().values().stream()
                .filter(Address::isPrimary)
                .findFirst()
                .ifPresent(primaryAddress -> {
                    // Map to standard OIDC address claim names for better compatibility
                    user.setSingleAttribute("street_address", primaryAddress.getAddressLine1());
                    user.setSingleAttribute("postal_code", primaryAddress.getZipCode());
                    user.setSingleAttribute("locality", primaryAddress.getCity()); // OIDC term for city
                    user.setSingleAttribute("country", primaryAddress.getCountryCode());
                });
        }

        // --- Process and store primary group membership ---
        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            profile.getMemberships().getGroup().values().stream()
                .filter(GroupMembership::isPrimary)
                .findFirst()
                .ifPresent(primaryMembership -> {
                    Group group = primaryMembership.getGroup();
                    if (group != null) {
                        user.setSingleAttribute("scoutnet_primary_group_name", group.getName());
                        user.setSingleAttribute("scoutnet_primary_group_no", String.valueOf(group.getGroupNo()));
                    }
                });
        }
    }

    /**
     * Helper to log and return an error challenge to the user.
     * (Helper method imported from the user_creation branch)
     */
    private void failAuthentication(AuthenticationFlowContext context, String username, String error) {
        context.getEvent().user(username).error("invalid_grant");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
            context.form().setError(error).createLoginPassword());
    }

    // --- Boilerplate methods unchanged ---
    @Override
    public boolean requiresUser() { return false; }
    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}
    @Override
    public void close() {}
}