package io.github.juliarezende521.workjourney.policy;

import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
class CountryWorkPolicyService {
    private final HrAuthorization authorization;
    private final CountryWorkPolicyRepository policies;
    private final AuditEntryRepository audits;
    private final Clock clock;
    CountryWorkPolicyService(HrAuthorization authorization, CountryWorkPolicyRepository policies,
                             AuditEntryRepository audits, Clock clock) {
        this.authorization = authorization; this.policies = policies; this.audits = audits; this.clock = clock;
    }
    @Transactional(readOnly = true)
    View current(String countryCode, LocalDate on) {
        authorization.require(); var country = CountryWorkPolicy.normalize(countryCode);
        if (on == null) throw new InvalidCountryWorkPolicy();
        return policies.findFirstByCountryCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(country, on)
                .map(View::from).orElse(null);
    }
    @Transactional(readOnly = true)
    Page history(String countryCode, int page, int size) {
        authorization.require(); var country = CountryWorkPolicy.normalize(countryCode); validatePage(page, size);
        var result = policies.findByCountryCode(country,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "effectiveFrom", "id")));
        return new Page(result.map(View::from).getContent(), page, size, result.getTotalElements());
    }
    @Transactional(timeout = 15)
    View create(CountryWorkPolicyRequestReader.Command command) {
        var actor = authorization.require(); var country = CountryWorkPolicy.normalize(command.countryCode());
        if (policies.existsByCountryCodeAndEffectiveFrom(country, command.effectiveFrom())) {
            throw new InvalidCountryWorkPolicy(409);
        }
        var now = UtcInstants.normalize(clock.instant());
        var policy = policies.save(new CountryWorkPolicy(country, command.effectiveFrom(),
                command.standardWeeklyMinutes(), command.maximumDailyMinutes(), command.minimumBreakMinutes(),
                command.overtimeToleranceMinutes(), actor.userId(), now));
        audits.save(new AuditEntry(actor.userId(), "COUNTRY_WORK_POLICY_CREATED", "COUNTRY_WORK_POLICY",
                policy.id(), now, "countryCode=" + country + ",effectiveFrom=" + command.effectiveFrom()));
        return View.from(policy);
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100)
            throw new InvalidCountryWorkPolicy();
    }
    record View(UUID id, String countryCode, LocalDate effectiveFrom, int standardWeeklyMinutes,
                int maximumDailyMinutes, int minimumBreakMinutes, int overtimeToleranceMinutes,
                Instant createdAtUtc) {
        static View from(CountryWorkPolicy policy) {
            return new View(policy.id(), policy.countryCode(), policy.effectiveFrom(), policy.standardWeeklyMinutes(),
                    policy.maximumDailyMinutes(), policy.minimumBreakMinutes(), policy.overtimeToleranceMinutes(),
                    policy.createdAt());
        }
    }
    record Page(List<View> items, int page, int size, long totalElements) { }
}
