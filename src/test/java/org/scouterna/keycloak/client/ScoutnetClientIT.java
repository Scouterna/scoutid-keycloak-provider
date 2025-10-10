package org.scouterna.keycloak.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scouterna.keycloak.client.dto.AuthResponse;
import org.scouterna.keycloak.client.dto.Profile;

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
            // Re-check here to formally skip the test in the runner
            // Using JUnit 5's Assumptions would be another way to do this.
            return; 
        }

        // Step 1: Authenticate and get a token
        AuthResponse authResponse = scoutnetClient.authenticate(username, password);

        assertNotNull(authResponse, "Authentication response should not be null");
        assertNotNull(authResponse.getToken(), "Token should not be null");
        assertFalse(authResponse.getToken().isEmpty(), "Token should not be empty");
        assertNotNull(authResponse.getMember(), "Member data should not be null");
        assertTrue(authResponse.getMember().getMemberNo() > 0, "Member number should be positive");
        
        System.out.println("Authentication successful for member no: " + authResponse.getMember().getMemberNo());

        // Step 2: Use the token to fetch the profile
        Profile profile = scoutnetClient.getProfile(authResponse.getToken());

        assertNotNull(profile, "Profile response should not be null");
        assertEquals(authResponse.getMember().getMemberNo(), profile.getMemberNo(), "Member number in profile should match member number in auth response");
        assertNotNull(profile.getFirstName(), "Profile first name should not be null");
        assertNotNull(profile.getMemberships(), "Profile should contain memberships");

        System.out.println("Profile fetch successful for: " + profile.getFirstName() + " " + profile.getLastName());
    }

    @Test
    void testFailedAuthentication() {
        AuthResponse response = scoutnetClient.authenticate("invalid-username-12345", "this-is-a-bad-password");
        assertNull(response, "Authentication with invalid credentials should return null");
    }
}