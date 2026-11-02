-- After the Flyway Job and scripts/bootstrap-auth-reader.sql, as bootstrap admin.
-- The admin inherits the migration owner through 01; the API never inherits it.
begin;
grant select, insert on public.app_users to oficina_api;
grant select, insert, update, delete on
    public.customers, public.vehicles, public.service_catalog_items, public.parts,
    public.work_orders, public.work_order_service_items, public.work_order_part_items,
    public.notification_outbox, public.idempotency_records
    to oficina_api;
grant select, insert on public.work_order_status_history to oficina_api;
grant oficina_auth_reader to oficina_lambda;
commit;
-- No grants on Flyway history or auth.customer_access; no default grants on future tables.
