package io.github.juliarezende521.workjourney.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ProblemResponseWriterTest {
    @Test
    void serializesProblemWithEscapedTextAndUtf8() throws Exception {
        var mapper = new ObjectMapper();
        var response = new MockHttpServletResponse();
        String title = "Falha \"controlada\"\nsem exposição";
        new ProblemResponseWriter(mapper).write(response, 401, title);
        var body = mapper.readTree(response.getContentAsString());
        assertEquals(401, response.getStatus());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertEquals("about:blank", body.get("type").asText());
        assertEquals(title, body.get("title").asText());
        assertEquals(401, body.get("status").asInt());
    }
}
