package io.github.juliarezende521.workjourney.account;

import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "app_user", schema = "dbo")
public class AppUser {
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    @Id private UUID id;
    @Column(nullable = false, length = 254, unique = true) private String email;
    @Column(name = "password_hash", nullable = false, length = 60) private String passwordHash;
    @Column(nullable = false) private boolean active;
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY) private Employee employee;

    protected AppUser() { }

    public AppUser(String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        if (this.email.isBlank() || this.email.length() > 254 || !this.email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (passwordHash == null || !BCRYPT_HASH.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("A BCrypt hash is required");
        }
        this.passwordHash = passwordHash;
        this.active = true;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Employee getEmployee() { return employee; }
}
