package se.scouterna.keycloak.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import se.scouterna.keycloak.client.dto.AuthResponse;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ScoutnetClient {

    private static final Logger log = Logger.getLogger(ScoutnetClient.class);
    private static final String AUTH_URL = "https://scoutnet.se/api/authenticate";
    private static final String PROFILE_URL = "https://scoutnet.se/api/get/profile";
    private static final String PROFILE_IMAGE_URL = "https://scoutnet.se/api/get/profile_image";
    private static final String ROLES_URL = "https://scoutnet.se/api/get/user_roles";
    
    // Max width/height for the avatar to keep the OIDC token size manageable
    private static final int TARGET_IMAGE_SIZE = 128;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ScoutnetClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
    }

    public AuthResponse authenticate(String username, String password) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("password", password);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                log.warnf("Scoutnet authentication failed for user %s. Status: %d, Body: %s", 
                    username, response.statusCode(), response.body());
                return null;
            }
            
            return objectMapper.readValue(response.body(), AuthResponse.class);
        } catch (Exception e) {
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
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_URL))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                log.warnf("Scoutnet profile fetch failed. Status: %d, Body: %s", 
                    response.statusCode(), response.body());
                return null;
            }
            
            return objectMapper.readValue(response.body(), Profile.class);
        } catch (Exception e) {
            log.error("Failed to communicate with Scoutnet API for profile fetch", e);
            return null;
        }
    }

    /**
     * Fetches the raw roles JSON structure from the API.
     * This structure will be parsed into a flattened list of roles later.
     */
    public Roles getRoles(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ROLES_URL))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                log.warnf("Scoutnet roles fetch failed. Status: %d, Body: %s", 
                    response.statusCode(), response.body());
                return null;
            }
            
            return objectMapper.readValue(response.body(), Roles.class);
        } catch (Exception e) {
            log.error("Failed to communicate with Scoutnet API for roles fetch", e);
            return null;
        }
    }

    /**
     * Fetches, resizes, and compresses the user profile image.
     *
     * @param token The bearer token.
     * @return byte[] containing the compressed JPEG image data, or null if not found.
     */
    public byte[] getProfileImage(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_IMAGE_URL))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                return processImage(response.body());
            } else if (response.statusCode() == 404) {
                log.debug("No profile image found for user.");
                return null;
            } else {
                log.warnf("Scoutnet profile image fetch failed. Status: %d", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to communicate with Scoutnet API for profile image fetch", e);
            return null;
        }
    }

    /**
     * Helper to resize and compress image to JPG to save space in the DB/Token.
     */
    private byte[] processImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return null;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            BufferedImage originalImage = ImageIO.read(bais);
            if (originalImage == null) return null; // Not a valid image

            // Calculate new dimensions preserving aspect ratio
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            int newWidth = TARGET_IMAGE_SIZE;
            int newHeight = TARGET_IMAGE_SIZE;

            if (originalWidth > originalHeight) {
                newHeight = (int) ((double) originalHeight / originalWidth * TARGET_IMAGE_SIZE);
            } else {
                newWidth = (int) ((double) originalWidth / originalHeight * TARGET_IMAGE_SIZE);
            }

            // Resize logic
            Image resultingImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            BufferedImage outputImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();

            // Write as JPEG for better compression than PNG
            ImageIO.write(outputImage, "jpg", baos);
            
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error processing profile image", e);
            return null; // Fail gracefully
        }
    }
}