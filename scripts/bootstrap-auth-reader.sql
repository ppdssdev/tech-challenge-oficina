-- Run once, after V11, as the migration owner with CREATEROLE (psql -v ON_ERROR_STOP=1).
-- Deliberately fails if the role already exists, rather than trusting old privileges.
-- This group role has no login or password. Provision a dedicated login separately.
begin;
create role oficina_auth_reader nologin nosuperuser nocreatedb nocreaterole
    noinherit noreplication nobypassrls;
grant usage on schema auth to oficina_auth_reader;
grant select on auth.customer_identity_v2 to oficina_auth_reader;

-- PostgreSQL has no DENY: PUBLIC grants are inherited even with NOINHERIT.
-- Refuse unsafe ambient grants instead of changing privileges of existing apps.
do $$
declare
    object record;
begin
    for object in
        select n.nspname, c.relname, c.oid
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname not in ('pg_catalog', 'information_schema')
          and n.nspname !~ '^pg_toast' and n.nspname !~ '^pg_temp'
          and c.relkind in ('r', 'p', 'v', 'm', 'f')
          and c.oid <> 'auth.customer_identity_v2'::regclass
    loop
        if has_table_privilege('oficina_auth_reader', object.oid,
            'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
           or has_any_column_privilege('oficina_auth_reader', object.oid,
            'SELECT,INSERT,UPDATE,REFERENCES') then
            raise exception 'Unsafe inherited privileges on %.%', object.nspname, object.relname;
        end if;
    end loop;
    for object in select oid, nspname from pg_namespace
        where nspname not like 'pg_%' and nspname <> 'information_schema'
    loop
        if has_schema_privilege('oficina_auth_reader', object.oid, 'CREATE') then
            raise exception 'Unsafe inherited CREATE on schema %', object.nspname;
        end if;
    end loop;
end
$$;
commit;
