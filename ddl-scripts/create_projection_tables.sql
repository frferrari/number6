
create database number6;
create user number6 with encrypted password 'number6';
grant all privileges on database number6 to number6;

CREATE TABLE IF NOT EXISTS public.AKKA_PROJECTION_OFFSET_STORE (
    projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    current_offset VARCHAR(255) NOT NULL,
    manifest VARCHAR(4) NOT NULL,
    mergeable BOOLEAN NOT NULL,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY(projection_name, projection_key)
    );

CREATE INDEX IF NOT EXISTS projection_name_index ON AKKA_PROJECTION_OFFSET_STORE (projection_name);
