package io.github.juliarezende521.workjourney.journey.query;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrJourneyQueriesTest {
    @Test void databasePermissionAndOwnedActiveIdentityAreRequiredBeforeAnyDirectoryOrJourneyRead() {
        var identity = mock(CurrentEmployeeIdentity.class);
        var users = mock(AppUserRepository.class);
        var employees = mock(EmployeeRepository.class);
        var store = mock(JpaWorkSessionStore.class);
        var actor = new CurrentEmployeeIdentity.Identity(UUID.randomUUID(), UUID.randomUUID());
        when(identity.require()).thenReturn(actor);
        var queries = new HrJourneyQueries(identity, users, employees, store, Clock.systemUTC());
        assertThrows(AccessDeniedException.class, () -> queries.employees(0, 20));
        assertThrows(AccessDeniedException.class, () -> queries.detail(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(store);
        verifyNoInteractions(employees);
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())).thenReturn(true);
        when(employees.findOwnedActive(actor.employeeId(), actor.userId())).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> queries.employees(0, 20));
        verifyNoInteractions(store);
        verify(employees, never()).findActive(any());
    }
}
