package se.scouterna.keycloak.client.dto;

import org.junit.jupiter.api.Test;
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
}