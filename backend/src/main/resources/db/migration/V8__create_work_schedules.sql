CREATE TABLE dbo.work_schedule (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    effective_from date NOT NULL,
    monday_minutes smallint NOT NULL,
    tuesday_minutes smallint NOT NULL,
    wednesday_minutes smallint NOT NULL,
    thursday_minutes smallint NOT NULL,
    friday_minutes smallint NOT NULL,
    saturday_minutes smallint NOT NULL,
    sunday_minutes smallint NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_work_schedule PRIMARY KEY (id),
    CONSTRAINT uq_work_schedule_employee_date UNIQUE (employee_id, effective_from),
    CONSTRAINT fk_work_schedule_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_work_schedule_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_work_schedule_minutes CHECK (
        monday_minutes BETWEEN 0 AND 1440 AND tuesday_minutes BETWEEN 0 AND 1440
        AND wednesday_minutes BETWEEN 0 AND 1440 AND thursday_minutes BETWEEN 0 AND 1440
        AND friday_minutes BETWEEN 0 AND 1440 AND saturday_minutes BETWEEN 0 AND 1440
        AND sunday_minutes BETWEEN 0 AND 1440
        AND monday_minutes + tuesday_minutes + wednesday_minutes + thursday_minutes
            + friday_minutes + saturday_minutes + sunday_minutes > 0),
    CONSTRAINT ck_work_schedule_created_utc CHECK (DATEPART(TZOFFSET, created_at_utc) = 0)
);
GO
CREATE INDEX ix_work_schedule_employee_effective
    ON dbo.work_schedule(employee_id, effective_from DESC);
