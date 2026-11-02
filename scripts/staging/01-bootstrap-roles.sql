-- Execute as the RDS bootstrap administrator, connected to oficina_db. One-time only.
-- Passwords are deliberately absent: logins cannot use password auth until provisioned.
begin;
do $$ begin
    if current_database() <> 'oficina_db' then raise exception 'Expected oficina_db'; end if;
end $$;
create role oficina_migrator login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
create role oficina_api login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
create role oficina_lambda login inherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
grant oficina_migrator to current_user;
revoke create on schema public from public;
revoke connect, temporary on database oficina_db from public;
grant connect on database oficina_db to oficina_migrator, oficina_api, oficina_lambda;
grant create on database oficina_db to oficina_migrator;
grant usage, create on schema public to oficina_migrator;
grant usage on schema public to oficina_api;
commit;
