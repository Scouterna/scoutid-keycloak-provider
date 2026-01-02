package se.scouterna.keycloak.client.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ProfileTest {

    @Test
    public void testGetFirstLast_basicNames() {
        Profile profile = new Profile();
        profile.setFirstName("John");
        profile.setLastName("Doe");
        
        assertEquals("john.doe", profile.getFirstLast());
    }

    @Test
    public void testGetFirstLast_withSpaces() {
        Profile profile = new Profile();
        profile.setFirstName("Anna Maria");
        profile.setLastName("von Berg");
        
        assertEquals("anna.maria.von.berg", profile.getFirstLast());
    }

    @Test
    public void testGetFirstLast_withDiacritics() {
        Profile profile = new Profile();
        profile.setFirstName("Åsa");
        profile.setLastName("Lindström");
        
        assertEquals("asa.lindstrom", profile.getFirstLast());
    }
    
    @Test
    public void testGetFirstLast_withExtensiveDiacritics() {
        Profile profile = new Profile();
        profile.setFirstName("Émile");
        profile.setLastName("Müller-Schön");
        
        assertEquals("emile.muller-schon", profile.getFirstLast());
    }
    
    @Test
    public void testGetFirstLast_withSlavicCharacters() {
        Profile profile = new Profile();
        profile.setFirstName("Paweł");
        profile.setLastName("Kowalski");
        
        assertEquals("pawel.kowalski", profile.getFirstLast());
    }
    
    @Test
    public void testGetFirstLast_withCzechCharacters() {
        Profile profile = new Profile();
        profile.setFirstName("František");
        profile.setLastName("Novák");
        
        assertEquals("frantisek.novak", profile.getFirstLast());
    }

    @Test
    public void testGetFirstLast_withSpecialCharacters() {
        Profile profile = new Profile();
        profile.setFirstName("Jean-Pierre");
        profile.setLastName("O'Connor");
        
        assertEquals("jean-pierre.oconnor", profile.getFirstLast());
    }

    @Test
    public void testGetFirstLast_nullNames() {
        Profile profile = new Profile();
        profile.setFirstName(null);
        profile.setLastName("Doe");
        
        assertNull(profile.getFirstLast());
        
        profile.setFirstName("John");
        profile.setLastName(null);
        
        assertNull(profile.getFirstLast());
    }

    @Test
    public void testGetFirstLast_emptyNames() {
        Profile profile = new Profile();
        profile.setFirstName("");
        profile.setLastName("Doe");
        
        assertEquals(".doe", profile.getFirstLast());
    }
    
    @Test
    public void testGetFirstLast_edgeCases() {
        Profile profile = new Profile();
        profile.setFirstName("Anna  Maria"); // Multiple spaces
        profile.setLastName("O'Connor"); // Apostrophe
        
        assertEquals("anna.maria.oconnor", profile.getFirstLast());
        
        profile.setFirstName(".John."); // Leading/trailing dots
        profile.setLastName("Smith");
        
        assertEquals("john.smith", profile.getFirstLast());
    }
}