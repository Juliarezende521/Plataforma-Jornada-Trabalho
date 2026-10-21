package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

record SummaryRequestFilter(LocalDate from,LocalDate to) {
    static SummaryRequestFilter read(HttpServletRequest request) {
        var parameters=request.getParameterMap();
        if(!parameters.keySet().equals(Set.of("from","to"))
                ||parameters.values().stream().anyMatch(values -> values.length!=1))throw new JourneyQueries.InvalidFilter();
        try {
            var result=new SummaryRequestFilter(LocalDate.parse(request.getParameter("from")),LocalDate.parse(request.getParameter("to")));
            JourneyQueries.validateSummary(result.from,result.to);
            return result;
        } catch(DateTimeParseException|NullPointerException invalid){throw new JourneyQueries.InvalidFilter();}
    }
}
