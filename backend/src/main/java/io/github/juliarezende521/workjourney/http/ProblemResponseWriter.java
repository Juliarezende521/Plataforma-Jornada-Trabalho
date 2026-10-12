package io.github.juliarezende521.workjourney.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Shared serialization for failures raised by security filters, outside MVC controllers. */
@Component
public final class ProblemResponseWriter {
    private final ObjectMapper mapper;

    public ProblemResponseWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), new Problem("about:blank", title, status));
    }

    private record Problem(String type, String title, int status) { }
}
