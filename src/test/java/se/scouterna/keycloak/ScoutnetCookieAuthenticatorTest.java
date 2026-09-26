package se.scouterna.keycloak;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure logic in the cookie authenticator — fetch throttle,
 * username migration detection, and related utility methods.
 * These don't require mocking Keycloak.
 */
class ScoutnetCookieAuthenticatorTest {

    private final ScoutnetCookieAuthenticator authenticator = new ScoutnetCookieAuthenticator();

    @Test
    void fetchNeeded_whenNoLastFetch() {
        assertTrue(authenticator.isFetchNeeded(null, 60));
    }

    @Test
    void fetchNeeded_whenIntervalExceeded() {
        String twoHoursAgo = String.valueOf(System.currentTimeMillis() - 120 * 60_000L);
        assertTrue(authenticator.isFetchNeeded(twoHoursAgo, 60));
    }

    @Test
    void fetchNotNeeded_whenWithinInterval() {
        String fiveMinutesAgo = String.valueOf(System.currentTimeMillis() - 5 * 60_000L);
        assertFalse(authenticator.isFetchNeeded(fiveMinutesAgo, 60));
    }

    @Test
    void fetchNeeded_whenExactlyAtInterval() {
        String exactlyOneHourAgo = String.valueOf(System.currentTimeMillis() - 60 * 60_000L);
        assertTrue(authenticator.isFetchNeeded(exactlyOneHourAgo, 60));
    }

    @Test
    void fetchNeeded_whenInvalidTimestamp() {
        assertTrue(authenticator.isFetchNeeded("not-a-number", 60));
    }

    @Test
    void fetchNeeded_whenZeroInterval() {
        String justNow = String.valueOf(System.currentTimeMillis());
        assertTrue(authenticator.isFetchNeeded(justNow, 0));
    }

    @Test
    void fetchNotNeeded_whenOneMinuteIntervalAndRecentFetch() {
        String thirtySecondsAgo = String.valueOf(System.currentTimeMillis() - 30_000L);
        assertFalse(authenticator.isFetchNeeded(thirtySecondsAgo, 1));
    }

    @Test
    void fetchNeeded_whenOneMinuteIntervalAndOldFetch() {
        String twoMinutesAgo = String.valueOf(System.currentTimeMillis() - 120_000L);
        assertTrue(authenticator.isFetchNeeded(twoMinutesAgo, 1));
    }

    @Test
    void needsMigration_legacyPipeFormat() {
        assertTrue(authenticator.needsUsernameMigration("scoutnet|1234567"));
    }

    @Test
    void needsMigration_alreadyMigrated() {
        assertFalse(authenticator.needsUsernameMigration("1234567@scoutnet"));
    }

    @Test
    void needsMigration_nullUsername() {
        assertFalse(authenticator.needsUsernameMigration(null));
    }

    @Test
    void migrateUsername_extractsMemberNo() {
        assertEquals("1234567@scoutnet", authenticator.migrateUsername("scoutnet|1234567"));
    }
}
