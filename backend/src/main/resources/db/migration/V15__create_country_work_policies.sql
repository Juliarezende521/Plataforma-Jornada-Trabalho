CREATE TABLE dbo.country_work_policy (
    id uniqueidentifier NOT NULL,
    country_code char(2) NOT NULL,
    effective_from date NOT NULL,
    standard_weekly_minutes smallint NOT NULL,
    maximum_daily_minutes smallint NOT NULL,
    minimum_break_minutes smallint NOT NULL,
    overtime_tolerance_minutes smallint NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at_utc datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_country_work_policy PRIMARY KEY (id),
    CONSTRAINT uq_country_work_policy_version UNIQUE (country_code, effective_from),
    CONSTRAINT fk_country_work_policy_creator FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_country_work_policy_country CHECK (country_code LIKE '[A-Z][A-Z]'),
    CONSTRAINT ck_country_work_policy_weekly CHECK (standard_weekly_minutes BETWEEN 1 AND 10080),
    CONSTRAINT ck_country_work_policy_daily CHECK (maximum_daily_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_country_work_policy_break CHECK (minimum_break_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_country_work_policy_tolerance CHECK (overtime_tolerance_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_country_work_policy_utc CHECK (DATEPART(TZOFFSET, created_at_utc) = 0)
);
GO
CREATE INDEX ix_country_work_policy_country_effective
    ON dbo.country_work_policy(country_code, effective_from DESC, id DESC);
