ALTER TABLE dbo.work_session ADD CONSTRAINT uq_work_session_id_employee UNIQUE (id, employee_id);
ALTER TABLE dbo.work_event ADD CONSTRAINT uq_work_event_id_session UNIQUE (id, work_session_id);

CREATE TABLE dbo.idempotency_request (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    operation varchar(20) COLLATE Latin1_General_100_BIN2 NOT NULL,
    request_key uniqueidentifier NOT NULL,
    payload_hash varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    command_type varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    expected_work_session_id uniqueidentifier NULL,
    http_status int NOT NULL,
    result_code varchar(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    result_work_session_id uniqueidentifier NULL,
    result_event_id uniqueidentifier NULL,
    occurred_at_utc datetimeoffset(7) NULL,
    confirmed_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_idempotency_request PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_request_key UNIQUE (employee_id, operation, request_key),
    CONSTRAINT fk_idempotency_request_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_idempotency_request_session_owner FOREIGN KEY (result_work_session_id, employee_id)
        REFERENCES dbo.work_session(id, employee_id),
    CONSTRAINT fk_idempotency_request_event_session FOREIGN KEY (result_event_id, result_work_session_id)
        REFERENCES dbo.work_event(id, work_session_id),
    CONSTRAINT ck_idempotency_request_operation CHECK (operation = 'ONLINE_EVENT' AND DATALENGTH(operation) = 12),
    CONSTRAINT ck_idempotency_request_hash CHECK (DATALENGTH(payload_hash) = 64 AND payload_hash NOT LIKE '%[^0-9a-f]%'),
    CONSTRAINT ck_idempotency_request_command CHECK (
        (command_type = 'ENTRY' AND expected_work_session_id IS NULL)
        OR (command_type IN ('PAUSE_START', 'PAUSE_END', 'EXIT') AND expected_work_session_id IS NOT NULL)),
    -- Rejected targets may be nonexistent or foreign; expected_work_session_id is not a FK.
    CONSTRAINT ck_idempotency_request_result CHECK (
        (http_status = 201 AND result_code = 'RECORDED' AND result_work_session_id IS NOT NULL
            AND result_event_id IS NOT NULL AND occurred_at_utc IS NOT NULL AND confirmed_at_utc >= occurred_at_utc)
        OR (http_status = 409 AND result_code IN ('ALREADY_OPEN', 'NO_OPEN_SESSION', 'TARGET_CHANGED', 'INVALID_TRANSITION', 'CLOCK_REGRESSION')
            AND result_work_session_id IS NULL AND result_event_id IS NULL AND occurred_at_utc IS NULL)),
    CONSTRAINT ck_idempotency_request_utc CHECK (DATEPART(TZOFFSET, confirmed_at_utc) = 0
        AND (occurred_at_utc IS NULL OR DATEPART(TZOFFSET, occurred_at_utc) = 0))
);
