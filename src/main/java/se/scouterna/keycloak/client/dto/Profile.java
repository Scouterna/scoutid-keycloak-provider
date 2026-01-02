package se.scouterna.keycloak.client.dto;

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
    @JsonProperty("contact_info")
    private Map<String, Map<String, Object>> contactInfo;

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
    public Map<String, Map<String, Object>> getContactInfo() { return contactInfo; }
    public void setContactInfo(Map<String, Map<String, Object>> contactInfo) { this.contactInfo = contactInfo; }
    
    /**
     * Generic helper to extract contact info by key
     */
    private String getContactInfoByKey(String key) {
        if (contactInfo == null) return null;
        
        return contactInfo.values().stream()
            .filter(contact -> key.equals(contact.get("key")))
            .map(contact -> (String) contact.get("value"))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Extracts the Scouterna email from contact_info if it exists
     */
    public String getScouternaEmail() {
        return getContactInfoByKey("scouterna-email");
    }
    
    /**
     * Generates a fake local email address for systems that require email mapping
     * Format: memberno@scoutid.local (e.g., 3169207@scoutid.local)
     */
    public String getScoutIdLocalEmail() {
        return memberNo + "@scoutid.local";
    }
    
    /**
     * Generates firstlast attribute by formatting first and last names for email-like usage
     * Format: firstname.lastname (e.g., "john.doe")
     */
    public String getFirstLast() {
        if (firstName == null || lastName == null) return null;
        return formatNameForEmail(firstName) + "." + formatNameForEmail(lastName);
    }
    
    private static String formatNameForEmail(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        
        String formatted = name.trim();
        formatted = formatted.replaceAll("\\s+", "."); // Replace spaces with dots
        formatted = formatted.replace(".-", "-").replace("-.", "-"); // Replace -. and -. with -
        formatted = removeDiacritics(formatted); // Remove diacritics
        formatted = formatted.replaceAll("[^0-9a-zA-Z.\\-]", ""); // Remove non-alphanumeric except . and -
        formatted = formatted.replaceAll("\\.{2,}", "."); // Multiple dots → single dot
        formatted = formatted.replaceAll("^\\.|\\.$", ""); // Remove leading/trailing dots
        return formatted.toLowerCase();
    }
    
    private static String removeDiacritics(String str) {
        if (str == null) return null;
        
        return str
            .replaceAll("[àáâãäåāăąǎ]", "a")
            .replaceAll("[ÀÁÂÃÄÅĀĂĄǍ]", "A")
            .replaceAll("[èéêëēĕėęěě]", "e")
            .replaceAll("[ÈÉÊËĒĔĖĘĚĚ]", "E")
            .replaceAll("[ìíîïĩīĭįıǐ]", "i")
            .replaceAll("[ÌÍÎÏĨĪĬĮIǏ]", "I")
            .replaceAll("[òóôõöøōŏőǒ]", "o")
            .replaceAll("[ÒÓÔÕÖØŌŎŐǑ]", "O")
            .replaceAll("[ùúûüũūŭůűųǔ]", "u")
            .replaceAll("[ÙÚÛÜŨŪŬŮŰŲǓ]", "U")
            .replaceAll("[ýÿŷ]", "y")
            .replaceAll("[ÝŸŶ]", "Y")
            .replaceAll("[ñńňņ]", "n")
            .replaceAll("[ÑŃŇŅ]", "N")
            .replaceAll("[çćčċ]", "c")
            .replaceAll("[ÇĆČĊ]", "C")
            .replaceAll("[řŕ]", "r")
            .replaceAll("[ŘŔ]", "R")
            .replaceAll("[ťţ]", "t")
            .replaceAll("[ŤŢ]", "T")
            .replaceAll("[ďđ]", "d")
            .replaceAll("[ĎĐ]", "D")
            .replaceAll("[ß]", "ss")
            .replaceAll("[æ]", "ae")
            .replaceAll("[Æ]", "AE")
            .replaceAll("[ð]", "d")
            .replaceAll("[Ð]", "D")
            .replaceAll("[þ]", "th")
            .replaceAll("[Þ]", "TH")
            .replaceAll("[ł]", "l")
            .replaceAll("[Ł]", "L")
            .replaceAll("[śšşș]", "s")
            .replaceAll("[ŚŠŞȘ]", "S")
            .replaceAll("[žźżž]", "z")
            .replaceAll("[ŽŹŻŽ]", "Z")
            .replaceAll("[ĳ]", "ij")
            .replaceAll("[Ĳ]", "IJ");
    }
}