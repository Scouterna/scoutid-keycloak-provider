package se.scouterna.keycloak;

import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class ScoutnetCookieAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "scoutnet-cookie-authenticator";
    public static final String CONFIG_FETCH_INTERVAL = "scoutnet.fetch.interval.minutes";
    public static final int DEFAULT_FETCH_INTERVAL_MINUTES = 60;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Scoutnet Cookie Re-authenticator";
    }

    @Override
    public String getHelpText() {
        return "Validates the Keycloak SSO cookie and uses a stored persistent Scoutnet token to re-fetch profile data. "
             + "Fetches are throttled to avoid unnecessary API calls.";
    }

    @Override
    public String getReferenceCategory() {
        return "cookie";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty fetchInterval = new ProviderConfigProperty();
        fetchInterval.setName(CONFIG_FETCH_INTERVAL);
        fetchInterval.setLabel("Fetch interval (minutes)");
        fetchInterval.setHelpText("Minimum time between Scoutnet profile fetches for the same user. "
            + "During this interval, cookie-based logins will succeed without contacting Scoutnet. "
            + "Set to 0 to always fetch fresh data (no caching). "
            + "Default: " + DEFAULT_FETCH_INTERVAL_MINUTES + " minutes.");
        fetchInterval.setType(ProviderConfigProperty.STRING_TYPE);
        fetchInterval.setDefaultValue(String.valueOf(DEFAULT_FETCH_INTERVAL_MINUTES));
        return List.of(fetchInterval);
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new ScoutnetCookieAuthenticator();
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
