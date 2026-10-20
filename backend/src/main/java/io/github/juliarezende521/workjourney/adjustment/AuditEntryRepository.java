package io.github.juliarezende521.workjourney.adjustment;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> { }
