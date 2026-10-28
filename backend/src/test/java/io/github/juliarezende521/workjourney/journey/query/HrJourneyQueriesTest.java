package io.github.juliarezende521.workjourney.journey.query;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrJourneyQueriesTest {
    @Test void closedStateUsesEachEmployeeReferenceDateNearMidnight() {
        var identity = mock(CurrentEmployeeIdentity.class);
        var users = mock(AppUserRepository.class);
        var employees = mock(EmployeeRepository.class);
        var store = mock(JpaWorkSessionStore.class);
        var actor = new CurrentEmployeeIdentity.Identity(UUID.randomUUID(), UUID.randomUUID());
        when(identity.require()).thenReturn(actor);
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())).thenReturn(true);
        var brazil = mock(Employee.class); var europe = mock(Employee.class);
        when(brazil.getId()).thenReturn(UUID.randomUUID()); when(europe.getId()).thenReturn(UUID.randomUUID());
        when(brazil.getName()).thenReturn("Brasil"); when(europe.getName()).thenReturn("Europa");
        when(brazil.getReferenceTimeZoneId()).thenReturn("America/Sao_Paulo");
        when(europe.getReferenceTimeZoneId()).thenReturn("Europe/Amsterdam");
        when(employees.findOwnedActive(actor.employeeId(), actor.userId())).thenReturn(Optional.of(brazil));
        when(employees.findActive(any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(brazil, europe)));
        var date = java.time.LocalDate.parse("2026-10-14");
        var brazilId = brazil.getId(); var europeId = europe.getId();
        when(store.latestCheckpoints(List.of(brazilId, europeId))).thenReturn(List.of(
                new JpaWorkSessionStore.LatestCheckpoint(brazilId, date, io.github.juliarezende521.workjourney.journey.WorkState.CLOSED),
                new JpaWorkSessionStore.LatestCheckpoint(europeId, date, io.github.juliarezende521.workjourney.journey.WorkState.CLOSED)));
        var clock = Clock.fixed(java.time.Instant.parse("2026-10-15T02:00:00Z"), java.time.ZoneOffset.UTC);
        var queries = new HrJourneyQueries(new HrAuthorization(identity, users, employees), employees, store,
                mock(io.github.juliarezende521.workjourney.adjustment.EffectiveTimelineService.class),
                mock(io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan.class), clock);
        var overview = queries.overview(0, 20, null);
        assertEquals("CLOSED", overview.items().get(0).state());
        assertEquals("NOT_STARTED", overview.items().get(1).state());
        assertNull(overview.items().get(1).workDate());
        assertThrows(JourneyQueries.InvalidFilter.class, () -> queries.overview(-1, 20, null));
        assertThrows(JourneyQueries.InvalidFilter.class, () -> queries.overview(0, 101, null));
        when(employees.findActiveByNamePrefix(eq("Ju~%lia~_"), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(brazil)));
        var filtered = queries.employees(0, 20, "  Ju%lia_  ");
        assertEquals(1, filtered.items().size());
        verify(employees).findActiveByNamePrefix(eq("Ju~%lia~_"), any());
        assertThrows(JourneyQueries.InvalidFilter.class,
                () -> queries.employees(0, 20, "a".repeat(81)));
    }

    @Test void databasePermissionAndOwnedActiveIdentityAreRequiredBeforeAnyDirectoryOrJourneyRead() {
        var identity = mock(CurrentEmployeeIdentity.class);
        var users = mock(AppUserRepository.class);
        var employees = mock(EmployeeRepository.class);
        var store = mock(JpaWorkSessionStore.class);
        var actor = new CurrentEmployeeIdentity.Identity(UUID.randomUUID(), UUID.randomUUID());
        when(identity.require()).thenReturn(actor);
        var queries = new HrJourneyQueries(new HrAuthorization(identity, users, employees), employees, store,
                mock(io.github.juliarezende521.workjourney.adjustment.EffectiveTimelineService.class),
                mock(io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan.class), Clock.systemUTC());
        assertThrows(AccessDeniedException.class, () -> queries.employees(0, 20, null));
        assertThrows(AccessDeniedException.class, () -> queries.overview(0, 20, null));
        assertThrows(AccessDeniedException.class, () -> queries.detail(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(store);
        verifyNoInteractions(employees);
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())).thenReturn(true);
        when(employees.findOwnedActive(actor.employeeId(), actor.userId())).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> queries.employees(0, 20, null));
        verifyNoInteractions(store);
        verify(employees, never()).findActive(any());
    }
}
