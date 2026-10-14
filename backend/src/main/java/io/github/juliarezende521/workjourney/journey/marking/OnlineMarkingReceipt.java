package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.journey.WorkEventType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Immutable
@Table(name = "idempotency_request", schema = "dbo")
public class OnlineMarkingReceipt {
    @Id private UUID id;
    @Column(name = "employee_id", nullable = false, updatable = false) private UUID employeeId;
    @Column(nullable = false, length = 20, updatable = false) private String operation;
    @Column(name = "request_key", nullable = false, updatable = false) private UUID requestKey;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false) private String payloadHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 16, updatable = false) private WorkEventType commandType;
    @Column(name = "expected_work_session_id", updatable = false) private UUID expectedWorkSessionId;
    @Column(name = "http_status", nullable = false, updatable = false) private int httpStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_code", nullable = false, length = 32, updatable = false) private MarkingResult.Code resultCode;
    @Column(name = "result_work_session_id", updatable = false) private UUID resultWorkSessionId;
    @Column(name = "result_event_id", updatable = false) private UUID resultEventId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "occurred_at_utc", columnDefinition = "datetimeoffset(7)", precision = 7, updatable = false)
    private OffsetDateTime occurredAtUtc;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "confirmed_at_utc", nullable = false, columnDefinition = "datetimeoffset(7)", precision = 7, updatable = false)
    private OffsetDateTime confirmedAtUtc;

    protected OnlineMarkingReceipt() { }

    OnlineMarkingReceipt(UUID employeeId, MarkingCommand command, MarkingResult result) {
        id = UUID.randomUUID();
        this.employeeId = employeeId;
        operation = MarkingCommand.OPERATION;
        requestKey = result.requestKey();
        payloadHash = command.payloadHash();
        commandType = command.type();
        expectedWorkSessionId = command.expectedWorkSessionId();
        httpStatus = result.httpStatus();
        resultCode = result.code();
        resultWorkSessionId = result.workSessionId();
        resultEventId = result.eventId();
        occurredAtUtc = result.occurredAtUtc() == null ? null : result.occurredAtUtc().atOffset(ZoneOffset.UTC);
        confirmedAtUtc = result.confirmedAtUtc().atOffset(ZoneOffset.UTC);
    }

    boolean matches(MarkingCommand command) {
        return payloadHash.equals(command.payloadHash()) && commandType == command.type()
                && Objects.equals(expectedWorkSessionId, command.expectedWorkSessionId());
    }

    MarkingResult result() {
        return new MarkingResult(requestKey, commandType, httpStatus, resultCode, resultWorkSessionId,
                resultEventId, occurredAtUtc == null ? null : occurredAtUtc.toInstant(), confirmedAtUtc.toInstant());
    }
}
