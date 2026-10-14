package io.github.juliarezende521.workjourney.journey.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class MarkingRequestReaderTest {
    @Test void unknownContentLengthStillBoundsActualInput() throws Exception {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[1025]);
        var error = assertThrows(InvalidJourneyRequest.class, () -> new MarkingRequestReader(new ObjectMapper()).command(request));
        assertEquals(413, error.status());
    }
}
