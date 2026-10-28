package io.github.juliarezende521.workjourney.policy;

import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CountryWorkPolicyServiceTest {
    @Test void createsVersionWithAuditAndNormalizesCountry() {
        var authorization = mock(HrAuthorization.class); var policies = mock(CountryWorkPolicyRepository.class);
        var audits = mock(AuditEntryRepository.class); var actor = new CurrentEmployeeIdentity.Identity(UUID.randomUUID(), UUID.randomUUID());
        when(authorization.require()).thenReturn(actor); when(policies.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new CountryWorkPolicyService(authorization, policies, audits,
                Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));
        var command = new CountryWorkPolicyRequestReader.Command(" br ", LocalDate.parse("2026-11-01"),
                2400, 600, 60, 10);

        var result = service.create(command);

        assertEquals("BR", result.countryCode()); assertEquals(2400, result.standardWeeklyMinutes());
        verify(policies).existsByCountryCodeAndEffectiveFrom("BR", command.effectiveFrom());
        verify(audits).save(any(AuditEntry.class));
    }

    @Test void rejectsDuplicateVersionAndInvalidLimits() {
        var authorization = mock(HrAuthorization.class); var policies = mock(CountryWorkPolicyRepository.class);
        when(authorization.require()).thenReturn(new CurrentEmployeeIdentity.Identity(UUID.randomUUID(), UUID.randomUUID()));
        when(policies.existsByCountryCodeAndEffectiveFrom("BR", LocalDate.parse("2026-11-01"))).thenReturn(true);
        var service = new CountryWorkPolicyService(authorization, policies, mock(AuditEntryRepository.class), Clock.systemUTC());
        var duplicate = new CountryWorkPolicyRequestReader.Command("BR", LocalDate.parse("2026-11-01"), 2400, 600, 60, 10);
        assertEquals(409, assertThrows(InvalidCountryWorkPolicy.class, () -> service.create(duplicate)).status());
        assertThrows(InvalidCountryWorkPolicy.class, () -> new CountryWorkPolicy("BR", LocalDate.now(), 2400,
                1441, 60, 10, UUID.randomUUID(), Instant.now()));
    }
}
