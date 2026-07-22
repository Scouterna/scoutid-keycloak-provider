package se.scouterna.keycloak.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoutnetMemberships {

    private Map<String, GroupEntry> groups;
    private Map<String, NamedRoleEntry> troops;
    private Map<String, NamedRoleEntry> patrols;
    private Map<String, RoleEntry> organisations;
    private Map<String, RoleEntry> regions;
    private Map<String, RoleEntry> districts;
    private Map<String, RoleEntry> corps;
    private Map<String, RoleEntry> networks;
    // Disabled: projects can push the serialized JSON past Keycloak's 2048-char attribute limit.
    // private Map<String, RoleEntry> projects;
    private String error;

    public Map<String, GroupEntry> getGroups() { return groups; }
    public void setGroups(Map<String, GroupEntry> groups) { this.groups = groups; }
    public Map<String, NamedRoleEntry> getTroops() { return troops; }
    public void setTroops(Map<String, NamedRoleEntry> troops) { this.troops = troops; }
    public Map<String, NamedRoleEntry> getPatrols() { return patrols; }
    public void setPatrols(Map<String, NamedRoleEntry> patrols) { this.patrols = patrols; }
    public Map<String, RoleEntry> getOrganisations() { return organisations; }
    public void setOrganisations(Map<String, RoleEntry> organisations) { this.organisations = organisations; }
    public Map<String, RoleEntry> getRegions() { return regions; }
    public void setRegions(Map<String, RoleEntry> regions) { this.regions = regions; }
    public Map<String, RoleEntry> getDistricts() { return districts; }
    public void setDistricts(Map<String, RoleEntry> districts) { this.districts = districts; }
    public Map<String, RoleEntry> getCorps() { return corps; }
    public void setCorps(Map<String, RoleEntry> corps) { this.corps = corps; }
    public Map<String, RoleEntry> getNetworks() { return networks; }
    public void setNetworks(Map<String, RoleEntry> networks) { this.networks = networks; }
    // public Map<String, RoleEntry> getProjects() { return projects; }
    // public void setProjects(Map<String, RoleEntry> projects) { this.projects = projects; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    // --- Nested types ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupEntry {
        private String name;
        @JsonProperty("is_primary")
        private boolean isPrimary;
        private List<RoleItem> roles;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isIsPrimary() { return isPrimary; }
        public void setIsPrimary(boolean isPrimary) { this.isPrimary = isPrimary; }
        public List<RoleItem> getRoles() { return roles; }
        public void setRoles(List<RoleItem> roles) { this.roles = roles; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NamedRoleEntry {
        private String name;
        @JsonProperty("groupId")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Integer groupId;
        private List<RoleItem> roles;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getGroupId() { return groupId; }
        public void setGroupId(Integer groupId) { this.groupId = groupId; }
        public List<RoleItem> getRoles() { return roles; }
        public void setRoles(List<RoleItem> roles) { this.roles = roles; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RoleEntry {
        private List<RoleItem> roles;

        public List<RoleItem> getRoles() { return roles; }
        public void setRoles(List<RoleItem> roles) { this.roles = roles; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RoleItem {
        private int id;
        private String key;
        private String name;

        public RoleItem(int id, String key, String name) {
            this.id = id;
            this.key = key;
            this.name = name;
        }

        public int getId() { return id; }
        public String getKey() { return key; }
        public String getName() { return name; }
    }
}
