CREATE TABLE dbo.work_schedule_exception (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    work_date date NOT NULL,
    planned_minutes smallint NOT NULL,
    tolerance_minutes smallint NOT NULL,
    category varchar(24) NOT NULL,
    reason nvarchar(200) NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_work_schedule_exception PRIMARY KEY (id),
    CONSTRAINT fk_schedule_exception_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_schedule_exception_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_schedule_exception_minutes CHECK (planned_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_schedule_exception_tolerance CHECK (tolerance_minutes BETWEEN 0 AND 120),
    CONSTRAINT ck_schedule_exception_category CHECK (category IN ('HOLIDAY','TIME_OFF','TRAINING','SPECIAL')),
    CONSTRAINT ck_schedule_exception_reason CHECK (LEN(reason) BETWEEN 3 AND 200),
    CONSTRAINT ck_schedule_exception_created_utc CHECK (DATEPART(TZOFFSET, created_at_utc) = 0)
);
GO
CREATE INDEX ix_schedule_exception_employee_date
    ON dbo.work_schedule_exception(employee_id, work_date, created_at_utc DESC);
