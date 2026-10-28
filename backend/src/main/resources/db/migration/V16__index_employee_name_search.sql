CREATE INDEX ix_employee_name_directory
    ON dbo.employee(name, id)
    INCLUDE (reference_time_zone_id, user_id);
