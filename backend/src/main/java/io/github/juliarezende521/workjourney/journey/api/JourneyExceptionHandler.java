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

@RestControllerAdvice(assignableTypes = {JourneyController.class, JourneyQueryController.class, HrJourneyController.class,
        io.github.juliarezende521.workjourney.adjustment.WorkAdjustmentController.class,
        io.github.juliarezende521.workjourney.adjustment.AuditController.class,
        io.github.juliarezende521.workjourney.closing.MonthlyClosureController.class,
        io.github.juliarezende521.workjourney.closing.MonthlyClosureReportController.class,
        io.github.juliarezende521.workjourney.team.ManagerJourneyController.class,
        io.github.juliarezende521.workjourney.team.TeamAssignmentController.class,
        io.github.juliarezende521.workjourney.contract.EmploymentContractController.class,
        io.github.juliarezende521.workjourney.schedule.WorkScheduleController.class})
public class JourneyExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JourneyExceptionHandler.class);

    @ExceptionHandler(io.github.juliarezende521.workjourney.journey.query.JourneyQueries.InvalidFilter.class)
    ResponseEntity<Problem> filter() { return problem(400, "Invalid history filter"); }

    @ExceptionHandler(io.github.juliarezende521.workjourney.journey.query.JourneyQueries.NotVisible.class)
    ResponseEntity<Problem> hidden() { return problem(404, "Journey not visible"); }

    @ExceptionHandler(InvalidJourneyRequest.class)
    ResponseEntity<Problem> invalid(InvalidJourneyRequest error) {
        return problem(error.status(), error.status() == 413 ? "Request body too large" : "Invalid journey request");
    }

    @ExceptionHandler(JourneyController.ReceiptNotFound.class)
    ResponseEntity<Problem> absent() { return problem(404, "Receipt not visible"); }

    @ExceptionHandler(io.github.juliarezende521.workjourney.adjustment.InvalidAdjustment.class)
    ResponseEntity<Problem> invalidAdjustment(io.github.juliarezende521.workjourney.adjustment.InvalidAdjustment error) {
        return problem(error.status(), error.status()==413 ? "Request body too large" : "Invalid adjustment request");
    }

    @ExceptionHandler(io.github.juliarezende521.workjourney.adjustment.AdjustmentNotVisible.class)
    ResponseEntity<Problem> hiddenAdjustment() { return problem(404, "Adjustment not visible"); }

    @ExceptionHandler(io.github.juliarezende521.workjourney.adjustment.AdjustmentConflict.class)
    ResponseEntity<Problem> adjustmentConflict() { return problem(409, "Adjustment decision conflicts with current journey"); }

    @ExceptionHandler(io.github.juliarezende521.workjourney.schedule.InvalidSchedule.class)
    ResponseEntity<Problem> invalidSchedule(io.github.juliarezende521.workjourney.schedule.InvalidSchedule error) {
        return problem(error.status(),error.status()==413?"Request body too large":error.status()==409?"Schedule already exists for effective date":"Invalid work schedule");
    }
    @ExceptionHandler(io.github.juliarezende521.workjourney.contract.InvalidContract.class)
    ResponseEntity<Problem> invalidContract(io.github.juliarezende521.workjourney.contract.InvalidContract error) {
        return problem(error.status(),error.status()==413?"Request body too large":error.status()==409?"Contract already exists for effective date":"Invalid employment contract");
    }

    @ExceptionHandler(io.github.juliarezende521.workjourney.closing.InvalidMonthlyClosure.class)
    ResponseEntity<Problem> invalidClosure(io.github.juliarezende521.workjourney.closing.InvalidMonthlyClosure error) {
        String title = switch (error.reason()) {
            case "PERIOD_NOT_FINISHED" -> "Monthly period has not finished";
            case "ALREADY_CLOSED" -> "Monthly period is already closed";
            case "OPEN_SESSIONS" -> "Monthly period has open work sessions";
            case "INCOMPLETE_SCHEDULE" -> "Monthly period has incomplete schedule coverage";
            case "PENDING_ADJUSTMENTS" -> "Monthly period has pending adjustments";
            case "PERIOD_CLOSED" -> "Monthly period is closed";
            case "REPORT_TOO_LARGE" -> "Monthly closure report is too large";
            default -> "Invalid monthly closure request";
        };
        return problem(error.status(), title);
    }

    @ExceptionHandler(io.github.juliarezende521.workjourney.team.InvalidTeamAssignment.class)
    ResponseEntity<Problem> invalidTeamAssignment(io.github.juliarezende521.workjourney.team.InvalidTeamAssignment error) {
        String title = switch (error.reason()) {
            case "SELF_ASSIGNMENT" -> "Employee cannot manage themself";
            case "VERSION_EXISTS" -> "Manager assignment already exists for effective date";
            case "CYCLE" -> "Manager assignment would create a cycle";
            case "TOO_LARGE" -> "Request body too large";
            default -> "Invalid manager assignment";
        };
        return problem(error.status(), title);
    }

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
