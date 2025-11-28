package se.scouterna.keycloak;

import org.keycloak.models.KeycloakSession;
import org.keycloak.theme.ThemeResourceProvider;
import org.keycloak.theme.ThemeResourceProviderFactory;
import org.keycloak.theme.resources.ClassPathThemeProvider; // This import will now resolve

public class ScoutnetThemeResourceProviderFactory implements ThemeResourceProviderFactory {

    public static final String PROVIDER_ID = "scoutnet-theme-loader";

    @Override
    public ThemeResourceProvider create(KeycloakSession session) {
        // FIX: Using the literal string ID for the default ThemeResourceProvider
        // The default provider ID is "themeResources" in Keycloak 26.
        return session.getProvider(ThemeResourceProvider.class, "themeResources"); 
    }

    // --- (Rest of the boilerplate methods remain the same) ---
    @Override
    public void init(org.keycloak.Config.Scope config) {}

    @Override
    public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}