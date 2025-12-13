package se.scouterna.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import se.scouterna.keycloak.client.ScoutnetClient;
import se.scouterna.keycloak.client.dto.*; // Added rich profile DTOs

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        // Step 1: Try persistent token with proper UUID app_id
        AuthResponse authResponse = scoutnetClient.authenticateWithAppId(username, password, 
            "keycloak-scoutid", "Keycloak ScoutID Provider", "Keycloak Server");
        if (authResponse == null || authResponse.getToken() == null || authResponse.getToken().isEmpty()) {
            failAuthentication(context, username, "Invalid username or password.");
            return;
        }

        // Step 2: Use the token to fetch the full profile
        Profile profile = scoutnetClient.getProfile(authResponse.getToken());
        if (profile == null) {
            log.warnf("Persistent token failed, trying temporary token for user: %s", username);
            // Fallback to temporary token
            AuthResponse tempAuthResponse = scoutnetClient.authenticate(username, password);
            if (tempAuthResponse != null && tempAuthResponse.getToken() != null) {
                profile = scoutnetClient.getProfile(tempAuthResponse.getToken());
                if (profile != null) {
                    authResponse = tempAuthResponse; // Use the working token
                    log.infof("Temporary token worked for user: %s", username);
                }
            }
            
            if (profile == null) {
                failAuthentication(context, username, "Could not retrieve user profile from Scoutnet after successful login.");
                return;
            }
        }

        // Step 2b: Fetch roles information (optional - can be stored for quick access)
        Roles roles = scoutnetClient.getRoles(authResponse.getToken());
        if (roles == null) {
            log.infof("Could not retrieve user roles from Scoutnet after successful login.");
        }

        // Store the persistent token for later use
        String persistentToken = authResponse.getToken();

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

        // Step 4: Update the user with essential data and optionally roles
        updateUserFromProfile(user, profile, persistentToken, roles);

        context.setUser(user);
        context.getAuthenticationSession().removeAuthNote("username");
        context.success();
    }

    private void updateUserFromProfile(UserModel user, Profile profile, String persistentToken, Roles roles) {
        // --- Basic Info ---
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        user.setEmail(profile.getEmail());

        // --- Essential Attributes ---
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(profile.getMemberNo()));
        user.setSingleAttribute("scoutnet_token", persistentToken);
        user.setSingleAttribute("scoutnet_dob", profile.getDob());

        // --- Roles (stored for authorization - legitimate business need) ---
        if (roles != null) {
            List<String> roleList = parseAndFlattenRoles(roles);
            user.setAttribute("roles", roleList);
        }
        


        // --- Primary Group (for basic identity) ---
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

    private List<String> parseAndFlattenRoles(Roles roles) {
        Set<String> roleSet = new HashSet<>();
        
        Map<String, Map<String, Map<String, String>>> allRolesMap = new HashMap<>();

        // Aggregate all role types
        if (roles.getOrganisation() != null) allRolesMap.put("organisation", roles.getOrganisation());
        if (roles.getGroup() != null) allRolesMap.put("group", roles.getGroup());
        if (roles.getRegion() != null) allRolesMap.put("region", roles.getRegion());
        if (roles.getProject() != null) allRolesMap.put("project", roles.getProject());
        if (roles.getNetwork() != null) allRolesMap.put("network", roles.getNetwork());
        if (roles.getCorps() != null) allRolesMap.put("corps", roles.getCorps());
        if (roles.getDistrict() != null) allRolesMap.put("district", roles.getDistrict());
        if (roles.getTroop() != null) allRolesMap.put("troop", roles.getTroop());
        if (roles.getPatrol() != null) allRolesMap.put("patrol", roles.getPatrol());

        // Flatten roles with wildcards
        for (Map.Entry<String, Map<String, Map<String, String>>> roleTypeEntry : allRolesMap.entrySet()) {
            String roleType = roleTypeEntry.getKey();
            Map<String, Map<String, String>> rolesForType = roleTypeEntry.getValue();

            if (rolesForType != null && !rolesForType.isEmpty()) {
                for (Map.Entry<String, Map<String, String>> roleTypeIdEntry : rolesForType.entrySet()) {
                    String roleTypeId = roleTypeIdEntry.getKey();
                    Map<String, String> rolesForTypeId = roleTypeIdEntry.getValue();
                    
                    if (rolesForTypeId != null && !rolesForTypeId.isEmpty()) {
                        for (Map.Entry<String, String> finalRoleEntry : rolesForTypeId.entrySet()) {
                            String roleName = finalRoleEntry.getValue();

                            // Generate wildcard combinations
                            roleSet.add(roleType + ":" + roleTypeId + ":" + roleName);
                            roleSet.add(roleType + ":*:" + roleName);
                            roleSet.add("*:*:" + roleName);
                        }
                        roleSet.add(roleType + ":" + roleTypeId + ":*");
                    }
                }
                roleSet.add(roleType + ":*:*");
            }
        }
        
        List<String> roleList = new ArrayList<>(roleSet);
        Collections.sort(roleList);
        return roleList;
    }



    /**
     * Helper to log and return an error challenge to the user.
     * (Helper method imported from the user_creation branch)
     */
    private void failAuthentication(AuthenticationFlowContext context, String username, String error) {
        context.getEvent().user(username).error("invalid_grant");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
            context.form().setError(error).createLoginUsernamePassword());
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