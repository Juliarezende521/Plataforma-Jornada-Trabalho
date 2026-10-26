package io.github.juliarezende521.workjourney.journey.query;

import java.nio.charset.StandardCharsets;

public final class JourneyReportCsv {
    private static final String[] HEADERS = {"Colaborador", "Fuso de referência", "Data inicial", "Data final",
            "Tempo efetivo", "Tempo planejado", "Saldo em segundos", "Tolerância em segundos",
            "Dentro da tolerância", "Dias planejados", "Dias do período", "Jornadas", "Encerradas",
            "Pendentes", "Com ajuste", "Dias trabalhados", "Consultado em UTC"};

    private JourneyReportCsv() { }

    public static byte[] write(HrJourneyQueries.Report report) {
        var summary=report.summary();
        String[] values={report.employee().name(),report.employee().referenceTimeZoneId(),summary.from().toString(),
                summary.to().toString(),summary.totalWorkDuration(),summary.plannedWorkDuration(),
                nullable(summary.balanceSeconds()),nullable(summary.toleranceSeconds()),nullable(summary.withinTolerance()),
                Integer.toString(summary.scheduledDays()),Integer.toString(summary.periodDays()),
                Long.toString(summary.totalSessions()),Long.toString(summary.closedSessions()),
                Long.toString(summary.openSessions()),Long.toString(summary.adjustedSessions()),
                Integer.toString(summary.workedDays()),summary.observedAtUtc().toString()};
        String csv="\uFEFF"+row(HEADERS)+"\r\n"+row(values)+"\r\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private static String nullable(Object value) { return value==null ? "" : value.toString(); }

    private static String row(String[] values) {
        return java.util.Arrays.stream(values).map(JourneyReportCsv::cell)
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private static String cell(String value) {
        String safe=value==null ? "" : value;
        String trimmed=safe.stripLeading();
        if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) safe="'"+safe;
        return "\""+safe.replace("\"","\"\"")+"\"";
    }
}
