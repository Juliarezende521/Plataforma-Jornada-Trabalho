package io.github.juliarezende521.workjourney.account;

import jakarta.persistence.*;
import io.github.juliarezende521.workjourney.time.ReferenceTimeZones;
import java.util.UUID;

@Entity
@Table(name = "employee", schema = "dbo")
public class Employee {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true) private AppUser user;
    @org.hibernate.annotations.Nationalized
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "reference_time_zone_id", nullable = false, length = 100)
    private String referenceTimeZoneId;

    protected Employee() { }

    public Employee(AppUser user, String name, String timeZoneId) {
        if (user == null || name == null || name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Valid user, name and IANA zone are required");
        }
        String validZoneId = ReferenceTimeZones.requireValid(timeZoneId).getId();
        this.id = UUID.randomUUID();
        this.user = user;
        this.name = name.strip();
        this.referenceTimeZoneId = validZoneId;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getReferenceTimeZoneId() { return referenceTimeZoneId; }
}
