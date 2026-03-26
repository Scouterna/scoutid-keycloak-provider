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
import se.scouterna.keycloak.client.dto.AuthResult;
import se.scouterna.keycloak.client.dto.AuthResponse;
import se.scouterna.keycloak.client.dto.Profile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Keycloak Authenticator that validates credentials against the Scoutnet API.
 * Requests a persistent token (via app_id) and stores it securely using Keycloak's credential store.
 */
public class ScoutnetAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(ScoutnetAuthenticator.class);
    private static final String APP_NAME = "ScoutID";

    private final ScoutnetClient scoutnetClient;
    private final ScoutnetProfileSync profileSync;

    public ScoutnetAuthenticator() {
        this.scoutnetClient = new ScoutnetClient();
        this.profileSync = new ScoutnetProfileSync(scoutnetClient, new ScoutnetGroupManager());
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        log.info("Displaying login form for Scoutnet authentication.");
        context.challenge(context.form().createLoginUsernamePassword());
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.infof("[%s] Processing submitted login form for Scoutnet authentication.", correlationId);
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            failAuthentication(context, username, "Please provide both a username and a password.", correlationId);
            return;
        }

        boolean isPersonnummer = !username.contains("@") && !(username.matches("\\d{7}") && !username.matches("\\d{10,12}"));
        if (isPersonnummer) {
            username = normalizePersonnummer(username);
        }

        // Step 1: Authenticate — request persistent token only if "remember me" is checked and enabled
        boolean rememberMe = context.getRealm().isRememberMe() && "on".equals(formData.getFirst("rememberMe"));
        String logUsername = safeLogUsername(username, isPersonnummer);
        String appId = null;
        String deviceName = null;
        if (rememberMe) {
            appId = "scoutid-keycloak-" + context.getRealm().getName();
            deviceName = context.getUriInfo().getBaseUri().toString();
        }
        AuthResult authResult = scoutnetClient.authenticate(username, password, logUsername, appId, APP_NAME, deviceName, correlationId);
        if (!authResult.isSuccess()) {
            String messageKey = authResult.getError() == AuthResult.AuthError.INVALID_CREDENTIALS
                ? "invalidUserMessage"
                : "loginTimeout";
            log.warnf("[%s] Authentication failed for user: %s", correlationId, logUsername);
            failAuthentication(context, username, messageKey, correlationId);
            return;
        }

        AuthResponse authResponse = authResult.getAuthResponse();
        if (authResponse.getToken() == null || authResponse.getToken().isEmpty()) {
            failAuthentication(context, username, "loginTimeout", correlationId);
            return;
        }

        // Step 2: Fetch profile and roles
        ScoutnetProfileSync.FetchResult fetchResult = profileSync.fetchProfileAndRoles(authResponse.getToken(), correlationId);
        if (fetchResult == null) {
            log.errorf("[%s] Could not retrieve user profile from Scoutnet for user: %s", correlationId, logUsername);
            failAuthentication(context, username, "loginTimeout", correlationId);
            return;
        }

        Profile profile = fetchResult.getProfile();

        // Step 3: Find or create the Keycloak user
        String keycloakUsername = "scoutnet|" + profile.getMemberNo();
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), keycloakUsername);

        if (user == null) {
            log.infof("[%s] First time login for Scoutnet user: %d. Creating new Keycloak user: %s.", correlationId, profile.getMemberNo(), keycloakUsername);
            user = context.getSession().users().addUser(context.getRealm(), keycloakUsername);
            user.setEnabled(true);
        } else {
            log.debugf("[%s] Found existing Keycloak user: %s, checking for profile updates.", correlationId, keycloakUsername);
        }

        // Step 4: Sync profile data
        profileSync.syncUserProfile(context.getSession(), context.getRealm(), user, fetchResult, correlationId);

        // Step 5: Store persistent token securely (only if remember-me was checked)
        if (rememberMe) {
            ScoutnetTokenCredentialProvider.storeToken(user, authResponse.getToken(), appId);
            context.getAuthenticationSession().setAuthNote("remember_me", "true");
            log.debugf("[%s] Stored persistent Scoutnet token for user: %s", correlationId, keycloakUsername);
        }

        context.setUser(user);
        context.getAuthenticationSession().removeAuthNote("username");
        log.infof("[%s] Authentication successful for user: %s (rememberMe=%s)", correlationId, keycloakUsername, rememberMe);
        context.success();
    }

    private void failAuthentication(AuthenticationFlowContext context, String username, String messageKey, String correlationId) {
        log.errorf("[%s] Authentication failed: %s", correlationId, messageKey);
        context.getEvent().user(username).error("invalid_grant");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
            context.form().setError(messageKey).createLoginUsernamePassword());
    }

    private String safeLogUsername(String username, boolean isPersonnummer) {
        if (isPersonnummer && username.length() >= 8) {
            return username.substring(0, 8) + "****";
        }
        return username;
    }

    private String normalizePersonnummer(String input) {
        String digits = input.replaceAll("-", "");

        if (digits.matches("\\d{10}")) {
            int year = Integer.parseInt(digits.substring(0, 2));
            int currentYear = LocalDate.now().getYear();
            int currentCentury = currentYear / 100;
            int currentYearInCentury = currentYear % 100;

            String century = (year > currentYearInCentury || (currentYear - (currentCentury * 100 + year)) >= 100)
                ? String.valueOf(currentCentury - 1)
                : String.valueOf(currentCentury);

            return century + digits;
        } else if (digits.matches("\\d{12}")) {
            return digits;
        }

        return input;
    }

    @Override
    public boolean requiresUser() { return false; }
    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}
    @Override
    public void close() {}
}
