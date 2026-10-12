package io.github.juliarezende521.workjourney.account;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class AccountModelTest {
    @Test
    void normalizesEmailAndRejectsPlaintextPassword() {
        String hash = new BCryptPasswordEncoder(4).encode("test-password-only");
        var user = new AppUser(" BRASIL@EXAMPLE.INVALID ", hash);
        assertEquals("brasil@example.invalid", user.getEmail());
        assertThrows(IllegalArgumentException.class, () -> new AppUser("a@example.invalid", "plaintext"));
    }
    @Test
    void rejectsNullHashAndNullReferenceZoneWithValidationErrors() {
        assertThrows(IllegalArgumentException.class, () -> new AppUser("a@example.invalid", null));
        var user = new AppUser("a@example.invalid", new BCryptPasswordEncoder(4).encode("test-password-only"));
        assertThrows(IllegalArgumentException.class, () -> new Employee(user, "Brasil", null));
    }

    @Test
    void rejectsOffsetInsteadOfIanaReferenceZone() {
        var user = new AppUser("brasil@example.invalid", new BCryptPasswordEncoder(4).encode("test-password-only"));
        assertThrows(IllegalArgumentException.class, () -> new Employee(user, "Brasil", "+03:00"));
        assertDoesNotThrow(() -> new Employee(user, "Brasil", "America/Sao_Paulo"));
    }
}
