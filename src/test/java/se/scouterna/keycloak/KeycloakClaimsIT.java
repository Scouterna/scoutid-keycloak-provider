package se.scouterna.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that authenticates against a running local Keycloak and verifies
 * that the issued ID token contains all expected claims.
 *
 * Requires:
 *   - Local Keycloak running (docker-compose up)
 *   - SCOUTNET_USERNAME and SCOUTNET_PASSWORD environment variables set
 */
public class KeycloakClaimsIT {

    private static final String KEYCLOAK_BASE_URL =
            System.getenv().getOrDefault("KEYCLOAK_BASE_URL", "http://localhost:8080");
    private static final String REALM = "master";
    private static final String CLIENT_ID = "scout-it-client";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String username;
    private String password;

    @BeforeEach
    void setUp() {
        username = System.getenv("SCOUTNET_USERNAME");
        password = System.getenv("SCOUTNET_PASSWORD");

        if (username == null || password == null) {
            System.out.println("WARN: SCOUTNET_USERNAME or SCOUTNET_PASSWORD not set — skipping Keycloak claims IT.");
        }
    }

    @Test
    void idTokenContainsAllExpectedClaims() throws Exception {
        if (username == null || password == null) return;

        JsonNode idToken = fetchIdToken();

        // profile scope — standard
        assertClaim(idToken, "preferred_username");
        assertClaim(idToken, "given_name");
        assertClaim(idToken, "family_name");
        assertClaim(idToken, "name");
        assertClaim(idToken, "picture");
        assertClaim(idToken, "birthdate");
        assertClaim(idToken, "locale");

        // profile scope — custom
        assertClaim(idToken, "scoutnet_member_no");

        // email scope — standard
        assertClaim(idToken, "email");
        assertTrue(idToken.has("email_verified"), "expected claim: email_verified");

        // email scope — custom
        assertClaim(idToken, "scouterna_email");
        // alt_email is only present when the user has an alternative email in Scoutnet — no assertion needed

        // phone scope
        assertClaim(idToken, "phone_number");

        // scoutnet-memberships scope
        assertClaim(idToken, "primary_group_name");
        assertClaim(idToken, "primary_group_no");
        assertClaim(idToken, "memberships");
        // group_emails_json is only present when the user has a kår email address configured
        if (idToken.has("group_emails_json")) {
            assertFalse(idToken.get("group_emails_json").isNull(), "group_emails_json should not be null");
        } else {
            System.out.println("INFO: group_emails_json not present for this test user (no kår email configured)");
        }

        // memberships is a structured object with the expected top-level keys
        JsonNode memberships = idToken.get("memberships");
        assertTrue(memberships.isObject(), "memberships should be a JSON object");
        assertTrue(memberships.has("groups"), "memberships should contain 'groups'");
        assertTrue(memberships.has("troops"), "memberships should contain 'troops'");

        System.out.println("All expected claims present for: " + idToken.get("preferred_username").asText());
    }

    @Test
    void idTokenDoesNotContainInternalAttributes() throws Exception {
        if (username == null || password == null) return;

        JsonNode idToken = fetchIdToken();

        assertFalse(idToken.has("scoutnet_profile_hash"), "scoutnet_profile_hash should not appear in token");
        assertFalse(idToken.has("firstlast"), "firstlast should not appear in token");
    }

    private JsonNode fetchIdToken() throws Exception {
        String tokenUrl = KEYCLOAK_BASE_URL + "/realms/" + REALM + "/protocol/openid-connect/token";

        Map<String, String> params = Map.of(
                "grant_type", "password",
                "client_id", CLIENT_ID,
                "username", username,
                "password", password,
                "scope", "openid profile email phone scoutnet-memberships"
        );

        String body = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "Token endpoint returned " + response.statusCode() + ":\n" + response.body());

        JsonNode tokenResponse = mapper.readTree(response.body());
        assertTrue(tokenResponse.has("id_token"), "Response should contain id_token");

        return decodeJwtPayload(tokenResponse.get("id_token").asText());
    }

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        return mapper.readTree(payloadBytes);
    }

    private void assertClaim(JsonNode token, String claim) {
        assertTrue(token.has(claim), "expected claim: " + claim);
        assertFalse(token.get(claim).isNull(), "claim should not be null: " + claim);
        if (token.get(claim).isTextual()) {
            assertFalse(token.get(claim).asText().isBlank(), "claim should not be blank: " + claim);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
