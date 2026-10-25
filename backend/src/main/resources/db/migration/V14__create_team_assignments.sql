CREATE TABLE dbo.team_assignment (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    manager_employee_id uniqueidentifier NOT NULL,
    effective_from date NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_team_assignment PRIMARY KEY (id),
    CONSTRAINT uq_team_assignment_version UNIQUE (employee_id, effective_from),
    CONSTRAINT fk_team_assignment_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_team_assignment_manager FOREIGN KEY (manager_employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_team_assignment_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_team_assignment_distinct CHECK (employee_id <> manager_employee_id),
    CONSTRAINT ck_team_assignment_utc CHECK (DATEPART(TZOFFSET, created_at_utc) = 0)
);
GO
CREATE INDEX ix_team_assignment_employee_effective
    ON dbo.team_assignment(employee_id, effective_from DESC, id DESC);
CREATE INDEX ix_team_assignment_manager_effective
    ON dbo.team_assignment(manager_employee_id, effective_from DESC, employee_id);
