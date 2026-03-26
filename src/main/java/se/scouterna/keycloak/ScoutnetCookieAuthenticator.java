package se.scouterna.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationManager;
import se.scouterna.keycloak.client.ScoutnetClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticator that validates the Keycloak SSO cookie and, if valid,
 * uses the stored persistent Scoutnet token to fetch fresh profile data.
 *
 * Fetches are throttled by a configurable interval (default 60 min).
 * Within the interval, cookie-based logins succeed immediately without
 * contacting Scoutnet. After the interval, a fresh fetch is performed
 * and the profile is synced if the hash has changed.
 *
 * If anything fails (no cookie, no token, token revoked, refresh failed),
 * it falls through to the password authenticator via context.attempted().
 */
public class ScoutnetCookieAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(ScoutnetCookieAuthenticator.class);
    private static final String LAST_FETCH_ATTRIBUTE = "scoutnet_last_fetch";

    private final ScoutnetClient scoutnetClient;
    private final ScoutnetProfileSync profileSync;

    public ScoutnetCookieAuthenticator() {
        this.scoutnetClient = new ScoutnetClient();
        this.profileSync = new ScoutnetProfileSync(scoutnetClient, new ScoutnetGroupManager());
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Step 1: Validate SSO cookie
        AuthenticationManager.AuthResult authResult = AuthenticationManager.authenticateIdentityCookie(
            context.getSession(), context.getRealm(), true);

        if (authResult == null) {
            context.attempted();
            return;
        }

        UserModel user = authResult.user();
        if (user == null) {
            context.attempted();
            return;
        }

        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        boolean isRememberMe = authResult.session().isRememberMe();
        log.infof("[%s] SSO cookie valid for user: %s (rememberMe=%s)", correlationId, user.getUsername(), isRememberMe);

        // Step 2: Check if fetch is needed based on throttle interval
        int fetchIntervalMinutes = getFetchIntervalMinutes(context);
        String lastFetchStr = user.getFirstAttribute(LAST_FETCH_ATTRIBUTE);
        if (!isFetchNeeded(lastFetchStr, fetchIntervalMinutes)) {
            long lastFetch = Long.parseLong(lastFetchStr);
            long elapsedSec = (System.currentTimeMillis() - lastFetch) / 1000;
            log.infof("[%s] Scoutnet fetch skipped for user: %s (last fetch %ds ago, interval %dm, rememberMe=%s)",
                correlationId, user.getUsername(), elapsedSec, fetchIntervalMinutes, isRememberMe);
            context.setUser(user);
            context.attachUserSession(authResult.session());
            context.success();
            return;
        }

        if (lastFetchStr != null) {
            long lastFetch = Long.parseLong(lastFetchStr);
            String lastFetchTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(
                Instant.ofEpochMilli(lastFetch).atZone(ZoneId.systemDefault()));
            long elapsedSec = (System.currentTimeMillis() - lastFetch) / 1000;
            log.infof("[%s] Scoutnet fetch needed for user: %s (last fetch at %s, %ds ago, interval %dm, rememberMe=%s)",
                correlationId, user.getUsername(), lastFetchTime, elapsedSec, fetchIntervalMinutes, isRememberMe);
        } else {
            log.infof("[%s] Scoutnet fetch needed for user: %s (no previous fetch, rememberMe=%s)",
                correlationId, user.getUsername(), isRememberMe);
        }

        // Step 3: Retrieve stored persistent token
        String token = ScoutnetTokenCredentialProvider.getToken(user);
        if (token == null) {
            log.debugf("[%s] No stored Scoutnet token for user: %s, falling through to password auth", correlationId, user.getUsername());
            context.attempted();
            return;
        }

        // Step 4: Fetch fresh profile with stored token
        ScoutnetProfileSync.FetchResult fetchResult = profileSync.fetchProfileAndRoles(token, correlationId);

        if (fetchResult == null) {
            log.debugf("[%s] Stored token failed for user: %s, attempting refresh", correlationId, user.getUsername());
            String newToken = scoutnetClient.refreshToken(token, correlationId);

            if (newToken != null) {
                fetchResult = profileSync.fetchProfileAndRoles(newToken, correlationId);
                if (fetchResult != null) {
                    String appId = "scoutid-keycloak-" + context.getRealm().getName();
                    ScoutnetTokenCredentialProvider.storeToken(user, newToken, appId);
                    log.debugf("[%s] Token refreshed and stored for user: %s", correlationId, user.getUsername());
                }
            }

            if (fetchResult == null) {
                log.infof("[%s] Token invalid and refresh failed for user: %s, falling through to password auth", correlationId, user.getUsername());
                context.attempted();
                return;
            }
        }

        // Step 5: Sync profile data (skips if hash unchanged) and update fetch timestamp
        profileSync.syncUserProfile(context.getSession(), context.getRealm(), user, fetchResult, correlationId);
        user.setSingleAttribute(LAST_FETCH_ATTRIBUTE, String.valueOf(System.currentTimeMillis()));

        context.setUser(user);
        context.attachUserSession(authResult.session());
        log.infof("[%s] Cookie-based re-auth successful for user: %s (rememberMe=%s)", correlationId, user.getUsername(), isRememberMe);
        context.success();
    }

    private int getFetchIntervalMinutes(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config != null) {
            Map<String, String> configMap = config.getConfig();
            if (configMap != null) {
                String value = configMap.get(ScoutnetCookieAuthenticatorFactory.CONFIG_FETCH_INTERVAL);
                if (value != null) {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        // fall through to default
                    }
                }
            }
        }
        return ScoutnetCookieAuthenticatorFactory.DEFAULT_FETCH_INTERVAL_MINUTES;
    }

    private boolean isFetchNeeded(String lastFetchStr, int intervalMinutes) {
        if (lastFetchStr == null) return true;

        try {
            long lastFetch = Long.parseLong(lastFetchStr);
            long elapsed = System.currentTimeMillis() - lastFetch;
            return elapsed >= intervalMinutes * 60_000L;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        context.attempted();
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
