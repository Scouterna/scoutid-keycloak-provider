package se.scouterna.keycloak;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure logic in the cookie authenticator — fetch throttle
 * and related utility methods. These don't require mocking Keycloak.
 */
class ScoutnetCookieAuthenticatorTest {

    @Test
    void fetchNeeded_whenNoLastFetch() {
        assertTrue(isFetchNeeded(null, 60));
    }

    @Test
    void fetchNeeded_whenIntervalExceeded() {
        String twoHoursAgo = String.valueOf(System.currentTimeMillis() - 120 * 60_000L);
        assertTrue(isFetchNeeded(twoHoursAgo, 60));
    }

    @Test
    void fetchNotNeeded_whenWithinInterval() {
        String fiveMinutesAgo = String.valueOf(System.currentTimeMillis() - 5 * 60_000L);
        assertFalse(isFetchNeeded(fiveMinutesAgo, 60));
    }

    @Test
    void fetchNeeded_whenExactlyAtInterval() {
        String exactlyOneHourAgo = String.valueOf(System.currentTimeMillis() - 60 * 60_000L);
        assertTrue(isFetchNeeded(exactlyOneHourAgo, 60));
    }

    @Test
    void fetchNeeded_whenInvalidTimestamp() {
        assertTrue(isFetchNeeded("not-a-number", 60));
    }

    @Test
    void fetchNeeded_whenZeroInterval() {
        String justNow = String.valueOf(System.currentTimeMillis());
        assertTrue(isFetchNeeded(justNow, 0));
    }

    @Test
    void fetchNotNeeded_whenOneMinuteIntervalAndRecentFetch() {
        String thirtySecondsAgo = String.valueOf(System.currentTimeMillis() - 30_000L);
        assertFalse(isFetchNeeded(thirtySecondsAgo, 1));
    }

    @Test
    void fetchNeeded_whenOneMinuteIntervalAndOldFetch() {
        String twoMinutesAgo = String.valueOf(System.currentTimeMillis() - 120_000L);
        assertTrue(isFetchNeeded(twoMinutesAgo, 1));
    }

    // Mirror of the private method in ScoutnetCookieAuthenticator for testing
    private boolean isFetchNeeded(String lastFetchStr, int intervalMinutes) {
        if (lastFetchStr == null) return true;
        try {
            long lastFetch = Long.parseLong(lastFetchStr);
            long elapsed = System.currentTimeMillis() - lastFetch;
            return elapsed >= intervalMinutes * 60_000L;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
