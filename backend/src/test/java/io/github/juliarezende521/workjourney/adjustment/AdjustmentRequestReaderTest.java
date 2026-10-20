package io.github.juliarezende521.workjourney.adjustment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class AdjustmentRequestReaderTest {
    private final AdjustmentRequestReader reader=new AdjustmentRequestReader(new ObjectMapper());
    @Test void readsTheExactContract() throws Exception {
        var request=json("{\"workSessionId\":\"00000000-0000-0000-0000-000000000001\",\"requestedEventType\":\"EXIT\",\"proposedOccurredAtUtc\":\"2026-10-20T12:00:00Z\",\"reason\":\"Forgot exit\"}");
        assertEquals("EXIT",reader.request(request).eventType().name());
    }
    @Test void rejectsUnknownAndDuplicateFields() {
        assertThrows(InvalidAdjustment.class,() -> reader.decision(json("{\"decision\":\"APPROVED\",\"admin\":true}")));
        assertThrows(InvalidAdjustment.class,() -> reader.decision(json("{\"decision\":\"APPROVED\",\"decision\":\"REJECTED\"}")));
    }
    @Test void rejectsOversizedChunkedBodies() {
        var error=assertThrows(InvalidAdjustment.class,() -> reader.decision(json("{\"decision\":\"APPROVED\",\"note\":\""+"a".repeat(5000)+"\"}")));
        assertEquals(413,error.status());
    }
    private MockHttpServletRequest json(String body) {
        var request=new MockHttpServletRequest(); request.setContentType("application/json"); request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return request;
    }
}
