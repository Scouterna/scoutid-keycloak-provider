package se.scouterna.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import se.scouterna.keycloak.client.ScoutnetClient;
import se.scouterna.keycloak.client.dto.*; // Added rich profile DTOs

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keycloak Authenticator that validates credentials against an external Scoutnet API.
 * It uses a two-step process: authenticate to get a token, then use the token to fetch the full user profile.
 * It then creates or updates the Keycloak user with the rich profile data (addresses, memberships, etc.).
 */
public class ScoutnetAuthenticator implements Authenticator {

    private static final Logger log = Logger.getLogger(ScoutnetAuthenticator.class);
    private final ScoutnetClient scoutnetClient;

    // Initialize the client via the constructor (from the user_creation branch)
    public ScoutnetAuthenticator() {
        this.scoutnetClient = new ScoutnetClient();
    }

    /**
     * This method is called when the authenticator is first executed in the flow.
     * Its ONLY job is to display the username and password form to the user.
     * (Retained from the main branch)
     */
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        log.info("Displaying login form for Scoutnet authentication.");
        // This will render the standard login.ftl template
        context.challenge(context.form().createLoginUsernamePassword());
    }

    /**
     * This method is called ONLY after the user has submitted the form.
     * Contains the full authentication and user creation/update logic.
     */
    @Override
    public void action(AuthenticationFlowContext context) {
        log.info("Processing submitted login form for Scoutnet authentication.");
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        // Basic validation
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            failAuthentication(context, username, "Please provide both a username and a password.");
            return;
        }

        // Step 1: Authenticate and get the token
        AuthResponse authResponse = scoutnetClient.authenticate(username, password);
        if (authResponse == null || authResponse.getToken() == null || authResponse.getToken().isEmpty()) {
            failAuthentication(context, username, "Invalid username or password.");
            return;
        }

        // Step 2: Use the token to fetch the full profile
        Profile profile = scoutnetClient.getProfile(authResponse.getToken());
        if (profile == null) {
            failAuthentication(context, username, "Could not retrieve user profile from Scoutnet after successful login.");
            return;
        }

        // Step 2b: Fetch profile image (non-blocking failure - if image fails, we still proceed)
        byte[] profileImage = scoutnetClient.getProfileImage(authResponse.getToken());

        // Step 2c: Fetch roles information from user (non-blocking)
        Roles roles = scoutnetClient.getRoles(authResponse.getToken());

        if (roles == null) {
            log.infof("Could not retrieve user roles from Scoutnet after successful login.");
        }

        // Step 3: Find or create the Keycloak user
        String keycloakUsername = "scoutnet-" + profile.getMemberNo();
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), keycloakUsername);

        if (user == null) {
            log.infof("First time login for Scoutnet user: %d. Creating new Keycloak user: %s.", profile.getMemberNo(), keycloakUsername);
            user = context.getSession().users().addUser(context.getRealm(), keycloakUsername);
            user.setEnabled(true);
        } else {
            log.infof("Updating existing Keycloak user: %s from Scoutnet profile.", keycloakUsername);
        }

        // Step 4: Update the user with the rich profile data
        updateUserFromProfile(user, profile, profileImage, roles);

        context.setUser(user);
        context.getAuthenticationSession().removeAuthNote("username");
        context.success();
    }

    private void updateUserFromProfile(UserModel user, Profile profile, byte[] imageBytes, Roles roles) {
        // --- Basic Info ---
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        user.setEmail(profile.getEmail());

        // --- Custom Attributes ---
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(profile.getMemberNo()));
        user.setSingleAttribute("scoutnet_dob", profile.getDob());

        // --- OIDC Picture ---
        if (imageBytes != null && imageBytes.length > 0) {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            // We use the standard OIDC attribute name "picture".
            // We prepend the data URI scheme so clients use it directly as <img src="...">
            // We forced the format to JPEG in the client.
            user.setSingleAttribute("picture", "data:image/jpeg;base64," + base64Image);
        }

        // --- Roles ---
        if (roles != null) {
            List<String> roleList = parseAndFlattenRoles(roles);
            
            // This is the correct method call: setting the final list of strings
            // for the custom attribute named "roles".
            user.setAttribute("roles", roleList); 
        }

        // --- Memberships ---
        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            profile.getMemberships().getGroup().values().stream()
                .filter(GroupMembership::isPrimary)
                .findFirst()
                .ifPresent(primaryMembership -> {
                    Group group = primaryMembership.getGroup();
                    if (group != null) {
                        user.setSingleAttribute("scoutnet_primary_group_name", group.getName());
                        user.setSingleAttribute("scoutnet_primary_group_no", String.valueOf(group.getGroupNo()));
                    }
                });
        }
    }

    private List<String> parseAndFlattenRoles(Roles roles) {
        Set<String> roleSet = new HashSet<>();
        
        // The Map type now accurately reflects the three-level nesting: 
        // Key (Role Type Name) -> Value (Map<Type ID, Map<Role ID, Role Name>>)
        Map<String, Map<String, Map<String, String>>> allRolesMap = new HashMap<>();

        // --- AGGREGATE ALL ROLE MAPS FROM THE ROLES OBJECT ---
        // Safely aggregate all role types into one map for simplified iteration.
        // NOTE: Ensure your Roles.java class has working getters for all fields.
        if (roles.getOrganisation() != null) allRolesMap.put("organisation", roles.getOrganisation());
        if (roles.getGroup() != null) allRolesMap.put("group", roles.getGroup());
        // ... add all other role types (region, project, troop, etc.) ...
        if (roles.getRegion() != null) allRolesMap.put("region", roles.getRegion());
        if (roles.getProject() != null) allRolesMap.put("project", roles.getProject());
        if (roles.getNetwork() != null) allRolesMap.put("network", roles.getNetwork());
        if (roles.getCorps() != null) allRolesMap.put("corps", roles.getCorps());
        if (roles.getDistrict() != null) allRolesMap.put("district", roles.getDistrict());
        if (roles.getTroop() != null) allRolesMap.put("troop", roles.getTroop());
        if (roles.getPatrol() != null) allRolesMap.put("patrol", roles.getPatrol());


        // --- ITERATION AND FLATTENING LOGIC ---

        // 1. Iterate over the role types (e.g., "organisation")
        for (Map.Entry<String, Map<String, Map<String, String>>> roleTypeEntry : allRolesMap.entrySet()) {
            String roleType = roleTypeEntry.getKey();
            // rolesForType is now the Map<Type ID, Map<Role ID, Role Name>>
            Map<String, Map<String, String>> rolesForType = roleTypeEntry.getValue();

            if (rolesForType != null && !rolesForType.isEmpty()) {

                // 2. Iterate over the Type IDs (e.g., "692")
                for (Map.Entry<String, Map<String, String>> roleTypeIdEntry : rolesForType.entrySet()) {
                    String roleTypeId = roleTypeIdEntry.getKey();
                    // rolesForTypeId is now the Map<Role ID, Role Name>
                    Map<String, String> rolesForTypeId = roleTypeIdEntry.getValue();
                    
                    if (rolesForTypeId != null && !rolesForTypeId.isEmpty()) {
                        
                        // 3. Iterate over the Role ID/Role Name pairs (e.g., "68": "board_member")
                        for (Map.Entry<String, String> finalRoleEntry : rolesForTypeId.entrySet()) {
                            // String roleId = finalRoleEntry.getKey(); // Role ID (not used in final string)
                            String roleName = finalRoleEntry.getValue(); // Role Name

                            // Generate all wildcard combinations based on your PHP logic:
                            
                            // Full specific role: organisation:692:board_member
                            roleSet.add(roleType + ":" + roleTypeId + ":" + roleName);
                            
                            // Type-wide role: organisation:*:board_member
                            roleSet.add(roleType + ":*:" + roleName);
                            
                            // Global role: *:*:board_member
                            roleSet.add("*:*:" + roleName);
                        }
                        
                        // Add the wildcard for all roles within this specific type ID: organisation:692:*
                        roleSet.add(roleType + ":" + roleTypeId + ":*");
                    }
                }
                
                // Add the wildcard for all roles within this specific type: organisation:*:*
                roleSet.add(roleType + ":*:*");
            }
        }
        
        // Finalize the list: convert Set to List and sort
        List<String> roleList = new ArrayList<>(roleSet);
        Collections.sort(roleList);
        return roleList;
    }

    /**
     * Helper to log and return an error challenge to the user.
     * (Helper method imported from the user_creation branch)
     */
    private void failAuthentication(AuthenticationFlowContext context, String username, String error) {
        context.getEvent().user(username).error("invalid_grant");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
            context.form().setError(error).createLoginUsernamePassword());
    }

    // --- Boilerplate methods unchanged ---
    @Override
    public boolean requiresUser() { return false; }
    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}
    @Override
    public void close() {}
}