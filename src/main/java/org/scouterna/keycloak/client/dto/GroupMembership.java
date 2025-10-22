package org.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupMembership {
    @JsonProperty("is_primary")
    private boolean isPrimary;
    @JsonProperty("group")
    private Group group;

    // Getters and Setters
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }
}