ALTER TABLE dbo.work_session ADD CONSTRAINT uq_work_session_id_employee UNIQUE (id, employee_id);

CREATE TABLE dbo.work_adjustment_request (
    id uniqueidentifier NOT NULL,
    work_session_id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    requested_by_user_id uniqueidentifier NOT NULL,
    requested_event_type varchar(16) NOT NULL,
    proposed_occurred_at_utc datetimeoffset(7) NOT NULL,
    reason nvarchar(500) NOT NULL,
    status varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    requested_at_utc datetimeoffset(7) NOT NULL,
    decided_at_utc datetimeoffset(7) NULL,
    decided_by_user_id uniqueidentifier NULL,
    decision_note nvarchar(500) NULL,
    applied_event_id uniqueidentifier NULL,
    version bigint NOT NULL CONSTRAINT df_work_adjustment_version DEFAULT (0),
    CONSTRAINT pk_work_adjustment_request PRIMARY KEY (id),
    CONSTRAINT fk_work_adjustment_session_employee FOREIGN KEY (work_session_id, employee_id)
        REFERENCES dbo.work_session(id, employee_id),
    CONSTRAINT fk_work_adjustment_employee_requester FOREIGN KEY (employee_id, requested_by_user_id)
        REFERENCES dbo.employee(id, user_id),
    CONSTRAINT fk_work_adjustment_reviewer FOREIGN KEY (decided_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT fk_work_adjustment_applied_event FOREIGN KEY (applied_event_id) REFERENCES dbo.work_event(id),
    CONSTRAINT ck_work_adjustment_event_type CHECK (requested_event_type IN ('ENTRY', 'PAUSE_START', 'PAUSE_END', 'EXIT')),
    CONSTRAINT ck_work_adjustment_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_work_adjustment_reason CHECK (LEN(LTRIM(RTRIM(reason))) BETWEEN 10 AND 500),
    CONSTRAINT ck_work_adjustment_version CHECK (version >= 0),
    CONSTRAINT ck_work_adjustment_utc CHECK (
        DATEPART(TZOFFSET, proposed_occurred_at_utc) = 0
        AND DATEPART(TZOFFSET, requested_at_utc) = 0
        AND (decided_at_utc IS NULL OR DATEPART(TZOFFSET, decided_at_utc) = 0)),
    CONSTRAINT ck_work_adjustment_decision CHECK (
        (status = 'PENDING' AND decided_at_utc IS NULL AND decided_by_user_id IS NULL AND decision_note IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND decided_at_utc IS NOT NULL AND decided_by_user_id IS NOT NULL)
    )
);
GO
CREATE INDEX ix_work_adjustment_employee_requested
    ON dbo.work_adjustment_request(employee_id, requested_at_utc DESC, id DESC);
CREATE INDEX ix_work_adjustment_status_requested
    ON dbo.work_adjustment_request(status, requested_at_utc, id);

CREATE TABLE dbo.audit_entry (
    id uniqueidentifier NOT NULL,
    actor_user_id uniqueidentifier NOT NULL,
    action varchar(64) NOT NULL,
    entity_type varchar(64) NOT NULL,
    entity_id uniqueidentifier NOT NULL,
    recorded_at_utc datetimeoffset(7) NOT NULL,
    details nvarchar(1000) NULL,
    CONSTRAINT pk_audit_entry PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_audit_action CHECK (LEN(LTRIM(RTRIM(action))) > 0),
    CONSTRAINT ck_audit_entity_type CHECK (LEN(LTRIM(RTRIM(entity_type))) > 0),
    CONSTRAINT ck_audit_utc CHECK (DATEPART(TZOFFSET, recorded_at_utc) = 0)
);
GO
CREATE INDEX ix_audit_entity ON dbo.audit_entry(entity_type, entity_id, recorded_at_utc);
