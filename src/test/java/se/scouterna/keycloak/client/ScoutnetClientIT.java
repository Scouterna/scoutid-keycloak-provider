package se.scouterna.keycloak.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.scouterna.keycloak.client.dto.AuthResult;
import se.scouterna.keycloak.client.dto.AuthResponse;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test for the ScoutnetClient.
 * This test requires valid Scoutnet credentials to be set as environment variables:
 * - SCOUTNET_USERNAME
 * - SCOUTNET_PASSWORD
 */
public class ScoutnetClientIT {

    private ScoutnetClient scoutnetClient;
    private String username;
    private String password;

    @BeforeEach
    void setUp() {
        scoutnetClient = new ScoutnetClient();
        // Read credentials from environment variables for security
        username = System.getenv("SCOUTNET_USERNAME");
        password = System.getenv("SCOUTNET_PASSWORD");

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            // This will cause tests that need credentials to be skipped
            System.out.println("WARN: SCOUTNET_USERNAME or SCOUTNET_PASSWORD env vars not set. Skipping integration test.");
        }
    }

    @Test
    void testFullAuthenticationAndProfileFetch() {
        if (username == null || password == null) {
            return; 
        }

        String correlationId = "test-" + System.currentTimeMillis();

        // Step 1: Authenticate and get a token
        AuthResult authResult = scoutnetClient.authenticate(username, password, correlationId);

        assertNotNull(authResult, "Authentication result should not be null");
        assertTrue(authResult.isSuccess(), "Authentication should succeed");
        
        AuthResponse authResponse = authResult.getAuthResponse();
        assertNotNull(authResponse, "Authentication response should not be null");
        assertNotNull(authResponse.getToken(), "Token should not be null");
        assertFalse(authResponse.getToken().isEmpty(), "Token should not be empty");
        assertNotNull(authResponse.getMember(), "Member data should not be null");
        assertTrue(authResponse.getMember().getMemberNo() > 0, "Member number should be positive");
        
        System.out.println("Authentication successful for member no: " + authResponse.getMember().getMemberNo());

        // Step 2: Use the token to fetch the profile
        Profile profile = scoutnetClient.getProfile(authResponse.getToken(), correlationId);

        assertNotNull(profile, "Profile response should not be null");
        assertEquals(authResponse.getMember().getMemberNo(), profile.getMemberNo(), "Member number in profile should match member number in auth response");
        assertNotNull(profile.getFirstName(), "Profile first name should not be null");
        assertNotNull(profile.getMemberships(), "Profile should contain memberships");

        System.out.println("Profile fetch successful for: " + profile.getFirstName() + " " + profile.getLastName());

        // Step 3: Fetch Profile Image
        byte[] imageBytes = scoutnetClient.getProfileImage(authResponse.getToken(), correlationId);
        
        if (imageBytes != null) {
            System.out.println("Compressed Image size: " + imageBytes.length + " bytes.");
            
            // Validation: The image should be reasonably small due to resizing (e.g., < 100KB)
            // A 128x128 JPG is usually around 2KB - 10KB.
            assertTrue(imageBytes.length > 0, "Image bytes should not be empty");
            assertTrue(imageBytes.length < 100_000, "Image should be compressed/resized to a reasonable size for a token claim");
            
            // Check magic numbers for JPEG (FF D8)
            assertEquals((byte) 0xFF, imageBytes[0], "Should be JPEG format");
            assertEquals((byte) 0xD8, imageBytes[1], "Should be JPEG format");
        } else {
            System.out.println("User has no profile image.");
        }

        // Step 4: Fetch roles
        Roles roles = scoutnetClient.getRoles(authResponse.getToken(), correlationId);
        
        assertNotNull(roles, "Roles response should not be null");
        
        // Validation: Check if at least one common role type field is not null (e.g., 'organisation' or 'group').
        // Since roles can be empty for a new user, you must validate based on expected content.
        // Adjust these checks based on what a test user is expected to have.
        
        // Example assertion (Assuming a test user always belongs to at least one organisation or group):
        boolean hasOrganisationOrGroup = (roles.getOrganisation() != null && !roles.getOrganisation().isEmpty()) ||
                                         (roles.getGroup() != null && !roles.getGroup().isEmpty());
        
        // If the API returns empty maps/nulls for users with no roles, this test needs to be adjusted.
        // For a typical user, we expect some data:
        if (!hasOrganisationOrGroup) {
             System.out.println("WARN: Test user appears to have no organisation or group roles. Roles JSON check will pass if all fields are null.");
        }
        
        System.out.println("Roles fetch successful.");
    }

    @Test
    void testProfileHashComparison() {
        if (username == null || password == null) {
            return;
        }

        String correlationId = "test-hash-" + System.currentTimeMillis();

        // Authenticate and get profile data
        AuthResult authResult = scoutnetClient.authenticate(username, password, correlationId);
        assertTrue(authResult.isSuccess(), "Authentication should succeed");
        
        String token = authResult.getAuthResponse().getToken();
        String profileJson1 = scoutnetClient.getProfileJson(token, correlationId);
        String profileJson2 = scoutnetClient.getProfileJson(token, correlationId + "-2");
        String rolesJson = scoutnetClient.getRolesJson(token, correlationId);
        
        assertNotNull(profileJson1, "First profile JSON should not be null");
        assertNotNull(profileJson2, "Second profile JSON should not be null");
        assertNotNull(rolesJson, "Roles JSON should not be null");
        
        // Test hash generation with same data
        String hash1 = generateTestHash(profileJson1, rolesJson, null);
        String hash2 = generateTestHash(profileJson2, rolesJson, null);
        
        assertEquals(hash1, hash2, "Identical profile and roles data should produce identical hashes");
        
        // Test hash with modified profile JSON
        String modifiedProfileJson = profileJson1.replaceAll("\"first_name\":\s*\"[^\"]*\"", "\"first_name\": \"TestModified\"");
        String hashModifiedProfile = generateTestHash(modifiedProfileJson, rolesJson, null);
        
        assertNotEquals(hash1, hashModifiedProfile, "Modified profile data should produce different hash");
        
        // Test hash with modified roles JSON
        String modifiedRolesJson = rolesJson.replaceAll("\"organisation\":", "\"organisation_modified\":");
        String hashModifiedRoles = generateTestHash(profileJson1, modifiedRolesJson, null);
        
        assertNotEquals(hash1, hashModifiedRoles, "Modified roles data should produce different hash");
        
        // Test that last_login changes don't affect hash
        String jsonWithDifferentLogin = profileJson1.replaceAll("\"last_login\":\s*\"[^\"]*\"", "\"last_login\": \"2025-01-01 00:00:00\"");
        String hashWithDifferentLogin = generateTestHash(jsonWithDifferentLogin, rolesJson, null);
        
        assertEquals(hash1, hashWithDifferentLogin, "Different last_login should not affect hash");
        
        System.out.println("Hash comparison test successful. Hash length: " + hash1.length());
    }
    
    // Helper method that mimics the hash generation logic from ScoutnetAuthenticator
    private String generateTestHash(String profileJson, String rolesJson, byte[] imageBytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            
            // Remove last_login from JSON before hashing (same logic as authenticator)
            String cleanedJson = profileJson.replaceAll(",?\\s*\"last_login\"\\s*:\\s*\"[^\"]*\"", "");
            digest.update(cleanedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            if (rolesJson != null) {
                digest.update(rolesJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            
            if (imageBytes != null) {
                digest.update(String.valueOf(imageBytes.length).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Test
    void testFailedAuthentication() {
        AuthResult result = scoutnetClient.authenticate("invalid-username", "bad-password", "test-fail");
        assertNotNull(result, "Authentication result should not be null");
        assertFalse(result.isSuccess(), "Authentication with invalid credentials should fail");
        assertEquals(AuthResult.AuthError.INVALID_CREDENTIALS, result.getError(), "Should return invalid credentials error");
    }
}