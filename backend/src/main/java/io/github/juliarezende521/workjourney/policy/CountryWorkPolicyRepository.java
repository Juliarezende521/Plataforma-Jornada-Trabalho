package io.github.juliarezende521.workjourney.policy;

import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("!bootstrap")
interface CountryWorkPolicyRepository extends JpaRepository<CountryWorkPolicy, UUID> {
    Optional<CountryWorkPolicy> findFirstByCountryCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String countryCode, LocalDate on);
    Page<CountryWorkPolicy> findByCountryCode(String countryCode, Pageable pageable);
    boolean existsByCountryCodeAndEffectiveFrom(String countryCode, LocalDate effectiveFrom);
}
