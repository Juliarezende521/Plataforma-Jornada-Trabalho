package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import java.time.YearMonth;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/hr/monthly-closures")
public class MonthlyClosureReportController {
    private static final int MAX_ROWS = 10_000;
    private final HrAuthorization authorization;
    private final MonthlyClosureRepository closures;

    MonthlyClosureReportController(HrAuthorization authorization, MonthlyClosureRepository closures) {
        this.authorization = authorization;
        this.closures = closures;
    }

    @GetMapping(value = "/report", produces = "text/csv")
    @Transactional(readOnly = true)
    ResponseEntity<byte[]> report(HttpServletRequest request) {
        exact(request);
        var competence = competence(request.getParameter("competence"));
        authorization.require();
        var rows = closures.consolidated((short) competence.getYear(), (byte) competence.getMonthValue(),
                PageRequest.of(0, MAX_ROWS + 1));
        if (rows.size() > MAX_ROWS) throw new InvalidMonthlyClosure(413, "REPORT_TOO_LARGE");
        var filename = "fechamentos-" + competence + ".csv";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .cacheControl(CacheControl.noStore())
                .body(MonthlyClosureReportCsv.write(competence, rows));
    }

    private static void exact(HttpServletRequest request) {
        var parameters = request.getParameterMap();
        if (!Set.of("competence").equals(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw new InvalidMonthlyClosure();
        }
    }

    private static YearMonth competence(String value) {
        try {
            var parsed = YearMonth.parse(value);
            if (parsed.getYear() < 1 || parsed.getYear() > 9999) throw new InvalidMonthlyClosure();
            return parsed;
        } catch (RuntimeException invalid) {
            if (invalid instanceof InvalidMonthlyClosure closure) throw closure;
            throw new InvalidMonthlyClosure();
        }
    }
}
