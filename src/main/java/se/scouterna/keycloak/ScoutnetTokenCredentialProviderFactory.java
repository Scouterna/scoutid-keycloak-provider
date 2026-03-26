package se.scouterna.keycloak;

import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.models.KeycloakSession;

public class ScoutnetTokenCredentialProviderFactory implements CredentialProviderFactory<ScoutnetTokenCredentialProvider> {

    public static final String PROVIDER_ID = "scoutnet-token";

    @Override
    public CredentialProvider<? extends org.keycloak.credential.CredentialModel> create(KeycloakSession session) {
        return new ScoutnetTokenCredentialProvider(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
