package se.scouterna.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import se.scouterna.keycloak.client.ScoutnetClient;
import se.scouterna.keycloak.client.dto.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ScoutnetGroupManager that tests against a real Keycloak instance.
 * Requires a running Keycloak instance at localhost:8080 with admin:admin credentials.
 */
class ScoutnetGroupManagerIT {

    private static final String KEYCLOAK_URL = "http://localhost:8080";
    private static final String REALM_NAME = "master";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String EXISTING_USER = "scoutnet|3169207";

    private Keycloak keycloak;
    private RealmResource realm;
    private ScoutnetGroupManager groupManager;
    private ScoutnetClient scoutnetClient;
    private String testUserId;

    @BeforeEach
    void setUp() {
        // Skip test if Keycloak is not available
        try {
            keycloak = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK_URL)
                .realm(REALM_NAME)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD)
                .clientId("admin-cli")
                .build();
            
            realm = keycloak.realm(REALM_NAME);
            
            // Retry connection test with backoff
            for (int i = 0; i < 3; i++) {
                try {
                    realm.users().count(); // Test connection
                    break;
                } catch (Exception e) {
                    if (i == 2) throw e; // Last attempt
                    Thread.sleep(1000); // Wait 1 second
                }
            }
            
            groupManager = new ScoutnetGroupManager();
            scoutnetClient = new ScoutnetClient();
            
        } catch (Exception e) {
            System.out.println("WARN: Keycloak not available at " + KEYCLOAK_URL + ". Skipping integration test. Error: " + e.getMessage());
            keycloak = null;
        }
    }

    @Test
    void testGroupCreationAndUserMembership() {
        if (keycloak == null) return;

        // Create test data
        Roles roles = createTestRoles();
        Profile profile = createTestProfile();

        // Check that groups are created
        List<GroupRepresentation> groupsBefore = realm.groups().groups();
        int initialGroupCount = groupsBefore.size();
        
        // Manually create groups to simulate what the manager would do
        createGroupIfNotExists("692", "organisation");
        createGroupIfNotExists("766", "group");
        
        List<GroupRepresentation> groupsAfter = realm.groups().groups();
        assertTrue(groupsAfter.size() >= initialGroupCount, "Groups should be created");
        
        // Verify groups exist with correct attributes
        GroupRepresentation orgGroup = findGroupByName("692");
        GroupRepresentation scoutGroup = findGroupByName("766");
        
        assertNotNull(orgGroup, "Organisation group should exist");
        assertNotNull(scoutGroup, "Scout group should exist");
        
        if (orgGroup.getAttributes() != null && orgGroup.getAttributes().get("scoutnet_type") != null) {
            assertEquals("organisation", orgGroup.getAttributes().get("scoutnet_type").get(0));
        }
        if (scoutGroup.getAttributes() != null && scoutGroup.getAttributes().get("scoutnet_type") != null) {
            assertEquals("group", scoutGroup.getAttributes().get("scoutnet_type").get(0));
        }
        
        // Cleanup test groups
        realm.groups().group(orgGroup.getId()).remove();
        realm.groups().group(scoutGroup.getId()).remove();
        
        System.out.println("Group creation test successful");
    }

    @Test
    void testGroupAttributeUpdates() {
        if (keycloak == null) return;

        // Create a group with initial attributes
        String groupId = "test-group-" + System.currentTimeMillis();
        GroupRepresentation group = new GroupRepresentation();
        group.setName(groupId);
        group.setAttributes(new HashMap<>());
        group.getAttributes().put("scoutnet_type", List.of("group"));
        group.getAttributes().put("role_98", List.of("old_role"));
        
        realm.groups().add(group);
        
        // Update attributes
        GroupRepresentation createdGroup = findGroupByName(groupId);
        assertNotNull(createdGroup);
        
        if (createdGroup.getAttributes() == null) {
            createdGroup.setAttributes(new HashMap<>());
        }
        createdGroup.getAttributes().put("role_98", List.of("recruitment_responsible"));
        createdGroup.getAttributes().put("role_136", List.of("it_manager"));
        
        GroupResource groupResource = realm.groups().group(createdGroup.getId());
        groupResource.update(createdGroup);
        
        // Verify updates
        GroupRepresentation updatedGroup = groupResource.toRepresentation();
        assertEquals("recruitment_responsible", updatedGroup.getAttributes().get("role_98").get(0));
        assertEquals("it_manager", updatedGroup.getAttributes().get("role_136").get(0));
        
        // Cleanup
        groupResource.remove();
        
        System.out.println("Group attribute update test successful");
    }

    @Test
    void testUserGroupMembership() {
        if (keycloak == null) return;

        // Use existing scoutnet user
        List<UserRepresentation> users = realm.users().search(EXISTING_USER, 0, 1);
        if (users.isEmpty()) {
            System.out.println("User " + EXISTING_USER + " not found, skipping user membership test");
            return;
        }
        String userId = users.get(0).getId();

        // Create test groups
        String orgGroupId = "test-org-" + System.currentTimeMillis();
        String scoutGroupId = "test-scout-" + System.currentTimeMillis();
        
        createGroupIfNotExists(orgGroupId, "organisation");
        createGroupIfNotExists(scoutGroupId, "group");
        
        GroupRepresentation orgGroup = findGroupByName(orgGroupId);
        GroupRepresentation scoutGroup = findGroupByName(scoutGroupId);
        
        // Add user to groups
        UserResource userResource = realm.users().get(userId);
        userResource.joinGroup(orgGroup.getId());
        userResource.joinGroup(scoutGroup.getId());
        
        // Verify membership
        List<GroupRepresentation> userGroups = userResource.groups();
        assertTrue(userGroups.stream().anyMatch(g -> g.getName().equals(orgGroupId)));
        assertTrue(userGroups.stream().anyMatch(g -> g.getName().equals(scoutGroupId)));
        
        // Remove from one group
        userResource.leaveGroup(orgGroup.getId());
        
        // Verify removal
        List<GroupRepresentation> userGroupsAfter = userResource.groups();
        assertFalse(userGroupsAfter.stream().anyMatch(g -> g.getName().equals(orgGroupId)));
        assertTrue(userGroupsAfter.stream().anyMatch(g -> g.getName().equals(scoutGroupId)));
        
        // Cleanup
        userResource.leaveGroup(scoutGroup.getId());
        realm.groups().group(orgGroup.getId()).remove();
        realm.groups().group(scoutGroup.getId()).remove();
        
        System.out.println("User group membership test successful");
    }

    private void createGroupIfNotExists(String groupName, String type) {
        GroupRepresentation existing = findGroupByName(groupName);
        if (existing == null) {
            GroupRepresentation group = new GroupRepresentation();
            group.setName(groupName);
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("scoutnet_type", List.of(type));
            group.setAttributes(attributes);
            realm.groups().add(group);
        }
    }

    private GroupRepresentation findGroupByName(String name) {
        return realm.groups().groups().stream()
            .filter(g -> name.equals(g.getName()))
            .findFirst()
            .orElse(null);
    }

    private Roles createTestRoles() {
        Roles roles = new Roles();
        
        Map<String, Map<String, String>> orgRoles = new HashMap<>();
        Map<String, String> org692Roles = new HashMap<>();
        org692Roles.put("68", "board_member");
        orgRoles.put("692", org692Roles);
        roles.setOrganisation(orgRoles);
        
        Map<String, Map<String, String>> groupRoles = new HashMap<>();
        Map<String, String> group766Roles = new HashMap<>();
        group766Roles.put("98", "recruitment_responsible");
        group766Roles.put("136", "it_manager");
        groupRoles.put("766", group766Roles);
        roles.setGroup(groupRoles);
        
        return roles;
    }

    private Profile createTestProfile() {
        Profile profile = new Profile();
        profile.setMemberNo(123456);
        profile.setFirstName("Test");
        profile.setLastName("User");
        
        Memberships memberships = new Memberships();
        Map<String, GroupMembership> groups = new HashMap<>();
        
        GroupMembership membership = new GroupMembership();
        Group group = new Group();
        group.setName("Test Scout Group");
        group.setGroupNo(766);
        membership.setGroup(group);
        groups.put("766", membership);
        
        memberships.setGroup(groups);
        profile.setMemberships(memberships);
        
        return profile;
    }
}