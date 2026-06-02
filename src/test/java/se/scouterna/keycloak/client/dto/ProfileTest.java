package se.scouterna.keycloak.client.dto;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileTest {

    @Test
    void testGetFirstLastWithDiacritics() {
        Profile profile = new Profile();
        profile.setFirstName("Müller");
        profile.setLastName("Øresund");
        
        assertEquals("muller.oresund", profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithVietnameseCharacters() {
        Profile profile = new Profile();
        profile.setFirstName("Nguyễn");
        profile.setLastName("Phạm");
        
        assertEquals("nguyen.pham", profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithTurkishCharacters() {
        Profile profile = new Profile();
        profile.setFirstName("Çağlar");
        profile.setLastName("Şahin");
        
        assertEquals("caglar.sahin", profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithSpacesAndSpecialChars() {
        Profile profile = new Profile();
        profile.setFirstName("Jean-Pierre");
        profile.setLastName("Van Der Berg");
        
        assertEquals("jean-pierre.van.der.berg", profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithNullValues() {
        Profile profile = new Profile();
        profile.setFirstName(null);
        profile.setLastName("Doe");
        
        assertNull(profile.getFirstLast());
        
        profile.setFirstName("John");
        profile.setLastName(null);
        
        assertNull(profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithEmptyValues() {
        Profile profile = new Profile();
        profile.setFirstName("");
        profile.setLastName("Doe");
        
        assertEquals(".doe", profile.getFirstLast());
        
        // Test whitespace-only names
        profile.setFirstName("   ");
        assertEquals(".doe", profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithDotAndHyphenEdgeCases() {
        Profile profile = new Profile();
        
        // Test hyphen-dot patterns
        profile.setFirstName("Jean.-Pierre");
        profile.setLastName("Test");
        assertEquals("jean-pierre.test", profile.getFirstLast());
    }

    @Test
    void testGetFirstLastWithSpecialCharacterFiltering() {
        Profile profile = new Profile();
        profile.setLastName("Test");
        
        // Test that non-alphanumeric chars (except . and -) are removed
        profile.setFirstName("John@#$%");
        assertEquals("john.test", profile.getFirstLast());
        
        // Test that numbers are preserved
        profile.setFirstName("John2");
        assertEquals("john2.test", profile.getFirstLast());
    }

    // --- contact_info accessors ---

    private Profile profileWithContactInfo(String key, String value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", key);
        entry.put("value", value);
        Map<String, Map<String, Object>> contactInfo = new LinkedHashMap<>();
        contactInfo.put("1", entry);
        Profile profile = new Profile();
        profile.setContactInfo(contactInfo);
        return profile;
    }

    @Test
    void getScouternaEmail_returnsMatchingValue() {
        assertEquals("teo@scouterna.se",
            profileWithContactInfo("scouterna-email", "teo@scouterna.se").getScouternaEmail());
    }

    @Test
    void getMobilePhone_returnsMatchingValue() {
        assertEquals("+46701234567",
            profileWithContactInfo("mobile_phone", "+46701234567").getMobilePhone());
    }

    @Test
    void getAltEmail_returnsMatchingValue() {
        assertEquals("alt@example.com",
            profileWithContactInfo("alt_email", "alt@example.com").getAltEmail());
    }

    @Test
    void contactInfoAccessors_returnNullWhenKeyAbsent() {
        Profile profile = profileWithContactInfo("other_key", "irrelevant");
        assertNull(profile.getScouternaEmail());
        assertNull(profile.getMobilePhone());
        assertNull(profile.getAltEmail());
    }

    @Test
    void contactInfoAccessors_returnNullWhenContactInfoNull() {
        Profile profile = new Profile();
        assertNull(profile.getScouternaEmail());
        assertNull(profile.getMobilePhone());
        assertNull(profile.getAltEmail());
    }
}