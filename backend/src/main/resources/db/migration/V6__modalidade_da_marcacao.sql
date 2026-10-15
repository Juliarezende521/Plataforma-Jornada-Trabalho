-- Registros antigos permanecem sem modalidade declarada.
ALTER TABLE dbo.work_event ADD work_mode varchar(16) COLLATE Latin1_General_100_BIN2 NULL;
GO
ALTER TABLE dbo.work_event ADD CONSTRAINT ck_work_event_work_mode
    CHECK (work_mode IS NULL OR work_mode IN ('OFFICE', 'REMOTE', 'TRAVEL'));
