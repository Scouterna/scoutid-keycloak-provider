package org.scouterna.keycloak.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.scouterna.keycloak.client.dto.AuthResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ScoutnetClient {

    private static final Logger log = Logger.getLogger(ScoutnetClient.class);
    private static final String AUTH_URL = "https://scoutnet.se/api/authenticate";
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ScoutnetClient() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    public AuthResponse authenticate(String username, String password) {
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
            log.error("Failed to communicate with Scoutnet API", e);
            return null;
        }
    }
}