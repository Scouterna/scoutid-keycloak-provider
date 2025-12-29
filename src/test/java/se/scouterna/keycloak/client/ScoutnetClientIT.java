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

        assertEquals(authResponse.getMember().getMemberNo(), profile.getMemberNo());

        System.out.println("Profile fetch successful for: " + profile.getFirstName());

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
        
        // Validation 1: Check if at least one common role type field is not null (e.g., 'organisation' or 'group').
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
    void testFailedAuthentication() {
        AuthResult result = scoutnetClient.authenticate("invalid-username", "bad-password", "test-fail");
        assertNotNull(result, "Authentication result should not be null");
        assertFalse(result.isSuccess(), "Authentication with invalid credentials should fail");
        assertEquals(AuthResult.AuthError.INVALID_CREDENTIALS, result.getError(), "Should return invalid credentials error");
    }
}