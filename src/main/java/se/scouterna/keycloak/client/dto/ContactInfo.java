package se.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactInfo {
    @JsonProperty("addresses")
    private Map<String, Address> addresses;

    // Getters and Setters
    public Map<String, Address> getAddresses() { return addresses; }
    public void setAddresses(Map<String, Address> addresses) { this.addresses = addresses; }
}