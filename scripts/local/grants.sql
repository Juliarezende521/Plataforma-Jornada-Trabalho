USE work_journey;
GO

GRANT SELECT, INSERT ON dbo.app_user TO work_journey_app;
GRANT SELECT, INSERT ON dbo.employee TO work_journey_app;
GRANT SELECT, INSERT, UPDATE ON dbo.work_session TO work_journey_app;
GRANT SELECT, INSERT ON dbo.work_event TO work_journey_app;
GRANT SELECT, INSERT ON dbo.idempotency_request TO work_journey_app;
GRANT SELECT, INSERT, UPDATE ON dbo.work_adjustment_request TO work_journey_app;
GRANT INSERT ON dbo.audit_entry TO work_journey_app;
GO
