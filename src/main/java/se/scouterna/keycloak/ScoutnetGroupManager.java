package se.scouterna.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import se.scouterna.keycloak.client.dto.Group;
import se.scouterna.keycloak.client.dto.GroupMembership;
import se.scouterna.keycloak.client.dto.Profile;
import se.scouterna.keycloak.client.dto.Roles;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ScoutnetGroupManager {

    private static final Logger log = Logger.getLogger(ScoutnetGroupManager.class);
    private static final String PARENT_GROUP_NAME = "scoutnet";
    
    // Attributes to track for hash changes - add new ones here
    private static final List<String> TRACKED_ATTRIBUTES = Arrays.asList("domain");

    public void syncUserGroups(KeycloakSession session, RealmModel realm, UserModel user, Profile profile, Roles roles, String correlationId) {
        if (roles == null && (profile == null || profile.getMemberships() == null)) {
            log.debugf("[%s] No roles or membership data available, skipping group sync for user: %s", correlationId, user.getUsername());
            return;
        }

        GroupModel parentGroup = ensureParentGroup(realm);
        migrateUserFromRootGroups(user, parentGroup, correlationId);
        
        if (!user.isMemberOf(parentGroup)) {
            user.joinGroup(parentGroup);
            log.debugf("[%s] Added user %s to parent group %s", correlationId, user.getUsername(), PARENT_GROUP_NAME);
        }

        Set<String> targetGroupIds = new HashSet<>();
        Map<String, String> groupNames = extractGroupNames(profile);

        // Process roles-based groups
        if (roles != null) {
            // Process organisation groups
            if (roles.getOrganisation() != null) {
                for (String groupId : roles.getOrganisation().keySet()) {
                    targetGroupIds.add(groupId);
                    GroupModel group = findOrCreateGroup(realm, parentGroup, groupId, groupNames.get(groupId));
                    updateGroupAttributes(group, "organisation");
                    if (!user.isMemberOf(group)) {
                        user.joinGroup(group);
                        log.debugf("[%s] Added user %s to organisation group %s", correlationId, user.getUsername(), groupId);
                    }
                }
            }

            // Process scout groups
            if (roles.getGroup() != null) {
                for (String groupId : roles.getGroup().keySet()) {
                    targetGroupIds.add(groupId);
                    GroupModel group = findOrCreateGroup(realm, parentGroup, groupId, groupNames.get(groupId));
                    updateGroupAttributes(group, "group");
                    if (!user.isMemberOf(group)) {
                        user.joinGroup(group);
                        log.debugf("[%s] Added user %s to scout group %s", correlationId, user.getUsername(), groupId);
                    }
                }
            }

            // Process district groups
            if (roles.getDistrict() != null) {
                for (String groupId : roles.getDistrict().keySet()) {
                    targetGroupIds.add(groupId);
                    GroupModel group = findOrCreateGroup(realm, parentGroup, groupId, null);
                    updateGroupAttributes(group, "district");
                    if (!user.isMemberOf(group)) {
                        user.joinGroup(group);
                        log.debugf("[%s] Added user %s to district group %s", correlationId, user.getUsername(), groupId);
                    }
                }
            }
        }

        // Process membership-based groups (from profile)
        if (profile != null && profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            for (Map.Entry<String, GroupMembership> entry : profile.getMemberships().getGroup().entrySet()) {
                String groupId = entry.getKey(); // Use membership key (766) not group_no (1427)
                targetGroupIds.add(groupId);
                GroupModel group = findOrCreateGroup(realm, parentGroup, groupId, entry.getValue().getGroup().getName());
                updateGroupAttributes(group, "group");
                if (!user.isMemberOf(group)) {
                    user.joinGroup(group);
                    log.debugf("[%s] Added user %s to membership group %s", correlationId, user.getUsername(), groupId);
                }
            }
        }

        // Remove user from scoutnet subgroups they're no longer part of
        parentGroup.getSubGroupsStream().forEach(subgroup -> {
            if (!targetGroupIds.contains(subgroup.getName()) && user.isMemberOf(subgroup)) {
                user.leaveGroup(subgroup);
                log.debugf("[%s] Removed user %s from group %s", correlationId, user.getUsername(), subgroup.getName());
            }
        });
    }

    private GroupModel ensureParentGroup(RealmModel realm) {
        return realm.getGroupsStream()
            .filter(g -> PARENT_GROUP_NAME.equals(g.getName()))
            .findFirst()
            .orElseGet(() -> {
                GroupModel parent = realm.createGroup(PARENT_GROUP_NAME);
                parent.setName(PARENT_GROUP_NAME);
                log.infof("Created parent group: %s", PARENT_GROUP_NAME);
                return parent;
            });
    }

    private void migrateUserFromRootGroups(UserModel user, GroupModel parentGroup, String correlationId) {
        user.getGroupsStream()
            .filter(g -> g.getParent() == null)
            .filter(g -> g.getFirstAttribute("scoutnet_type") != null)
            .filter(g -> !PARENT_GROUP_NAME.equals(g.getName()))
            .collect(Collectors.toList())
            .forEach(oldGroup -> {
                user.leaveGroup(oldGroup);
                log.infof("[%s] Migrated user %s from root group %s", correlationId, user.getUsername(), oldGroup.getName());
            });
    }

    private GroupModel findOrCreateGroup(RealmModel realm, GroupModel parentGroup, String groupId, String displayName) {
        GroupModel group = parentGroup.getSubGroupsStream()
            .filter(g -> groupId.equals(g.getName()))
            .findFirst()
            .orElseGet(() -> {
                GroupModel newGroup = realm.createGroup(groupId, parentGroup);
                newGroup.setName(groupId);
                log.debugf("Created new Keycloak subgroup: %s under %s", groupId, PARENT_GROUP_NAME);
                return newGroup;
            });
            
        // Update display name if we have one and it's different from current
        if (displayName != null && !displayName.trim().isEmpty()) {
            String currentName = group.getFirstAttribute("scoutnet_name");
            if (!displayName.equals(currentName)) {
                group.setSingleAttribute("scoutnet_name", displayName);
            }
        }
        
        return group;
    }

    private void updateGroupAttributes(GroupModel group, String groupType) {
        group.setSingleAttribute("scoutnet_type", groupType);
        
        // Initialize tracked attributes if not set
        for (String attribute : TRACKED_ATTRIBUTES) {
            if (group.getFirstAttribute(attribute) == null) {
                group.setSingleAttribute(attribute, "");
            }
        }
    }

    private Map<String, String> extractGroupNames(Profile profile) {
        Map<String, String> groupNames = new HashMap<>();
        
        if (profile != null && profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            for (GroupMembership membership : profile.getMemberships().getGroup().values()) {
                Group group = membership.getGroup();
                if (group != null && group.getName() != null && !group.getName().trim().isEmpty()) {
                    // Map group name to membership key, not group_no
                    for (Map.Entry<String, GroupMembership> entry : profile.getMemberships().getGroup().entrySet()) {
                        if (entry.getValue() == membership) {
                            groupNames.put(entry.getKey(), group.getName());
                            break;
                        }
                    }
                }
            }
        }
        
        return groupNames;
    }
}