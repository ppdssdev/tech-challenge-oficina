-- Run as provisioning administrator AFTER Flyway V10. No password in source.
-- NOLOGIN capability role. A separate provisioned LOGIN inherits ONLY this role.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auth_readonly') THEN
        CREATE ROLE auth_readonly NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END $$;
GRANT USAGE ON SCHEMA auth TO auth_readonly;
GRANT SELECT ON auth.customer_identity_v1 TO auth_readonly;
ALTER ROLE auth_readonly SET default_transaction_read_only = on;
-- No domain table grants and no default privileges on future objects.
