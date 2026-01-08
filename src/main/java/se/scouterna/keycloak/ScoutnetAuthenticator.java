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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final ScoutnetGroupManager groupManager;
    private static final String PROVIDER_VERSION = getProviderVersion();
    
    // Attributes to track for hash changes - must match ScoutnetGroupManager
    private static final List<String> TRACKED_ATTRIBUTES = Arrays.asList("domain");

    // Initialize the client via the constructor (from the user_creation branch)
    public ScoutnetAuthenticator() {
        this.scoutnetClient = new ScoutnetClient();
        this.groupManager = new ScoutnetGroupManager();
    }
    
    private static String getProviderVersion() {
        String version = ScoutnetAuthenticator.class.getPackage().getImplementationVersion();
        Logger logger = Logger.getLogger(ScoutnetAuthenticator.class);
        
        if (version != null) {
            logger.infof("ScoutID provider version: %s", version);
            return version;
        } else {
            logger.infof("ScoutID version unknown (no manifest version found)");
            return "dev";
        }
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
        String profileJson = scoutnetClient.getProfileJson(authResponse.getToken(), correlationId);
        if (profileJson == null) {
            String errorMsg = String.format("Could not retrieve user profile from Scoutnet after successful login for user: %s", username);
            failAuthentication(context, username, errorMsg, correlationId);
            return;
        }
        
        Profile profile;
        try {
            profile = new com.fasterxml.jackson.databind.ObjectMapper().readValue(profileJson, Profile.class);
        } catch (Exception e) {
            String errorMsg = String.format("Could not parse user profile from Scoutnet after successful login for user: %s", username);
            failAuthentication(context, username, errorMsg, correlationId);
            return;
        }

        // Step 2b: Fetch profile image (non-blocking failure - if image fails, we still proceed)
        // byte[] profileImage = scoutnetClient.getProfileImage(authResponse.getToken(), correlationId);
        byte[] profileImage = null;

        // Step 2c: Fetch roles information from user (non-blocking)
        String rolesJson = scoutnetClient.getRolesJson(authResponse.getToken(), correlationId);
        Roles roles = null;
        if (rolesJson != null) {
            try {
                roles = new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
                    .readValue(rolesJson, Roles.class);
            } catch (Exception e) {
                log.warnf("[%s] Could not parse user roles from Scoutnet: %s", correlationId, e.getMessage());
            }
        } else {
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
            log.debugf("[%s] Found existing Keycloak user: %s, checking for profile updates.", correlationId, keycloakUsername);
        }

        // Step 4: Update the user with the rich profile data
        updateUserFromProfile(context.getSession(), context.getRealm(), user, profile, profileJson, rolesJson, profileImage, roles, correlationId);

        context.setUser(user);
        context.getAuthenticationSession().removeAuthNote("username");
        log.infof("[%s] Authentication successful for user: %s", correlationId, keycloakUsername);
        context.success();
    }

    private void updateUserFromProfile(KeycloakSession session, RealmModel realm, UserModel user, Profile profile, String profileJson, String rolesJson, byte[] imageBytes, Roles roles, String correlationId) {
        // Generate hash of relevant profile, group and provider data
        String newProfileHash = generateProfileHash(realm, user, profileJson, rolesJson, imageBytes, profile, roles);
        String currentProfileHash = user.getFirstAttribute("scoutnet_profile_hash");
        
        // Skip update if hash has not changed
        if (newProfileHash.equals(currentProfileHash)) {
            log.infof("Profile hash unchanged (%s), skipping update for user: %s", 
                newProfileHash.substring(0, 8), user.getUsername());
            return;
        }

        log.infof("Profile hash changed (old: %s, new: %s), updating user: %s", 
            currentProfileHash != null ? currentProfileHash.substring(0, 8) : "null", 
            newProfileHash.substring(0, 8), user.getUsername());

        // Sync user groups
        groupManager.syncUserGroups(session, realm, user, profile, roles, correlationId);
        
        // Update all profile data
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        user.setEmail(profile.getEmail());
        user.setSingleAttribute("scoutnet_member_no", String.valueOf(profile.getMemberNo()));
        user.setSingleAttribute("scoutnet_dob", profile.getDob());
        user.setSingleAttribute("scoutid_local_email", profile.getScoutIdLocalEmail());
        
        String firstLast = profile.getFirstLast();
        String currentFirstLast = user.getFirstAttribute("firstlast");
        
        if (firstLast != null && !firstLast.trim().isEmpty()) {
            user.setSingleAttribute("firstlast", firstLast);
            
            // Only update group emails if firstLast has changed
            if (!firstLast.equals(currentFirstLast)) {
                updateGroupEmailAttributes(session, realm, user, firstLast);
            }
        } else {
            // Clear all group-email attributes if firstLast is empty
            user.getAttributes().keySet().stream()
                .filter(attr -> attr.startsWith("group_email_"))
                .forEach(attr -> user.removeAttribute(attr));
        }
        
        String scouternaEmail = profile.getScouternaEmail();
        if (scouternaEmail != null && !scouternaEmail.trim().isEmpty()) {
            user.setSingleAttribute("scouterna_email", scouternaEmail);
        }

        if (imageBytes != null && imageBytes.length > 0) {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            user.setSingleAttribute("picture", "data:image/jpeg;base64," + base64Image);
        }

        if (roles != null) {
            List<String> roleList = parseAndFlattenRoles(roles);
            user.setAttribute("roles", roleList);
        }

        if (profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            profile.getMemberships().getGroup().entrySet().stream()
                .filter(entry -> entry.getValue().isPrimary())
                .findFirst()
                .ifPresent(primaryEntry -> {
                    String membershipKey = primaryEntry.getKey();
                    GroupMembership primaryMembership = primaryEntry.getValue();
                    Group group = primaryMembership.getGroup();
                    if (group != null) {
                        user.setSingleAttribute("scoutnet_primary_group_name", group.getName());
                        user.setSingleAttribute("scoutnet_primary_group_no", membershipKey);
                    }
                });
        }

        // Store new hash
        user.setSingleAttribute("scoutnet_profile_hash", newProfileHash);
    }

    private String generateProfileHash(RealmModel realm, UserModel user, String profileJson, String rolesJson, byte[] imageBytes, Profile profile, Roles roles) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Add provider version to force rehash when mappings change
            digest.update(PROVIDER_VERSION.getBytes(StandardCharsets.UTF_8));
            
            // Remove last_login from JSON before hashing
            String cleanedJson = profileJson.replaceAll(",?\\s*\"last_login\"\\s*:\\s*\"[^\"]*\"", "");
            digest.update(cleanedJson.getBytes(StandardCharsets.UTF_8));
            
            if (rolesJson != null) {
                digest.update(rolesJson.getBytes(StandardCharsets.UTF_8));
            }
            
            if (imageBytes != null) {
                digest.update(String.valueOf(imageBytes.length).getBytes(StandardCharsets.UTF_8));
            }
            
            // Add group-specific domain attributes for user's actual groups to detect changes
            user.getGroupsStream().forEach(group -> {
                for (String attribute : TRACKED_ATTRIBUTES) {
                    String value = group.getFirstAttribute(attribute);
                    digest.update((group.getName() + ":" + attribute + ":" + (value != null ? value : "")).getBytes(StandardCharsets.UTF_8));
                }
            });
            
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
    
    private Set<String> getUserGroupIds(Profile profile, Roles roles) {
        Set<String> groupIds = new HashSet<>();
        
        if (roles != null) {
            if (roles.getOrganisation() != null) groupIds.addAll(roles.getOrganisation().keySet());
            if (roles.getGroup() != null) groupIds.addAll(roles.getGroup().keySet());
            if (roles.getDistrict() != null) groupIds.addAll(roles.getDistrict().keySet());
        }
        
        if (profile != null && profile.getMemberships() != null && profile.getMemberships().getGroup() != null) {
            for (String membershipKey : profile.getMemberships().getGroup().keySet()) {
                groupIds.add(membershipKey);
            }
        }
        
        return groupIds;
    }
    
    private void updateGroupEmailAttributes(KeycloakSession session, RealmModel realm, UserModel user, String firstLast) {
        Set<String> processedAttributes = new HashSet<>();
        
        // Create/update group-email attributes for user's actual groups
        user.getGroupsStream().forEach(group -> {
            String domain = group.getFirstAttribute("domain");
            domain = domain.trim();
            String attributeName = "group_email_" + group.getName();
            processedAttributes.add(attributeName);
            
            if (domain != null && !domain.isEmpty() && isValidDomain(domain)) {
                String baseEmail = firstLast + "@" + domain;
                String uniqueEmail = ensureUniqueEmail(session, realm, user, baseEmail, group.getName());
                user.setSingleAttribute(attributeName, uniqueEmail);
            } else {
                user.removeAttribute(attributeName);
            }
        });
        
        // Remove old group-email attributes for groups user no longer belongs to
        user.getAttributes().keySet().stream()
            .filter(attr -> attr.startsWith("group_email_"))
            .filter(attr -> !processedAttributes.contains(attr))
            .forEach(attr -> user.removeAttribute(attr));
    }
    
    private boolean isValidDomain(String domain) {
        return domain.contains(".") && 
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