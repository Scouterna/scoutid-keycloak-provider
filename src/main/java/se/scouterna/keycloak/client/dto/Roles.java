package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; 
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Roles {

    private Map<String, Map<String, String>> organisation;
    private Map<String, Map<String, String>> region;
    private Map<String, Map<String, String>> project;
    private Map<String, Map<String, String>> network;
    private Map<String, Map<String, String>> corps;
    private Map<String, Map<String, String>> district;
    private Map<String, Map<String, String>> group;
    private Map<String, Map<String, String>> troop;
    private Map<String, Map<String, String>> patrol;

    // --- Constructor ---
    public Roles() {
    }

    // --- Required Getter Methods ---

    public Map<String, Map<String, String>> getOrganisation() {
        return organisation;
    }

    public Map<String, Map<String, String>> getRegion() {
        return region;
    }

    public Map<String, Map<String, String>> getProject() {
        return project;
    }

    public Map<String, Map<String, String>> getNetwork() {
        return network;
    }

    public Map<String, Map<String, String>> getCorps() {
        return corps;
    }

    public Map<String, Map<String, String>> getDistrict() {
        return district;
    }

    public Map<String, Map<String, String>> getGroup() {
        return group;
    }

    public Map<String, Map<String, String>> getTroop() {
        return troop;
    }

    public Map<String, Map<String, String>> getPatrol() {
        return patrol;
    }

    // --- Setter Methods for Testing ---

    public void setOrganisation(Map<String, Map<String, String>> organisation) {
        this.organisation = organisation;
    }

    public void setRegion(Map<String, Map<String, String>> region) {
        this.region = region;
    }

    public void setProject(Map<String, Map<String, String>> project) {
        this.project = project;
    }

    public void setNetwork(Map<String, Map<String, String>> network) {
        this.network = network;
    }

    public void setCorps(Map<String, Map<String, String>> corps) {
        this.corps = corps;
    }

    public void setDistrict(Map<String, Map<String, String>> district) {
        this.district = district;
    }

    public void setGroup(Map<String, Map<String, String>> group) {
        this.group = group;
    }

    public void setTroop(Map<String, Map<String, String>> troop) {
        this.troop = troop;
    }

    public void setPatrol(Map<String, Map<String, String>> patrol) {
        this.patrol = patrol;
    }
}