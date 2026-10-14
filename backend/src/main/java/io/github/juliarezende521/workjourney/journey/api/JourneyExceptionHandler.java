package io.github.juliarezende521.workjourney.journey.api;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = JourneyController.class)
public class JourneyExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JourneyExceptionHandler.class);

    @ExceptionHandler(InvalidJourneyRequest.class)
    ResponseEntity<Problem> invalid(InvalidJourneyRequest error) {
        return problem(error.status(), error.status() == 413 ? "Request body too large" : "Invalid journey request");
    }

    @ExceptionHandler(JourneyController.ReceiptNotFound.class)
    ResponseEntity<Problem> absent() { return problem(404, "Receipt not visible"); }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Problem> unauthenticated() { return problem(401, "Authentication required"); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problem> denied() { return problem(403, "Access denied"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> failure(Exception error) {
        // No request payload, identity, exception message or SQL in the log/public response.
        String correlation = UUID.randomUUID().toString();
        LOG.error("Journey request failed: correlationId={}, exceptionType={}", correlation, error.getClass().getSimpleName());
        int status = error instanceof TransientDataAccessException ? 503 : 500;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Correlation-Id", correlation)
                .body(new Problem("about:blank", "Journey request could not be completed", status, correlation));
    }

    private ResponseEntity<Problem> problem(int status, String title) {
        String correlation = UUID.randomUUID().toString();
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Correlation-Id", correlation).body(new Problem("about:blank", title, status, correlation));
    }

    record Problem(String type, String title, int status, String correlationId) { }
}
