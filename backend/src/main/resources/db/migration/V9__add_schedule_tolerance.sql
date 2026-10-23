ALTER TABLE dbo.work_schedule
    ADD tolerance_minutes smallint NOT NULL
        CONSTRAINT df_work_schedule_tolerance DEFAULT 0;
GO
ALTER TABLE dbo.work_schedule
    ADD CONSTRAINT ck_work_schedule_tolerance CHECK (tolerance_minutes BETWEEN 0 AND 120);
