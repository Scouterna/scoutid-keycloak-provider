package se.scouterna.keycloak.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;
import se.scouterna.keycloak.client.dto.AuthResponse;
import se.scouterna.keycloak.client.dto.Profile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ScoutnetClient {

    private static final Logger log = Logger.getLogger(ScoutnetClient.class);
    private static final String AUTH_URL = "https://scoutnet.se/api/authenticate";
    private static final String PROFILE_URL = "https://scoutnet.se/api/get/profile";
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ScoutnetClient() {
        this.httpClient = HttpClients.createDefault();
        // Prevents errors if the API adds new fields we don't have in our DTOs
        this.objectMapper = new ObjectMapper();
    }

    public AuthResponse authenticate(String username, String password) {
        // This method remains the same as you provided
        try {
            HttpPost request = new HttpPost(AUTH_URL);
            Map<String, String> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("password", password);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            request.setEntity(new StringEntity(jsonPayload));
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    log.warnf("Scoutnet authentication failed for user %s. Status: %d, Body: %s", username, statusCode, responseBody);
                    return null;
                }
                return objectMapper.readValue(responseBody, AuthResponse.class);
            }
        } catch (IOException e) {
            log.error("Failed to communicate with Scoutnet API for authentication", e);
            return null;
        }
    }

    /**
     * Fetches the detailed user profile using the authentication token.
     *
     * @param token The bearer token from a successful authentication.
     * @return A Profile object, or null if the request fails.
     */
    public Profile getProfile(String token) {
        try {
            HttpGet request = new HttpGet(PROFILE_URL);
            request.setHeader("Authorization", "Bearer " + token);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    log.warnf("Scoutnet profile fetch failed. Status: %d, Body: %s", statusCode, responseBody);
                    return null;
                }
                return objectMapper.readValue(responseBody, Profile.class);
            }
        } catch (IOException e) {
            log.error("Failed to communicate with Scoutnet API for profile fetch", e);
            return null;
        }
    }
}