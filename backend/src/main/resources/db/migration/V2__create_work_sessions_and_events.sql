-- Composite ownership keys prevent online events from using another account as author.
ALTER TABLE dbo.employee ADD CONSTRAINT uq_employee_id_user UNIQUE (id, user_id);

CREATE TABLE dbo.work_session (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    author_user_id uniqueidentifier NOT NULL,
    work_date date NOT NULL,
    reference_time_zone_id varchar(100) NOT NULL,
    state varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    opened_at_utc datetimeoffset(7) NOT NULL,
    last_event_at_utc datetimeoffset(7) NOT NULL,
    closed_at_utc datetimeoffset(7) NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_work_session PRIMARY KEY (id),
    CONSTRAINT uq_work_session_id_author UNIQUE (id, author_user_id),
    CONSTRAINT fk_work_session_employee_author FOREIGN KEY (employee_id, author_user_id)
        REFERENCES dbo.employee(id, user_id),
    CONSTRAINT ck_work_session_zone CHECK (LEN(LTRIM(RTRIM(reference_time_zone_id))) > 0),
    CONSTRAINT ck_work_session_version CHECK (version >= 0),
    CONSTRAINT ck_work_session_state CHECK (
        (state IN ('WORKING', 'PAUSED') AND closed_at_utc IS NULL)
        OR (state = 'CLOSED' AND closed_at_utc IS NOT NULL)),
    CONSTRAINT ck_work_session_utc CHECK (
        DATEPART(TZOFFSET, opened_at_utc) = 0
        AND DATEPART(TZOFFSET, last_event_at_utc) = 0
        AND (closed_at_utc IS NULL OR DATEPART(TZOFFSET, closed_at_utc) = 0)),
    CONSTRAINT ck_work_session_time CHECK (
        last_event_at_utc >= opened_at_utc
        AND (closed_at_utc IS NULL OR closed_at_utc >= last_event_at_utc))
);

-- No date key: overnight sessions and multiple closed sessions on one date are valid.
CREATE UNIQUE INDEX uq_work_session_open_employee
    ON dbo.work_session(employee_id) WHERE closed_at_utc IS NULL;

CREATE INDEX ix_work_session_employee_history
    ON dbo.work_session(employee_id, work_date DESC, id DESC);

CREATE TABLE dbo.work_event (
    id uniqueidentifier NOT NULL,
    work_session_id uniqueidentifier NOT NULL,
    author_user_id uniqueidentifier NOT NULL,
    sequence bigint NOT NULL,
    event_type varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    occurred_at_utc datetimeoffset(7) NOT NULL,
    recorded_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_work_event PRIMARY KEY (id),
    CONSTRAINT uq_work_event_session_sequence UNIQUE (work_session_id, sequence),
    CONSTRAINT fk_work_event_session_author FOREIGN KEY (work_session_id, author_user_id)
        REFERENCES dbo.work_session(id, author_user_id),
    CONSTRAINT ck_work_event_sequence_type CHECK (
        (sequence = 1 AND event_type = 'ENTRY')
        OR (sequence > 1 AND event_type IN ('PAUSE_START', 'PAUSE_END', 'EXIT'))),
    CONSTRAINT ck_work_event_utc CHECK (
        DATEPART(TZOFFSET, occurred_at_utc) = 0 AND DATEPART(TZOFFSET, recorded_at_utc) = 0),
    CONSTRAINT ck_work_event_time CHECK (recorded_at_utc >= occurred_at_utc)
);
