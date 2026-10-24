package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.account.AppUserRepository;
import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries.InvalidFilter;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
class AuditQueryService {
    private final HrAuthorization hrAuthorization;
    private final AppUserRepository users;
    private final AuditEntryRepository audits;

    AuditQueryService(HrAuthorization hrAuthorization, AppUserRepository users, AuditEntryRepository audits) {
        this.hrAuthorization = hrAuthorization;
        this.users = users;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    Page history(int page, int size) {
        hrAuthorization.require();
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100) throw new InvalidFilter();
        var result = audits.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "recordedAtUtc", "id")));
        var actorIds = result.stream().map(AuditEntry::actorUserId).collect(java.util.stream.Collectors.toSet());
        var actorEmails = new HashMap<UUID, String>();
        users.findAllById(actorIds).forEach(user -> actorEmails.put(user.getId(), user.getEmail()));
        var items = result.stream().map(entry -> View.from(entry, actorEmails.get(entry.actorUserId()))).toList();
        return new Page(items, page, size, result.getTotalElements());
    }

    record View(UUID id, UUID actorUserId, String actorEmail, String action, String entityType,
                UUID entityId, Instant recordedAtUtc, String details) {
        static View from(AuditEntry entry, String email) {
            return new View(entry.id(), entry.actorUserId(), email, entry.action(), entry.entityType(),
                    entry.entityId(), entry.recordedAt(), entry.details());
        }
    }
    record Page(List<View> items, int page, int size, long totalElements) { }
}
