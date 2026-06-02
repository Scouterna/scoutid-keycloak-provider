package se.scouterna.keycloak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScoutnetProfileSyncTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

    private static final String FIXTURES = "/fixtures/buildMembershipsJson/";

    private <T> T loadFixture(String filename, Class<T> type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(FIXTURES + filename)) {
            assertNotNull(in, "Missing fixture: " + filename);
            return MAPPER.readValue(in, type);
        }
    }

    private JsonNode loadExpected(String filename) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(FIXTURES + filename)) {
            assertNotNull(in, "Missing fixture: " + filename);
            return MAPPER.readTree(in);
        }
    }

    @Test
    void buildMembershipsJson_fullScenario() throws Exception {
        Profile profile = loadFixture("profile_input.json", Profile.class);
        Roles roles = loadFixture("roles_input.json", Roles.class);

        String result = ScoutnetProfileSync.buildMembershipsJson(profile, roles);

        assertNotNull(result);
        assertEquals(loadExpected("expected_output.json"), MAPPER.readTree(result));
    }

    @Test
    void buildMembershipsJson_nullRoles_allFieldsPresent() throws Exception {
        Profile profile = loadFixture("profile_input.json", Profile.class);

        String result = ScoutnetProfileSync.buildMembershipsJson(profile, null);

        assertNotNull(result);
        JsonNode root = MAPPER.readTree(result);
        assertTrue(root.has("groups"), "groups should always be present");
        assertTrue(root.has("troops"), "troops should always be present");
        assertTrue(root.has("patrols"), "patrols should always be present");
        assertTrue(root.has("organisations"), "organisations should always be present");
        assertTrue(root.has("regions"), "regions should always be present");
        assertTrue(root.has("districts"), "districts should always be present");
        assertTrue(root.has("corps"), "corps should always be present");
        assertTrue(root.has("networks"), "networks should always be present");
        assertTrue(root.has("projects"), "projects should always be present");
        assertTrue(root.path("troops").isEmpty(), "troops should be empty when roles is null");
    }

    @Test
    void buildMembershipsJson_groupRoles_sortedByKey() throws Exception {
        Profile profile = loadFixture("profile_input.json", Profile.class);
        Roles roles = loadFixture("roles_input.json", Roles.class);

        String result = ScoutnetProfileSync.buildMembershipsJson(profile, roles);

        JsonNode groupRoles = MAPPER.readTree(result).path("groups").path("999").path("roles");
        assertEquals("group_committee_2", groupRoles.get(0).path("key").asText());
        assertEquals("it_manager", groupRoles.get(1).path("key").asText());
        assertEquals("nomination_committee", groupRoles.get(2).path("key").asText());
        assertEquals("recruitment_responsible", groupRoles.get(3).path("key").asText());
    }

    @Test
    void buildMembershipsJson_troopWithoutGroupInProfile_hasNullGroupId() throws Exception {
        Profile profile = loadFixture("profile_input.json", Profile.class);
        Roles roles = loadFixture("roles_input.json", Roles.class);

        String result = ScoutnetProfileSync.buildMembershipsJson(profile, roles);

        JsonNode troop12346 = MAPPER.readTree(result).path("troops").path("12346");
        assertTrue(troop12346.path("groupId").isNull(), "groupId should be null for troop not found in profile");
        assertFalse(troop12346.has("name"), "name should be absent for troop not found in profile");
    }

    @Test
    void buildMembershipsJson_noRoleSummary_groupRolesHaveNoName() throws Exception {
        Profile profile = loadFixture("profile_input.json", Profile.class);
        profile.setRoleSummary(null);
        Roles roles = loadFixture("roles_input.json", Roles.class);

        String result = ScoutnetProfileSync.buildMembershipsJson(profile, roles);

        JsonNode roles999 = MAPPER.readTree(result).path("groups").path("999").path("roles");
        assertTrue(roles999.isArray() && roles999.size() > 0);
        for (JsonNode roleItem : roles999) {
            assertFalse(roleItem.has("name"), "role name should be absent when role_summary is null");
        }
    }

    @Test
    void buildMembershipsJson_nullMemberships_allFieldsPresentButEmpty() throws Exception {
        Profile profile = new Profile();
        profile.setMemberships(null);

        String result = ScoutnetProfileSync.buildMembershipsJson(profile, null);

        assertNotNull(result);
        JsonNode root = MAPPER.readTree(result);
        assertTrue(root.path("groups").isEmpty(), "groups should be empty when profile has no memberships");
        assertTrue(root.path("troops").isEmpty());
        assertTrue(root.path("organisations").isEmpty());
        assertTrue(root.path("regions").isEmpty());
    }

    @Test
    void rolesDeserialization_emptyArraysTreatedAsNull() throws Exception {
        Roles roles = loadFixture("roles_empty_arrays.json", Roles.class);

        assertNull(roles.getRegion(),   "region: [] should deserialize as null");
        assertNull(roles.getDistrict(), "district: [] should deserialize as null");
        assertNull(roles.getProject(),  "project: [] should deserialize as null");
        assertNull(roles.getNetwork(),  "network: [] should deserialize as null");
        assertNull(roles.getCorps(),    "corps: [] should deserialize as null");
        assertNull(roles.getTroop(),    "troop: [] should deserialize as null");
        assertNull(roles.getPatrol(),   "patrol: [] should deserialize as null");
        assertNotNull(roles.getGroup(), "non-empty group map should still deserialize");
    }
}
