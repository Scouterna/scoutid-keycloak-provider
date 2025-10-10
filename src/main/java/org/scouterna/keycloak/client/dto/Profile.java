package org.scouterna.keycloak.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Profile {
    @JsonProperty("member_no")
    private int memberNo;
    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("last_name")
    private String lastName;
    @JsonProperty("email")
    private String email;
    @JsonProperty("dob")
    private String dob;
    @JsonProperty("sex")
    private String sex;
    @JsonProperty("addresses")
    private Map<String, Address> addresses;
    @JsonProperty("memberships")
    private Memberships memberships;

    // Getters and Setters
    public int getMemberNo() { return memberNo; }
    public void setMemberNo(int memberNo) { this.memberNo = memberNo; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public Map<String, Address> getAddresses() { return addresses; }
    public void setAddresses(Map<String, Address> addresses) { this.addresses = addresses; }
    public Memberships getMemberships() { return memberships; }
    public void setMemberships(Memberships memberships) { this.memberships = memberships; }
}