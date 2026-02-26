package se.scouterna.keycloak.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import se.scouterna.keycloak.client.dto.AuthResult;
import se.scouterna.keycloak.client.dto.AuthResponse;
import se.scouterna.keycloak.client.dto.ErrorResponse;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ScoutnetClient {

    private static final Logger log = Logger.getLogger(ScoutnetClient.class);
    private static final String SCOUTNET_BASE_URL = System.getenv().getOrDefault("SCOUTNET_BASE_URL", "https://scoutnet.se");
    private static final String AUTH_URL = SCOUTNET_BASE_URL + "/api/authenticate";
    private static final String PROFILE_URL = SCOUTNET_BASE_URL + "/api/get/profile";
    private static final String ROLES_URL = SCOUTNET_BASE_URL + "/api/get/user_roles";
    
    // Shared HttpClient with optimized connection pool for high-load scenarios
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)) // Reduced for faster failure detection
        .executor(java.util.concurrent.ForkJoinPool.commonPool())
        .version(HttpClient.Version.HTTP_2) // Use HTTP/2 for better performance
        .build();

    // Connection pool is managed by the underlying implementation
    // Default pool size is typically 2 per destination, but HTTP/2 multiplexes requests
    // For high load, consider using a custom executor with more threads

    // Shared ObjectMapper for thread safety and performance
    private static final ObjectMapper SHARED_OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

    public ScoutnetClient() {
        // No instance variables needed - everything is static
    }

    private String getErrorType(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Rate Limited";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "HTTP " + statusCode;
        };
    }

    private String tryParseErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "No error details";
        }
        
        try {
            ErrorResponse errorResponse = SHARED_OBJECT_MAPPER.readValue(responseBody, ErrorResponse.class);
            return errorResponse.getSafeErrorMessage();
        } catch (Exception e) {
            // If we can't parse as ErrorResponse, return a safe truncated version
            return responseBody.length() > 50 ? responseBody.substring(0, 50) + "..." : responseBody;
        }
    }

    public AuthResult authenticate(String username, String password, String correlationId) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("password", password);

            String jsonPayload = SHARED_OBJECT_MAPPER.writeValueAsString(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10)) // Reduced from 30s for faster failure detection
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                AuthResponse authResponse = SHARED_OBJECT_MAPPER.readValue(response.body(), AuthResponse.class);
                return AuthResult.success(authResponse);
            } else {
                String errorType = getErrorType(response.statusCode());
                String errorDetail = tryParseErrorResponse(response.body());
                log.debugf("[%s] Scoutnet authentication failed for user %s. Status: %d, Error: %s, Detail: %s", 
                    correlationId, username, response.statusCode(), errorType, errorDetail);
                
                AuthResult.AuthError authError = switch (response.statusCode()) {
                    case 401, 403 -> AuthResult.AuthError.INVALID_CREDENTIALS;
                    default -> AuthResult.AuthError.SERVICE_UNAVAILABLE;
                };
                return AuthResult.failure(authError);
            }
        } catch (java.net.http.HttpTimeoutException e) {
            log.errorf("[%s] Scoutnet API timeout during authentication for user %s: %s", correlationId, username, e.getMessage());
            return AuthResult.failure(AuthResult.AuthError.SERVICE_UNAVAILABLE);
        } catch (java.net.ConnectException e) {
            log.errorf("[%s] Cannot connect to Scoutnet API for user %s: %s", correlationId, username, e.getMessage());
            return AuthResult.failure(AuthResult.AuthError.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.errorf("[%s] Unexpected error during Scoutnet authentication for user %s: %s", correlationId, username, e.getClass().getSimpleName());
            return AuthResult.failure(AuthResult.AuthError.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Fetches the detailed user profile using the authentication token.
     *
     * @param token The bearer token from a successful authentication.
     * @return A Profile object, or null if the request fails.
     */
    public Profile getProfile(String token, String correlationId) {
        String profileJson = getProfileJson(token, correlationId);
        if (profileJson == null) return null;
        
        try {
            return SHARED_OBJECT_MAPPER.readValue(profileJson, Profile.class);
        } catch (Exception e) {
            log.errorf("[%s] Failed to parse profile JSON: %s", correlationId, e.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * Fetches the raw profile JSON for hashing purposes.
     */
    public String getProfileJson(String token, String correlationId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_URL))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                String errorType = getErrorType(response.statusCode());
                String errorDetail = tryParseErrorResponse(response.body());
                log.warnf("[%s] Scoutnet profile fetch failed. Status: %d, Error: %s, Detail: %s", 
                    correlationId, response.statusCode(), errorType, errorDetail);
                return null;
            }
            
            return response.body();
        } catch (java.net.http.HttpTimeoutException e) {
            log.errorf("[%s] Scoutnet API timeout during profile fetch: %s", correlationId, e.getMessage());
            return null;
        } catch (java.net.ConnectException e) {
            log.errorf("[%s] Cannot connect to Scoutnet API for profile fetch: %s", correlationId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.errorf("[%s] Unexpected error during Scoutnet profile fetch: %s", correlationId, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Fetches the raw roles JSON structure from the API.
     * This structure will be parsed into a flattened list of roles later.
     */
    public Roles getRoles(String token, String correlationId) {
        String rolesJson = getRolesJson(token, correlationId);
        if (rolesJson == null) return null;
        
        try {
            return SHARED_OBJECT_MAPPER.readValue(rolesJson, Roles.class);
        } catch (Exception e) {
            log.errorf("[%s] Failed to parse roles JSON: %s", correlationId, e.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * Fetches the raw roles JSON for hashing purposes.
     */
    public String getRolesJson(String token, String correlationId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ROLES_URL))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                String errorType = getErrorType(response.statusCode());
                String errorDetail = tryParseErrorResponse(response.body());
                log.warnf("[%s] Scoutnet roles fetch failed. Status: %d, Error: %s, Detail: %s", 
                    correlationId, response.statusCode(), errorType, errorDetail);
                return null;
            }
            
            return response.body();
        } catch (java.net.http.HttpTimeoutException e) {
            log.errorf("[%s] Scoutnet API timeout during roles fetch: %s", correlationId, e.getMessage());
            return null;
        } catch (java.net.ConnectException e) {
            log.errorf("[%s] Cannot connect to Scoutnet API for roles fetch: %s", correlationId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.errorf("[%s] Unexpected error during Scoutnet roles fetch: %s", correlationId, e.getClass().getSimpleName());
            return null;
        }
    }
}