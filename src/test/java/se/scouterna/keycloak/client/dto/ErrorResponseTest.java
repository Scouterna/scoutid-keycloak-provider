package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String safeMessage(String json) throws Exception {
        return MAPPER.readValue(json, ErrorResponse.class).getSafeErrorMessage();
    }

    @Test
    void testScoutnetErrField() throws Exception {
        // Body returned by https://scoutnet.se/api/authenticate on a failed login
        assertEquals("Incorrect login: Invalid username or password",
            safeMessage("{\"err\":\"Incorrect login: Invalid username or password\"}"));
    }

    @Test
    void testErrTakesPrecedence() throws Exception {
        assertEquals("from err",
            safeMessage("{\"err\":\"from err\",\"message\":\"from message\",\"error\":\"from error\"}"));
    }

    @Test
    void testFallsBackToMessageThenError() throws Exception {
        assertEquals("from message", safeMessage("{\"message\":\"from message\",\"error\":\"from error\"}"));
        assertEquals("from error", safeMessage("{\"err\":\" \",\"error\":\"from error\"}"));
    }

    @Test
    void testUnknownErrorWhenNoField() throws Exception {
        assertEquals("Unknown error", safeMessage("{}"));
        assertEquals("Unknown error", safeMessage("{\"other\":\"x\"}"));
    }

    @Test
    void testLongMessageIsTruncated() throws Exception {
        String result = safeMessage("{\"err\":\"" + "x".repeat(150) + "\"}");
        assertEquals("x".repeat(100) + "...", result);
    }
}
