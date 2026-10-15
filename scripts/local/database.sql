IF DB_ID(N'work_journey') IS NULL
BEGIN
    CREATE DATABASE work_journey;
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'work_journey_migrator')
BEGIN
    CREATE LOGIN work_journey_migrator WITH PASSWORD = 'LocalMigrator@2026';
END;

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'work_journey_app')
BEGIN
    CREATE LOGIN work_journey_app WITH PASSWORD = 'LocalApplication@2026';
END;
GO

USE work_journey;
GO

IF DATABASE_PRINCIPAL_ID(N'work_journey_migrator') IS NULL
BEGIN
    CREATE USER work_journey_migrator FOR LOGIN work_journey_migrator;
    ALTER ROLE db_ddladmin ADD MEMBER work_journey_migrator;
    ALTER ROLE db_datareader ADD MEMBER work_journey_migrator;
    ALTER ROLE db_datawriter ADD MEMBER work_journey_migrator;
END;

IF DATABASE_PRINCIPAL_ID(N'work_journey_app') IS NULL
BEGIN
    CREATE USER work_journey_app FOR LOGIN work_journey_app;
END;
GO
