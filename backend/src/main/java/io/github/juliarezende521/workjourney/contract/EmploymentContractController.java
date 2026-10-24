package io.github.juliarezende521.workjourney.contract;

import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!bootstrap")
public class EmploymentContractController {
    private final EmploymentContractService service;
    private final ContractRequestReader reader;
    private final MarkingRequestReader uuids;

    EmploymentContractController(EmploymentContractService service, ContractRequestReader reader,
                                 MarkingRequestReader uuids) {
        this.service = service;
        this.reader = reader;
        this.uuids = uuids;
    }

    @GetMapping("/api/hr/employees/{employeeId}/employment-contract")
    EmploymentContractService.View current(@PathVariable String employeeId, HttpServletRequest request) {
        validateExactParameters(request, Set.of("on"));
        return service.currentForHr(uuids.uuid(employeeId), optionalDate(request, "on"));
    }

    @GetMapping("/api/hr/employees/{employeeId}/employment-contracts")
    EmploymentContractService.Page history(@PathVariable String employeeId, HttpServletRequest request) {
        validateExactParameters(request, Set.of("page", "size"));
        return service.history(uuids.uuid(employeeId), integer(request, "page", 0), integer(request, "size", 20));
    }

    @PostMapping(value = "/api/hr/employees/{employeeId}/employment-contracts", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    EmploymentContractService.View create(@PathVariable String employeeId, HttpServletRequest request)
            throws IOException {
        var command = reader.read(request);
        return service.create(uuids.uuid(employeeId), command.effectiveFrom(), command.countryCode(),
                command.type(), command.weeklyMinutes());
    }

    private static LocalDate optionalDate(HttpServletRequest request, String name) {
        var value = request.getParameter(name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException invalid) {
            throw new InvalidContract();
        }
    }

    private static void validateExactParameters(HttpServletRequest request, Set<String> allowed) {
        var parameters = request.getParameterMap();
        boolean containsUnknownParameter = !allowed.containsAll(parameters.keySet());
        boolean containsRepeatedParameter = parameters.values().stream().anyMatch(values -> values.length != 1);
        if (containsUnknownParameter || containsRepeatedParameter) {
            throw new InvalidContract();
        }
    }

    private static int integer(HttpServletRequest request, String name, int fallback) {
        var value = request.getParameter(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidContract();
        }
    }
}
