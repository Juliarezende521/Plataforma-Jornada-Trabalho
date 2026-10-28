package io.github.juliarezende521.workjourney.policy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/hr/work-policies")
public class CountryWorkPolicyController {
    private final CountryWorkPolicyService service;
    private final CountryWorkPolicyRequestReader reader;
    CountryWorkPolicyController(CountryWorkPolicyService service, CountryWorkPolicyRequestReader reader) {
        this.service = service; this.reader = reader;
    }
    @GetMapping("/current")
    CountryWorkPolicyService.View current(HttpServletRequest request) {
        exact(request, Set.of("countryCode", "on"));
        return service.current(required(request, "countryCode"), requiredDate(request, "on"));
    }
    @GetMapping
    CountryWorkPolicyService.Page history(HttpServletRequest request) {
        exact(request, Set.of("countryCode", "page", "size"));
        return service.history(required(request, "countryCode"), integer(request, "page", 0), integer(request, "size", 20));
    }
    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    CountryWorkPolicyService.View create(HttpServletRequest request) throws IOException {
        return service.create(reader.read(request));
    }
    private static String required(HttpServletRequest request, String name) {
        var value = request.getParameter(name); if (value == null || value.isBlank()) throw new InvalidCountryWorkPolicy();
        return value;
    }
    private static LocalDate requiredDate(HttpServletRequest request, String name) {
        var value = required(request, name);
        try { return LocalDate.parse(value); } catch (RuntimeException invalid) { throw new InvalidCountryWorkPolicy(); }
    }
    private static int integer(HttpServletRequest request, String name, int fallback) {
        try { var value = request.getParameter(name); return value == null ? fallback : Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new InvalidCountryWorkPolicy(); }
    }
    private static void exact(HttpServletRequest request, Set<String> allowed) {
        var parameters = request.getParameterMap();
        if (!allowed.containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(v -> v.length != 1))
            throw new InvalidCountryWorkPolicy();
    }
}
