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
import org.scouterna.keycloak.client.dto.AuthResponse;
import org.scouterna.keycloak.client.dto.Member;

public class ScoutnetAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(ScoutnetAuthenticator.class);
    /**
     * This method is called when the authenticator is first executed in the flow.
     * Its ONLY job is to display the username and password form to the user.
     */
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        log.info("Displaying login form for Scoutnet authentication.");
        // This will render the standard login.ftl template with both username and password fields.
        context.challenge(context.form().createLoginUsernamePassword()); 
    }

    /**
     * This method is called ONLY after the user has submitted the form
     * that was displayed by the authenticate() method.
     */
    @Override
    public void action(AuthenticationFlowContext context) {
        log.info("Processing submitted login form for Scoutnet authentication.");
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        // Basic validation of the submitted form data.
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            log.warn("Form submitted with missing username or password.");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setError("Please provide both a username and a password.").createLoginPassword());
            return;
        }

        // --- All of your original logic now lives here ---
        ScoutnetClient scoutnetClient = new ScoutnetClient();
        AuthResponse authResponse = scoutnetClient.authenticate(username, password);

        if (authResponse == null || authResponse.getMember() == null) {
            context.getEvent().user(username).error("invalid_grant");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setError("Invalid username or password.").createLoginPassword());
            return;
        }

        Member member = authResponse.getMember();
        String scoutnetUsername = "scoutnet-" + member.getMemberNo();

        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), scoutnetUsername);

        if (user == null) {
            log.infof("First time login for Scoutnet user: %s. Creating new user.", scoutnetUsername);
            user = context.getSession().users().addUser(context.getRealm(), scoutnetUsername);
            user.setEnabled(true);
        }

        // Update user profile from Scoutnet data
        user.setFirstName(member.getFirstName());
        user.setLastName(member.getLastName());
        user.setEmail(member.getEmail());
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(member.getMemberNo()));

        context.setUser(user);
        context.success();
    }


    // --- The rest of the methods are boilerplate and remain unchanged ---

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // No-op
    }
}