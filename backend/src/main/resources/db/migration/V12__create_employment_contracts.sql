CREATE TABLE dbo.employment_contract (
    id uniqueidentifier NOT NULL,
    employee_id uniqueidentifier NOT NULL,
    effective_from date NOT NULL,
    country_code char(2) NOT NULL,
    employment_type varchar(20) NOT NULL,
    weekly_minutes smallint NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_employment_contract PRIMARY KEY (id),
    CONSTRAINT uq_employment_contract_version UNIQUE (employee_id, effective_from),
    CONSTRAINT fk_employment_contract_employee FOREIGN KEY (employee_id) REFERENCES dbo.employee(id),
    CONSTRAINT fk_employment_contract_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_employment_contract_country CHECK (country_code LIKE '[A-Z][A-Z]'),
    CONSTRAINT ck_employment_contract_type CHECK (employment_type IN ('FULL_TIME','PART_TIME')),
    CONSTRAINT ck_employment_contract_minutes CHECK (weekly_minutes BETWEEN 1 AND 10080),
    CONSTRAINT ck_employment_contract_utc CHECK (DATEPART(TZOFFSET, created_at_utc) = 0)
);
GO
CREATE INDEX ix_employment_contract_employee_effective
    ON dbo.employment_contract(employee_id, effective_from DESC, id DESC);
