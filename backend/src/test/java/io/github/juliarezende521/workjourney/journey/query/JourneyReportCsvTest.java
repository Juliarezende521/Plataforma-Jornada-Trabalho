package io.github.juliarezende521.workjourney.journey.query;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JourneyReportCsvTest {
    @Test
    void writesExcelFriendlyCsvAndNeutralizesFormulaCells() {
        var summary=new JourneyQueries.Summary(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31"),
                "PT160H","PT160H",0L,3600L,true,31,31,20,20,0,1,20,Instant.parse("2026-11-01T12:00:00Z"));
        var report=new HrJourneyQueries.Report(new HrJourneyQueries.Person(UUID.randomUUID(),
                "=IMPORTDATA(\"https://example.invalid\")","America/Sao_Paulo"),summary);

        String csv=new String(JourneyReportCsv.write(report),StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF\"Colaborador\";"));
        assertTrue(csv.contains("\"'=IMPORTDATA(\"\"https://example.invalid\"\")\""));
        assertTrue(csv.contains("\"PT160H\";\"PT160H\";\"0\""));
        assertTrue(csv.endsWith("\r\n"));
    }
}
