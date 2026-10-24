CREATE TABLE dbo.monthly_closure (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    competence_year smallint NOT NULL,
    competence_month tinyint NOT NULL,
    period_from date NOT NULL,
    period_to date NOT NULL,
    total_work_seconds bigint NOT NULL,
    planned_work_seconds bigint NOT NULL,
    balance_seconds bigint NOT NULL,
    tolerance_seconds bigint NOT NULL,
    within_tolerance bit NOT NULL,
    total_sessions bigint NOT NULL,
    adjusted_sessions bigint NOT NULL,
    worked_days int NOT NULL,
    closed_by_user_id uniqueidentifier NOT NULL,
    closed_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_monthly_closure PRIMARY KEY (id),
    CONSTRAINT uq_monthly_closure_competence UNIQUE (employee_id, competence_year, competence_month),
    CONSTRAINT fk_monthly_closure_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_monthly_closure_user FOREIGN KEY (closed_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_monthly_closure_year CHECK (competence_year BETWEEN 1 AND 9999),
    CONSTRAINT ck_monthly_closure_month CHECK (competence_month BETWEEN 1 AND 12),
    CONSTRAINT ck_monthly_closure_period CHECK (period_from <= period_to),
    CONSTRAINT ck_monthly_closure_counts CHECK (
        total_work_seconds >= 0 AND planned_work_seconds >= 0 AND tolerance_seconds >= 0
        AND total_sessions >= 0 AND adjusted_sessions >= 0 AND adjusted_sessions <= total_sessions
        AND worked_days >= 0
    ),
    CONSTRAINT ck_monthly_closure_utc CHECK (DATEPART(TZOFFSET, closed_at_utc) = 0)
);
GO
CREATE INDEX ix_monthly_closure_employee_competence
    ON dbo.monthly_closure(employee_id, competence_year DESC, competence_month DESC, id DESC);
