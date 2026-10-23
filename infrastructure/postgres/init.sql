-- Enable pgvector extension for RAG embeddings
CREATE EXTENSION IF NOT EXISTS vector;

-- Ensure UUID generation is available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------------------------------
-- Least-privilege application role (genz_app)
--
-- The APP_DB_PASSWORD psql variable is set by the entrypoint wrapper
-- script (infrastructure/postgres/docker-entrypoint-initdb.d/00-init.sh)
-- which reads $APP_DB_PASSWORD from the container environment and passes
-- it to psql via: psql -v app_db_password="$APP_DB_PASSWORD" -f init.sql
--
-- The role is created NOSUPERUSER + NOBYPASSRLS so RLS is always enforced.
-- flyway_schema_history is revoked after the broad GRANT so Flyway's admin
-- account (genz_user) keeps it but the app role never touches it.
-- Default privileges ensure future Flyway-owned tables are covered.
-- -----------------------------------------------------------------------

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'genz_app') THEN
    EXECUTE format(
      'CREATE ROLE genz_app WITH LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE',
      current_setting('app.init_app_db_password', true)
    );
  ELSE
    -- Idempotent: re-enforce privilege flags on the existing role.
    -- Password is NOT changed here to avoid overwriting a manually rotated one.
    ALTER ROLE genz_app WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
  END IF;
END
$$;

GRANT CONNECT ON DATABASE genz_ai TO genz_app;
GRANT USAGE ON SCHEMA public TO genz_app;

-- Broad DML grant — flyway_schema_history is revoked immediately after.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO genz_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO genz_app;

-- Deny access to Flyway's internal metadata table.
REVOKE ALL ON TABLE flyway_schema_history FROM genz_app;

-- Default privileges: tables created by genz_user (Flyway) in future migrations
-- are automatically visible to genz_app — no manual re-grant needed.
ALTER DEFAULT PRIVILEGES FOR ROLE genz_user IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO genz_app;
ALTER DEFAULT PRIVILEGES FOR ROLE genz_user IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO genz_app;

SELECT 'init.sql complete: pgvector, uuid-ossp, genz_app role provisioned' AS status;
