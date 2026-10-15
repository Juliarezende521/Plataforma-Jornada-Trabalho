-- Existing accounts remain employees without cross-employee access.
ALTER TABLE dbo.app_user ADD hr_reader bit NOT NULL
    CONSTRAINT df_app_user_hr_reader DEFAULT (0) WITH VALUES;
