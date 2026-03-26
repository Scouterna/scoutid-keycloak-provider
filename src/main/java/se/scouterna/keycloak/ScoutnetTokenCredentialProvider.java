package se.scouterna.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialTypeMetadata;
import org.keycloak.credential.CredentialTypeMetadataContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Credential provider for storing persistent Scoutnet API tokens.
 * Tokens are stored encrypted at rest via Keycloak's credential store,
 * not as plain user attributes.
 */
public class ScoutnetTokenCredentialProvider implements CredentialProvider<CredentialModel> {

    public static final String CREDENTIAL_TYPE = "scoutnet-token";
    private static final Logger log = Logger.getLogger(ScoutnetTokenCredentialProvider.class);

    private final KeycloakSession session;

    public ScoutnetTokenCredentialProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public String getType() {
        return CREDENTIAL_TYPE;
    }

    @Override
    public CredentialModel createCredential(RealmModel realm, UserModel user, CredentialModel credentialModel) {
        credentialModel.setType(CREDENTIAL_TYPE);
        return user.credentialManager().createStoredCredential(credentialModel);
    }

    @Override
    public boolean deleteCredential(RealmModel realm, UserModel user, String credentialId) {
        return user.credentialManager().removeStoredCredentialById(credentialId);
    }

    @Override
    public CredentialModel getCredentialFromModel(CredentialModel model) {
        return model;
    }

    @Override
    public CredentialTypeMetadata getCredentialTypeMetadata(CredentialTypeMetadataContext metadataContext) {
        return CredentialTypeMetadata.builder()
            .type(CREDENTIAL_TYPE)
            .category(CredentialTypeMetadata.Category.BASIC_AUTHENTICATION)
            .displayName("Scoutnet Token")
            .removeable(true)
            .build(session);
    }

    /**
     * Retrieves the stored Scoutnet token for a user.
     *
     * @return The token string, or null if no token is stored.
     */
    public static String getToken(UserModel user) {
        return user.credentialManager().getStoredCredentialsByTypeStream(CREDENTIAL_TYPE)
            .findFirst()
            .map(CredentialModel::getSecretData)
            .orElse(null);
    }

    /**
     * Stores or updates the persistent Scoutnet token for a user.
     */
    public static void storeToken(UserModel user, String token, String appId) {
        user.credentialManager().getStoredCredentialsByTypeStream(CREDENTIAL_TYPE)
            .forEach(existing -> user.credentialManager().removeStoredCredentialById(existing.getId()));

        String credentialData;
        try {
            credentialData = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(java.util.Map.of("app_id", appId != null ? appId : ""));
        } catch (Exception e) {
            credentialData = "{}";
        }

        CredentialModel credential = new CredentialModel();
        credential.setType(CREDENTIAL_TYPE);
        credential.setSecretData(token);
        credential.setCredentialData(credentialData);
        credential.setCreatedDate(System.currentTimeMillis());
        credential.setUserLabel("Scoutnet persistent token");
        user.credentialManager().createStoredCredential(credential);
    }
}
