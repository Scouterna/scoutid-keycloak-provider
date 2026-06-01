package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupMembership {
    @JsonProperty("is_primary")
    private boolean isPrimary;
    @JsonProperty("group")
    private Group group;
    @JsonProperty("troop")
    private Troop troop;
    @JsonProperty("patrol")
    private Patrol patrol;
    @JsonProperty("roles")
    private Map<String, String> roles;

    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }
    public Troop getTroop() { return troop; }
    public void setTroop(Troop troop) { this.troop = troop; }
    public Patrol getPatrol() { return patrol; }
    public void setPatrol(Patrol patrol) { this.patrol = patrol; }
    public Map<String, String> getRoles() { return roles; }
    public void setRoles(Map<String, String> roles) { this.roles = roles; }
}