package io.github.juliarezende521.workjourney.closing;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class MonthlyClosureReportCsv {
    private static final String[] HEADERS = {"Colaborador", "Fuso de referência", "Competência",
            "Tempo trabalhado", "Tempo planejado", "Saldo em segundos", "Tolerância em segundos",
            "Dentro da tolerância", "Jornadas", "Com ajuste", "Dias trabalhados", "Fechado em UTC"};

    private MonthlyClosureReportCsv() { }

    static byte[] write(YearMonth competence, List<MonthlyClosureRepository.ConsolidatedRow> rows) {
        var content = Stream.concat(Stream.of(row(HEADERS)), rows.stream().map(item -> row(new String[]{
                item.getEmployeeName(), item.getReferenceTimeZoneId(), competence.toString(),
                Duration.ofSeconds(item.getTotalWorkSeconds()).toString(),
                Duration.ofSeconds(item.getPlannedWorkSeconds()).toString(),
                Long.toString(item.getBalanceSeconds()), Long.toString(item.getToleranceSeconds()),
                Boolean.toString(item.getWithinTolerance()), Long.toString(item.getTotalSessions()),
                Long.toString(item.getAdjustedSessions()), Integer.toString(item.getWorkedDays()),
                item.getClosedAtUtc().toInstant().toString()
        }))).collect(Collectors.joining("\r\n", "\uFEFF", "\r\n"));
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String[] values) {
        return java.util.Arrays.stream(values).map(MonthlyClosureReportCsv::cell)
                .collect(Collectors.joining(";"));
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        String trimmed = safe.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
