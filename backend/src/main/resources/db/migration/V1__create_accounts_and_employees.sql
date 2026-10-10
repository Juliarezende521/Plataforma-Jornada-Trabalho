CREATE TABLE dbo.app_user (
    id uniqueidentifier NOT NULL,
    email varchar(254) NOT NULL,
    password_hash varchar(60) NOT NULL,
    active bit NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email CHECK (LEN(email) > 0 AND email = LOWER(LTRIM(RTRIM(email))))
);

CREATE TABLE dbo.employee (
    id uniqueidentifier NOT NULL,
    user_id uniqueidentifier NOT NULL,
    name nvarchar(150) NOT NULL,
    reference_time_zone_id varchar(100) NOT NULL,
    CONSTRAINT pk_employee PRIMARY KEY (id),
    CONSTRAINT uq_employee_user UNIQUE (user_id),
    CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_employee_name CHECK (LEN(LTRIM(RTRIM(name))) > 0),
    CONSTRAINT ck_employee_zone CHECK (LEN(reference_time_zone_id) > 0)
);
