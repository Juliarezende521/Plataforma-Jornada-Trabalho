package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries.InvalidFilter;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {
    @Test void hrReadsAPaginatedAuditTrailWithActorEmailInTwoQueries() {
        var identity = mock(CurrentEmployeeIdentity.class);
        var employees = mock(EmployeeRepository.class);
        var users = mock(AppUserRepository.class);
        var audits = mock(AuditEntryRepository.class);
        var account = new AppUser("hr@example.invalid", "$2a$12$" + "A".repeat(53), true);
        var employee = new Employee(account, "RH", "America/Sao_Paulo");
        when(identity.require()).thenReturn(new CurrentEmployeeIdentity.Identity(account.getId(), employee.getId()));
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(account.getId())).thenReturn(true);
        when(employees.findOwnedActive(employee.getId(), account.getId())).thenReturn(Optional.of(employee));
        var entry = new AuditEntry(account.getId(), "WORK_SCHEDULE_CREATED", "WORK_SCHEDULE",
                UUID.randomUUID(), Instant.EPOCH, "effectiveFrom=2026-11-01");
        when(audits.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));
        when(users.findAllById(any())).thenReturn(List.of(account));
        var service = new AuditQueryService(new HrAuthorization(identity, users, employees), users, audits);

        var result = service.history(0, 20);

        assertEquals(1, result.totalElements());
        assertEquals("hr@example.invalid", result.items().get(0).actorEmail());
        assertEquals("WORK_SCHEDULE_CREATED", result.items().get(0).action());
        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(audits).findAll(pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals("recordedAtUtc: DESC,id: DESC", pageable.getValue().getSort().toString());
        verify(users).findAllById(any());
    }

    @Test void rejectsUnboundedPaginationBeforeQueryingAuditEntries() {
        var identity = mock(CurrentEmployeeIdentity.class);
        var employees = mock(EmployeeRepository.class);
        var users = mock(AppUserRepository.class);
        var audits = mock(AuditEntryRepository.class);
        var account = new AppUser("hr@example.invalid", "$2a$12$" + "A".repeat(53), true);
        var employee = new Employee(account, "RH", "UTC");
        when(identity.require()).thenReturn(new CurrentEmployeeIdentity.Identity(account.getId(), employee.getId()));
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(account.getId())).thenReturn(true);
        when(employees.findOwnedActive(employee.getId(), account.getId())).thenReturn(Optional.of(employee));
        var service = new AuditQueryService(new HrAuthorization(identity, users, employees), users, audits);
        assertThrows(InvalidFilter.class, () -> service.history(0, 101));
        verifyNoInteractions(audits);
    }

    @Test void revalidatesHrPermissionBeforeReadingAuditEntries() {
        var identity = mock(CurrentEmployeeIdentity.class);
        var employees = mock(EmployeeRepository.class);
        var users = mock(AppUserRepository.class);
        var audits = mock(AuditEntryRepository.class);
        var userId = UUID.randomUUID();
        when(identity.require()).thenReturn(new CurrentEmployeeIdentity.Identity(userId, UUID.randomUUID()));
        var service = new AuditQueryService(new HrAuthorization(identity, users, employees), users, audits);
        assertThrows(AccessDeniedException.class, () -> service.history(0, 20));
        verifyNoInteractions(audits);
    }
}
