package se.scouterna.keycloak.client.dto;

public class AuthResult {
    private final AuthResponse authResponse;
    private final AuthError error;
    
    public enum AuthError {
        INVALID_CREDENTIALS("scoutnet.auth.invalid.credentials"),
        SERVICE_UNAVAILABLE("scoutnet.auth.service.unavailable");
        
        private final String messageKey;
        
        AuthError(String messageKey) {
            this.messageKey = messageKey;
        }
        
        public String getMessageKey() {
            return messageKey;
        }
    }
    
    private AuthResult(AuthResponse authResponse, AuthError error) {
        this.authResponse = authResponse;
        this.error = error;
    }
    
    public static AuthResult success(AuthResponse authResponse) {
        return new AuthResult(authResponse, null);
    }
    
    public static AuthResult failure(AuthError error) {
        return new AuthResult(null, error);
    }
    
    public boolean isSuccess() {
        return authResponse != null && error == null;
    }
    
    public AuthResponse getAuthResponse() {
        return authResponse;
    }
    
    public AuthError getError() {
        return error;
    }
    
    public String getMessageKey() {
        return error != null ? error.getMessageKey() : null;
    }
}