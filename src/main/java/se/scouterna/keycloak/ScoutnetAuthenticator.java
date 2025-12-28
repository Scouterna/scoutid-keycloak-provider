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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.infof("[%s] Processing submitted login form for Scoutnet authentication.", correlationId);
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        // Basic validation
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            failAuthentication(context, username, "Please provide both a username and a password.", correlationId);
            return;
        }

        if (username.contains("@") || (username.matches("\\d{7}") && !username.matches("\\d{10,12}"))) {
            // Email addresses and 7-digit member numbers don't need normalization
        } else {
            username = normalizePersonnummer(username);
        }

        // Step 1: Authenticate and get a token
        AuthResult authResult = scoutnetClient.authenticate(username, password, correlationId);
        if (!authResult.isSuccess()) {
            String messageKey = authResult.getError() == AuthResult.AuthError.INVALID_CREDENTIALS 
                ? "invalidUserMessage" 
                : "loginTimeout";
            failAuthentication(context, username, messageKey, correlationId);
            return;
        }
        
        AuthResponse authResponse = authResult.getAuthResponse();
        if (authResponse.getToken() == null || authResponse.getToken().isEmpty()) {
            failAuthentication(context, username, "loginTimeout", correlationId);
            return;
        }

        // Step 2: Use the token to fetch the full profile
        Profile profile = scoutnetClient.getProfile(authResponse.getToken(), correlationId);
        if (profile == null) {
            String errorMsg = String.format("Could not retrieve user profile from Scoutnet after successful login for user: %s", username);
            failAuthentication(context, username, errorMsg, correlationId);
            return;
        }

        // Step 2b: Fetch profile image (non-blocking failure - if image fails, we still proceed)
        // byte[] profileImage = scoutnetClient.getProfileImage(authResponse.getToken(), correlationId);
        byte[] profileImage = null;

        // Step 2c: Fetch roles information from user (non-blocking)
        Roles roles = scoutnetClient.getRoles(authResponse.getToken(), correlationId);

        if (roles == null) {
            log.infof("[%s] Could not retrieve user roles from Scoutnet after successful login.", correlationId);
        }

        // Step 3: Find or create the Keycloak user
        String keycloakUsername = "scoutnet|" + profile.getMemberNo();
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), keycloakUsername);

        if (user == null) {
            log.infof("[%s] First time login for Scoutnet user: %d. Creating new Keycloak user: %s.", correlationId, profile.getMemberNo(), keycloakUsername);
            user = context.getSession().users().addUser(context.getRealm(), keycloakUsername);
            user.setEnabled(true);
        } else {
            log.infof("[%s] Updating existing Keycloak user: %s from Scoutnet profile.", correlationId, keycloakUsername);
        }

        // Step 4: Update the user with the rich profile data
        updateUserFromProfile(user, profile, profileImage, roles);

        context.setUser(user);
        context.getAuthenticationSession().removeAuthNote("username");
        log.infof("[%s] Authentication successful for user: %s", correlationId, keycloakUsername);
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
     */
    private void failAuthentication(AuthenticationFlowContext context, String username, String messageKey, String correlationId) {
        log.errorf("[%s] Authentication failed for user %s: %s", correlationId, username, messageKey);
        context.getEvent().user(username).error("invalid_grant");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
            context.form().setError(messageKey).createLoginUsernamePassword());
    }

    private String normalizePersonnummer(String input) {
        // Remove hyphen and normalize to 12 digits
        String digits = input.replaceAll("-", "");
        
        if (digits.matches("\\d{10}")) {
            // ÅÅMMDDXXXX -> ÅÅÅÅMMDDXXXX (add century based on age < 100)
            int year = Integer.parseInt(digits.substring(0, 2));
            int currentYear = LocalDate.now().getYear();
            int currentCentury = currentYear / 100;
            int currentYearInCentury = currentYear % 100;
            
            // If year is in future or person would be >= 100, use previous century
            String century = (year > currentYearInCentury || (currentYear - (currentCentury * 100 + year)) >= 100) 
                ? String.valueOf(currentCentury - 1) 
                : String.valueOf(currentCentury);
            
            return century + digits;
        } else if (digits.matches("\\d{12}")) {
            // Already 12 digits
            return digits;
        }
        
        // Return unchanged if not a recognizable personnummer format
        return input;
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