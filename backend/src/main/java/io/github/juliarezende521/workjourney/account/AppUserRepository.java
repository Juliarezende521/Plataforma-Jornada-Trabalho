package io.github.juliarezende521.workjourney.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    boolean existsByIdAndActiveTrueAndHrReaderTrue(UUID id);

    @EntityGraph(attributePaths = "employee")
    Optional<AppUser> findByEmail(String email);
}
