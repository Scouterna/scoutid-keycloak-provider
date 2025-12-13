package se.scouterna.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;
import se.scouterna.keycloak.client.ScoutnetClient;
import se.scouterna.keycloak.client.dto.ContactInfo;
import se.scouterna.keycloak.client.dto.Profile;

/**
 * Simple utility to fetch fresh contact data from ScoutNet (no storage)
 */
public class ScoutnetUserAttributeProvider implements Provider {
    
    private static final Logger log = Logger.getLogger(ScoutnetUserAttributeProvider.class);
    private final ScoutnetClient scoutnetClient;
    
    public ScoutnetUserAttributeProvider() {
        this.scoutnetClient = new ScoutnetClient();
    }
    
    public ContactInfo getFreshContactInfo(UserModel user) {
        String scoutnetToken = user.getFirstAttribute("scoutnet_token");
        if (scoutnetToken == null) return null;
        
        try {
            Profile profile = scoutnetClient.getProfile(scoutnetToken);
            if (profile != null) {
                ContactInfo contactInfo = new ContactInfo();
                contactInfo.setAddresses(profile.getAddresses());
                return contactInfo;
            }
        } catch (Exception e) {
            log.warnf("Failed to fetch contact info, attempting token refresh: %s", e.getMessage());
            
            // Try token refresh
            String newToken = scoutnetClient.refreshToken(scoutnetToken);
            if (newToken != null) {
                user.setSingleAttribute("scoutnet_token", newToken);
                try {
                    Profile profile = scoutnetClient.getProfile(newToken);
                    if (profile != null) {
                        ContactInfo contactInfo = new ContactInfo();
                        contactInfo.setAddresses(profile.getAddresses());
                        return contactInfo;
                    }
                } catch (Exception retryException) {
                    log.errorf("Failed to fetch contact info even after token refresh: %s", retryException.getMessage());
                }
            }
        }
        return null;
    }
    
    @Override
    public void close() {
        // Nothing to close
    }
}