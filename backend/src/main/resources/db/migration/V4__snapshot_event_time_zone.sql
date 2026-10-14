-- Existing events keep their journey's original reference zone. New events snapshot the employee zone at registration.
ALTER TABLE dbo.work_event ADD recorded_time_zone_id varchar(100) NULL;
UPDATE e SET recorded_time_zone_id = s.reference_time_zone_id
FROM dbo.work_event e JOIN dbo.work_session s ON s.id = e.work_session_id;
ALTER TABLE dbo.work_event ALTER COLUMN recorded_time_zone_id varchar(100) NOT NULL;
ALTER TABLE dbo.work_event ADD CONSTRAINT ck_work_event_recorded_zone CHECK (LEN(LTRIM(RTRIM(recorded_time_zone_id))) > 0);
