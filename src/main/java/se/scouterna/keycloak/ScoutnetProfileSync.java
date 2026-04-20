package se.scouterna.keycloak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import se.scouterna.keycloak.client.ScoutnetClient;
import se.scouterna.keycloak.client.dto.Group;
import se.scouterna.keycloak.client.dto.GroupMembership;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;
import se.scouterna.keycloak.client.dto.RoleSummaryEntry;
import se.scouterna.keycloak.client.dto.Troop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Shared logic for fetching Scoutnet profile/roles data and syncing it into Keycloak.
 * Used by both ScoutnetAuthenticator (password login) and ScoutnetCookieAuthenticator (token-based re-auth).
 */
public class ScoutnetProfileSync {

    private static final Logger log = Logger.getLogger(ScoutnetProfileSync.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
    private static final String PROVIDER_VERSION = getProviderVersion();
    private static final List<String> TRACKED_ATTRIBUTES = Arrays.asList("domain");

    private final ScoutnetClient scoutnetClient;
    private final ScoutnetGroupManager groupManager;

    public ScoutnetProfileSync(ScoutnetClient scoutnetClient, ScoutnetGroupManager groupManager) {
        this.scoutnetClient = scoutnetClient;
        this.groupManager = groupManager;
    }

    /**
     * Result of a profile fetch from Scoutnet.
     */
    public static class FetchResult {
        private final Profile profile;
        private final String profileJson;
        private final Roles roles;
        private final String rolesJson;

        public FetchResult(Profile profile, String profileJson, Roles roles, String rolesJson) {
            this.profile = profile;
            this.profileJson = profileJson;
            this.roles = roles;
            this.rolesJson = rolesJson;
        }

        public Profile getProfile() { return profile; }
        public String getProfileJson() { return profileJson; }
        public Roles getRoles() { return roles; }
        public String getRolesJson() { return rolesJson; }
    }

    /**
     * Fetches profile and roles from Scoutnet using the given token.
     *
     * @return FetchResult, or null if profile fetch failed.
     */
    public FetchResult fetchProfileAndRoles(String token, String correlationId) {
        String profileJson = scoutnetClient.getProfileJson(token, correlationId);
        if (profileJson == null) return null;

        Profile profile;
        try {
            profile = OBJECT_MAPPER.readValue(profileJson, Profile.class);
        } catch (Exception e) {
            log.errorf("[%s] Could not parse profile JSON: %s", correlationId, e.getClass().getSimpleName());
            return null;
        }

        String rolesJson = scoutnetClient.getRolesJson(token, correlationId);
        Roles roles = null;
        if (rolesJson != null) {
            try {
                roles = OBJECT_MAPPER.readValue(rolesJson, Roles.class);
            } catch (Exception e) {
                log.warnf("[%s] Could not parse user roles from Scoutnet: %s", correlationId, e.getMessage());
            }
        } else {
            log.debugf("[%s] Could not retrieve user roles from Scoutnet.", correlationId);
        }

        return new FetchResult(profile, profileJson, roles, rolesJson);
    }

    /**
     * Syncs profile and roles data into the Keycloak user model.
     * Skips update if the profile hash hasn't changed.
     */
    public void syncUserProfile(KeycloakSession session, RealmModel realm, UserModel user,
                                FetchResult fetchResult, String correlationId) {
        Profile profile = fetchResult.getProfile();
        String profileJson = fetchResult.getProfileJson();
        Roles roles = fetchResult.getRoles();
        String rolesJson = fetchResult.getRolesJson();

        String newProfileHash = generateProfileHash(realm, user, profileJson, rolesJson);
        String currentProfileHash = user.getFirstAttribute("scoutnet_profile_hash");

        if (newProfileHash.equals(currentProfileHash)) {
            log.debugf("[%s] Profile hash unchanged (%s), skipping update for user: %s",
                correlationId, newProfileHash.substring(0, 8), user.getUsername());
            return;
        }

        log.infof("[%s] Profile hash changed (old: %s, new: %s), updating user: %s",
            correlationId,
            currentProfileHash != null ? currentProfileHash.substring(0, 8) : "null",
            newProfileHash.substring(0, 8), user.getUsername());

        groupManager.syncUserGroups(session, realm, user, profile, roles, correlationId);

        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        user.setEmail(profile.getEmail());
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(profile.getMemberNo()));
        user.setSingleAttribute("scoutnet_dob", profile.getDob());
        user.setSingleAttribute("scoutid_local_email", profile.getScoutIdLocalEmail());

        String firstLast = profile.getFirstLast();
        if (firstLast != null && !firstLast.trim().isEmpty()) {
            user.setSingleAttribute("firstlast", firstLast);
            updateGroupEmailAttributes(session, realm, user, firstLast);
        } else {
            user.getAttributes().keySet().stream()
                .filter(attr -> attr.startsWith("group_email_"))
                .forEach(user::removeAttribute);
        }

        String scouternaEmail = profile.getScouternaEmail();
        if (scouternaEmail != null && !scouternaEmail.trim().isEmpty()) {
            user.setSingleAttribute("scouterna_email", scouternaEmail);
        }

        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().trim().isEmpty()) {
            user.setSingleAttribute("picture", profile.getAvatarUrl());
        }

        if (roles != null) {
            List<String> roleList = parseAndFlattenRoles(roles);
            user.setAttribute("roles", roleList);
        }

        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            Map<String, GroupMembership> groups = profile.getMemberships().getGroup();

            groups.entrySet().stream()
                .filter(entry -> entry.getValue().isPrimary())
                .findFirst()
                .ifPresent(primaryEntry -> {
                    String membershipKey = primaryEntry.getKey();
                    Group group = primaryEntry.getValue().getGroup();
                    if (group != null) {
                        user.setSingleAttribute("scoutnet_primary_group_name", group.getName());
                        user.setSingleAttribute("scoutnet_primary_group_no", membershipKey);
                    }
                });

            String troopsJson = buildTroopsJson(groups);
            if (troopsJson != null) {
                user.setSingleAttribute("scoutnet_troops", troopsJson);
            } else {
                user.removeAttribute("scoutnet_troops");
            }
        }

        String definitionsJson = buildDefinitionsJson(profile);
        if (definitionsJson != null) {
            user.setSingleAttribute("scoutnet_definitions", definitionsJson);
        } else {
            user.removeAttribute("scoutnet_definitions");
        }

        user.setSingleAttribute("scoutnet_profile_hash", newProfileHash);
    }

    private String generateProfileHash(RealmModel realm, UserModel user, String profileJson, String rolesJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PROVIDER_VERSION.getBytes(StandardCharsets.UTF_8));

            String cleanedJson = profileJson.replaceAll(",?\\s*\"last_login\"\\s*:\\s*\"[^\"]*\"", "");
            digest.update(cleanedJson.getBytes(StandardCharsets.UTF_8));

            if (rolesJson != null) {
                digest.update(rolesJson.getBytes(StandardCharsets.UTF_8));
            }

            GroupModel scoutnetParent = realm.getGroupsStream()
                .filter(g -> "scoutnet".equals(g.getName()))
                .findFirst()
                .orElse(null);

            if (scoutnetParent != null) {
                user.getGroupsStream()
                    .filter(group -> scoutnetParent.equals(group.getParent()))
                    .sorted(java.util.Comparator.comparing(GroupModel::getName))
                    .forEach(group -> {
                        for (String attribute : TRACKED_ATTRIBUTES) {
                            String value = group.getFirstAttribute(attribute);
                            digest.update((group.getName() + ":" + attribute + ":" + (value != null ? value : "")).getBytes(StandardCharsets.UTF_8));
                        }
                    });
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void updateGroupEmailAttributes(KeycloakSession session, RealmModel realm, UserModel user, String firstLast) {
        Set<String> processedAttributes = new HashSet<>();
        // Create a map to hold the {groupId: email} pairs
        Map<String, String> groupEmailMap = new HashMap<>();

        user.getGroupsStream()
            .filter(group -> group.getParent() != null && "scoutnet".equals(group.getParent().getName()))
            .forEach(group -> {
                String domain = group.getFirstAttribute("domain");
                String groupId = group.getName(); // e.g., "766"
                String attributeName = "group_email_" + groupId;
                processedAttributes.add(attributeName);

                if (isValidDomain(domain)) {
                    domain = domain.trim();
                    String baseEmail = firstLast + "@" + domain;
                    String uniqueEmail = ensureUniqueEmail(session, realm, user, baseEmail, groupId);
                    
                    user.setSingleAttribute(attributeName, uniqueEmail);
                    
                    // Add to our collection for the JSON object
                    groupEmailMap.put(groupId, uniqueEmail);
                } else {
                    user.removeAttribute(attributeName);
                }
            });

        // Serialize the map to a JSON string and store it in a single attribute
        try {
            if (!groupEmailMap.isEmpty()) {
                String jsonValue = OBJECT_MAPPER.writeValueAsString(groupEmailMap);
                user.setSingleAttribute("group_emails_json", jsonValue);
            } else {
                user.removeAttribute("group_emails_json");
            }
        } catch (Exception e) {
            log.errorf("Failed to serialize group_emails_json for user %s: %s", user.getUsername(), e.getMessage());
        }

        // Cleanup old attributes (making sure we don't delete our new JSON attribute)
        user.getAttributes().keySet().stream()
            .filter(attr -> attr.startsWith("group_email_") && !attr.equals("group_emails_json"))
            .filter(attr -> !processedAttributes.contains(attr))
            .forEach(user::removeAttribute);
    }

    private boolean isValidDomain(String domain) {
        return domain != null &&
               !domain.trim().isEmpty() &&
               domain.contains(".") &&
               !domain.startsWith(".") &&
               !domain.endsWith(".") &&
               !domain.contains("/") &&
               !domain.contains(" ") &&
               !domain.contains(":") &&
               domain.length() > 3;
    }

    private String ensureUniqueEmail(KeycloakSession session, RealmModel realm, UserModel currentUser, String baseEmail, String groupName) {
        String email = baseEmail;
        int counter = 1;
        String attributeName = "group_email_" + groupName;

        while (isEmailInUse(session, realm, currentUser, email, attributeName)) {
            String[] parts = baseEmail.split("@");
            email = parts[0] + counter + "@" + parts[1];
            counter++;
        }

        return email;
    }

    private boolean isEmailInUse(KeycloakSession session, RealmModel realm, UserModel currentUser, String email, String attributeName) {
        return session.users().searchForUserByUserAttributeStream(realm, attributeName, email)
            .anyMatch(user -> !user.getId().equals(currentUser.getId()));
    }

    private List<String> parseAndFlattenRoles(Roles roles) {
        Set<String> roleSet = new HashSet<>();

        Map<String, Map<String, Map<String, String>>> allRolesMap = new HashMap<>();
        if (roles.getOrganisation() != null) allRolesMap.put("organisation", roles.getOrganisation());
        if (roles.getGroup() != null) allRolesMap.put("group", roles.getGroup());
        if (roles.getRegion() != null) allRolesMap.put("region", roles.getRegion());
        if (roles.getProject() != null) allRolesMap.put("project", roles.getProject());
        if (roles.getNetwork() != null) allRolesMap.put("network", roles.getNetwork());
        if (roles.getCorps() != null) allRolesMap.put("corps", roles.getCorps());
        if (roles.getDistrict() != null) allRolesMap.put("district", roles.getDistrict());
        if (roles.getTroop() != null) allRolesMap.put("troop", roles.getTroop());
        if (roles.getPatrol() != null) allRolesMap.put("patrol", roles.getPatrol());

        for (Map.Entry<String, Map<String, Map<String, String>>> roleTypeEntry : allRolesMap.entrySet()) {
            String roleType = roleTypeEntry.getKey();
            Map<String, Map<String, String>> rolesForType = roleTypeEntry.getValue();

            if (rolesForType != null) {
                for (Map.Entry<String, Map<String, String>> roleTypeIdEntry : rolesForType.entrySet()) {
                    String roleTypeId = roleTypeIdEntry.getKey();
                    Map<String, String> rolesForTypeId = roleTypeIdEntry.getValue();

                    if (rolesForTypeId != null) {
                        for (String roleName : rolesForTypeId.values()) {
                            roleSet.add(roleType + ":" + roleTypeId + ":" + roleName);
                        }
                    }
                }
            }
        }

        List<String> roleList = new ArrayList<>(roleSet);
        Collections.sort(roleList);
        return roleList;
    }

    private String buildDefinitionsJson(Profile profile) {
        Map<String, Object> definitions = new LinkedHashMap<>();

        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            Map<String, String> groupNames = new LinkedHashMap<>();
            Map<String, String> troopNames = new LinkedHashMap<>();
            for (Map.Entry<String, GroupMembership> entry : profile.getMemberships().getGroup().entrySet()) {
                Group group = entry.getValue().getGroup();
                if (group != null && group.getName() != null) {
                    groupNames.put(entry.getKey(), group.getName());
                }
                Troop troop = entry.getValue().getTroop();
                if (troop != null && troop.getName() != null) {
                    troopNames.put(String.valueOf(troop.getId()), troop.getName());
                }
            }
            if (!groupNames.isEmpty()) definitions.put("groups", groupNames);
            if (!troopNames.isEmpty()) definitions.put("troops", troopNames);
        }

        if (profile.getRoleSummary() != null) {
            Map<String, String> roleNames = new LinkedHashMap<>();
            for (RoleSummaryEntry entry : profile.getRoleSummary().values()) {
                if (entry.getRoleKey() != null && entry.getRoleName() != null) {
                    roleNames.put(entry.getRoleKey(), entry.getRoleName());
                }
            }
            if (!roleNames.isEmpty()) definitions.put("roles", roleNames);
        }

        if (definitions.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(definitions);
        } catch (Exception e) {
            log.warnf("Could not serialize definitions JSON: %s", e.getMessage());
            return null;
        }
    }

    private String buildTroopsJson(Map<String, GroupMembership> groups) {
        List<Map<String, Object>> troops = new ArrayList<>();
        for (Map.Entry<String, GroupMembership> entry : groups.entrySet()) {
            Troop troop = entry.getValue().getTroop();
            if (troop != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", troop.getId());
                t.put("name", troop.getName());
                t.put("group_no", entry.getKey());
                troops.add(t);
            }
        }
        if (troops.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(troops);
        } catch (Exception e) {
            log.warnf("Could not serialize troops JSON: %s", e.getMessage());
            return null;
        }
    }

    private static String getProviderVersion() {
        String version = ScoutnetProfileSync.class.getPackage().getImplementationVersion();
        if (version != null) {
            log.infof("ScoutID provider version: %s", version);
            return version;
        } else {
            log.info("ScoutID version unknown (no manifest version found)");
            return "dev";
        }
    }
}
