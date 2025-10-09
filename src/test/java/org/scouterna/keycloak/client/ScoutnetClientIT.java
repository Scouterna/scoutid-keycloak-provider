package org.scouterna.keycloak.client;

import org.scouterna.keycloak.client.dto.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test for the ScoutnetClient.
 *
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
        username = "username";
        password = "password";

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            fail("Environment variables SCOUTNET_USERNAME and SCOUTNET_PASSWORD must be set to run this test.");
        }
    }

    @Test
    void testSuccessfulAuthentication() {
        AuthResponse response = scoutnetClient.authenticate(username, password);

        assertNotNull(response, "Authentication response should not be null");
        assertNotNull(response.getToken(), "Token should not be null");
        assertFalse(response.getToken().isEmpty(), "Token should not be empty");
        assertNotNull(response.getMember(), "Member data should not be null");
        assertTrue(response.getMember().getMemberNo() > 0, "Member number should be positive");
        assertNotNull(response.getMember().getEmail(), "Member email should not be null");
    }

    @Test
    void testFailedAuthentication() {
        AuthResponse response = scoutnetClient.authenticate("invaliduser", "invalidpassword");
        assertNull(response, "Authentication with invalid credentials should return null");
    }
}