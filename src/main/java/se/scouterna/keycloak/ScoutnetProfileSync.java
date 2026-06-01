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
import se.scouterna.keycloak.client.dto.Patrol;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;
import se.scouterna.keycloak.client.dto.RoleSummaryEntry;
import se.scouterna.keycloak.client.dto.Troop;
import se.scouterna.keycloak.model.ScoutnetMemberships;
import se.scouterna.keycloak.model.ScoutnetMemberships.GroupEntry;
import se.scouterna.keycloak.model.ScoutnetMemberships.NamedRoleEntry;
import se.scouterna.keycloak.model.ScoutnetMemberships.RoleEntry;
import se.scouterna.keycloak.model.ScoutnetMemberships.RoleItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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
        user.setEmailVerified(true);
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(profile.getMemberNo()));
        user.setSingleAttribute("birthdate", profile.getDob());

        if (profile.getLanguage() != null && !profile.getLanguage().trim().isEmpty()) {
            user.setSingleAttribute("locale", profile.getLanguage());
        }

        String firstLast = profile.getFirstLast();
        if (firstLast != null && !firstLast.trim().isEmpty()) {
            user.setSingleAttribute("firstlast", firstLast);
            updateGroupEmailAttributes(session, realm, user, firstLast);
        }

        String scouternaEmail = profile.getScouternaEmail();
        if (scouternaEmail != null && !scouternaEmail.trim().isEmpty()) {
            user.setSingleAttribute("scouterna_email", scouternaEmail);
        }

        String altEmail = profile.getAltEmail();
        if (altEmail != null && !altEmail.trim().isEmpty()) {
            user.setSingleAttribute("alt_email", altEmail);
        } else {
            user.removeAttribute("alt_email");
        }

        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().trim().isEmpty()) {
            user.setSingleAttribute("picture", profile.getAvatarUrl());
        }

        String mobilePhone = profile.getMobilePhone();
        if (mobilePhone != null && !mobilePhone.trim().isEmpty()) {
            user.setSingleAttribute("phone_number", mobilePhone);
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
                        user.setSingleAttribute("primary_group_name", group.getName());
                        user.setSingleAttribute("primary_group_no", membershipKey);
                    }
                });
        }

        String membershipsJson = buildMembershipsJson(profile, roles);
        if (membershipsJson != null) {
            user.setSingleAttribute("memberships", membershipsJson);
        } else {
            user.removeAttribute("memberships");
        }

        // Remove attributes superseded by memberships
        user.removeAttribute("scoutnet_definitions");
        user.removeAttribute("scoutnet_troops");
        user.removeAttribute("roles");

        user.setSingleAttribute("scoutnet_profile_hash", newProfileHash);
    }

    static String buildMembershipsJson(Profile profile, Roles roles) {
        ScoutnetMemberships memberships = new ScoutnetMemberships();

        // Role display name lookup — role_summary covers group-level roles only
        Map<String, RoleSummaryEntry> roleSummaryByKey = new HashMap<>();
        if (profile.getRoleSummary() != null) {
            for (RoleSummaryEntry entry : profile.getRoleSummary().values()) {
                if (entry.getRoleKey() != null) {
                    roleSummaryByKey.put(entry.getRoleKey(), entry);
                }
            }
        }

        // Troop and patrol name/groupId lookups from profile membership entries
        Map<String, String> troopNames = new HashMap<>();
        Map<String, Integer> troopGroupIds = new HashMap<>();
        Map<String, String> patrolNames = new HashMap<>();
        Map<String, Integer> patrolGroupIds = new HashMap<>();
        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            for (Map.Entry<String, GroupMembership> gEntry : profile.getMemberships().getGroup().entrySet()) {
                int groupId;
                try { groupId = Integer.parseInt(gEntry.getKey()); } catch (NumberFormatException e) { continue; }
                GroupMembership membership = gEntry.getValue();
                Troop troop = membership.getTroop();
                if (troop != null) {
                    String troopId = String.valueOf(troop.getId());
                    troopNames.put(troopId, troop.getName());
                    troopGroupIds.put(troopId, groupId);
                }
                Patrol patrol = membership.getPatrol();
                if (patrol != null) {
                    String patrolId = String.valueOf(patrol.getId());
                    patrolNames.put(patrolId, patrol.getName());
                    patrolGroupIds.put(patrolId, groupId);
                }
            }
        }

        // Groups
        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            Map<String, GroupEntry> groupEntries = new LinkedHashMap<>();
            for (Map.Entry<String, GroupMembership> entry : profile.getMemberships().getGroup().entrySet()) {
                String groupId = entry.getKey();
                GroupMembership membership = entry.getValue();
                GroupEntry groupEntry = new GroupEntry();

                Group group = membership.getGroup();
                groupEntry.setName(group != null ? group.getName() : null);
                groupEntry.setIsPrimary(membership.isPrimary());
                groupEntry.setRoles(buildRoleItems(membership.getRoles(), roleSummaryByKey, true));
                groupEntries.put(groupId, groupEntry);
            }
            memberships.setGroups(groupEntries);
        }

        if (roles != null) {
            memberships.setTroops(buildNamedRoleEntries(roles.getTroop(), troopNames, troopGroupIds));
            memberships.setPatrols(buildNamedRoleEntries(roles.getPatrol(), patrolNames, patrolGroupIds));
            memberships.setOrganisations(buildSimpleRoleEntries(roles.getOrganisation()));
            memberships.setRegions(buildSimpleRoleEntries(roles.getRegion()));
            memberships.setDistricts(buildSimpleRoleEntries(roles.getDistrict()));
            memberships.setCorps(buildSimpleRoleEntries(roles.getCorps()));
            memberships.setNetworks(buildSimpleRoleEntries(roles.getNetwork()));
            memberships.setProjects(buildSimpleRoleEntries(roles.getProject()));
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(memberships);
        } catch (Exception e) {
            log.warnf("Could not serialize memberships: %s", e.getMessage());
            return null;
        }
    }

    private static Map<String, NamedRoleEntry> buildNamedRoleEntries(
            Map<String, Map<String, String>> rolesForType, Map<String, String> nameMap,
            Map<String, Integer> groupIdMap) {
        if (rolesForType == null || rolesForType.isEmpty()) return null;
        Map<String, NamedRoleEntry> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : rolesForType.entrySet()) {
            String entityId = entry.getKey();
            NamedRoleEntry namedEntry = new NamedRoleEntry();
            namedEntry.setName(nameMap.get(entityId));
            namedEntry.setGroupId(groupIdMap.get(entityId));
            namedEntry.setRoles(buildRoleItems(entry.getValue(), Collections.emptyMap(), false));
            result.put(entityId, namedEntry);
        }
        return result.isEmpty() ? null : result;
    }

    private static Map<String, RoleEntry> buildSimpleRoleEntries(
            Map<String, Map<String, String>> rolesForType) {
        if (rolesForType == null || rolesForType.isEmpty()) return null;
        Map<String, RoleEntry> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : rolesForType.entrySet()) {
            RoleEntry roleEntry = new RoleEntry();
            roleEntry.setRoles(buildRoleItems(entry.getValue(), Collections.emptyMap(), false));
            result.put(entry.getKey(), roleEntry);
        }
        return result.isEmpty() ? null : result;
    }

    private static List<RoleItem> buildRoleItems(Map<String, String> rolesMap,
            Map<String, RoleSummaryEntry> roleSummaryByKey, boolean includeDisplayName) {
        if (rolesMap == null || rolesMap.isEmpty()) return Collections.emptyList();
        List<RoleItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : rolesMap.entrySet()) {
            int roleId;
            try {
                roleId = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            String roleKey = entry.getValue();
            String displayName = null;
            if (includeDisplayName) {
                RoleSummaryEntry summary = roleSummaryByKey.get(roleKey);
                displayName = summary != null ? summary.getRoleName() : null;
            }
            items.add(new RoleItem(roleId, roleKey, displayName));
        }
        items.sort(Comparator.comparing(RoleItem::getKey));
        return items;
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
                    .sorted(Comparator.comparing(GroupModel::getName))
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
                    groupEmailMap.put(groupId, uniqueEmail);
                } else {
                    user.removeAttribute(attributeName);
                }
            });

        try {
            if (!groupEmailMap.isEmpty()) {
                user.setSingleAttribute("group_emails_json", OBJECT_MAPPER.writeValueAsString(groupEmailMap));
            } else {
                user.removeAttribute("group_emails_json");
            }
        } catch (Exception e) {
            log.errorf("Failed to serialize group_emails_json for user %s: %s", user.getUsername(), e.getMessage());
        }

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
