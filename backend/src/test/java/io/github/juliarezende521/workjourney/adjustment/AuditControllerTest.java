package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.journey.query.JourneyQueries.InvalidFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuditControllerTest {
    private final AuditQueryService service = mock(AuditQueryService.class);
    private final AuditController controller = new AuditController(service);

    @Test void acceptsOnlySingleBoundedPaginationParameters() {
        var valid = new MockHttpServletRequest();
        valid.addParameter("page", "1");
        valid.addParameter("size", "50");
        controller.history(valid);
        verify(service).history(1, 50);

        for (var request : new MockHttpServletRequest[]{unknown(), duplicate(), nonNumeric()}) {
            assertThrows(InvalidFilter.class, () -> controller.history(request));
        }
        verifyNoMoreInteractions(service);
    }

    private MockHttpServletRequest unknown() {
        var request = new MockHttpServletRequest(); request.addParameter("actorUserId", "forged"); return request;
    }
    private MockHttpServletRequest duplicate() {
        var request = new MockHttpServletRequest(); request.addParameter("page", "0", "1"); return request;
    }
    private MockHttpServletRequest nonNumeric() {
        var request = new MockHttpServletRequest(); request.addParameter("size", "many"); return request;
    }
}
