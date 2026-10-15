package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;

record HistoryRequestFilter(LocalDate from, LocalDate to, int page, int size) {
    static HistoryRequestFilter read(HttpServletRequest request) {
        var parameters = request.getParameterMap();
        if (!Set.of("from", "to", "page", "size").containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) throw new JourneyQueries.InvalidFilter();
        try {
            var from = LocalDate.parse(request.getParameter("from"));
            var to = LocalDate.parse(request.getParameter("to"));
            int page = Integer.parseInt(request.getParameter("page") == null ? "0" : request.getParameter("page"));
            int size = Integer.parseInt(request.getParameter("size") == null ? "20" : request.getParameter("size"));
            JourneyQueries.validate(from, to, page, size);
            return new HistoryRequestFilter(from, to, page, size);
        } catch (java.time.DateTimeException | NumberFormatException | NullPointerException invalid) {
            throw new JourneyQueries.InvalidFilter();
        }
    }
}
