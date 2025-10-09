package org.scouterna.keycloak;

import org.scouterna.keycloak.client.ScoutnetClient;
import org.scouterna.keycloak.client.dto.AuthResponse;
import org.scouterna.keycloak.client.dto.Member;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import jakarta.ws.rs.core.MultivaluedMap;

public class ScoutnetAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(ScoutnetAuthenticator.class);
    private final ScoutnetClient scoutnetClient;

    public ScoutnetAuthenticator() {
        this.scoutnetClient = new ScoutnetClient();
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        if (username == null || password == null) {
            log.warn("Username or password not submitted");
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        AuthResponse authResponse = scoutnetClient.authenticate(username, password);

        if (authResponse == null || authResponse.getMember() == null) {
            context.getEvent().user(username).error("invalid_grant");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setError("Invalid credentials.").createLoginPassword());
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
        // Optional: Store the member number as an attribute
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(member.getMemberNo()));

        context.setUser(user);
        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // This method is not used in this flow
    }

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