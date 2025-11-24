package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Memberships {
    @JsonProperty("group")
    private Map<String, GroupMembership> group;
    
    // Getter and Setter
    public Map<String, GroupMembership> getGroup() { return group; }
    public void setGroup(Map<String, GroupMembership> group) { this.group = group; }
}