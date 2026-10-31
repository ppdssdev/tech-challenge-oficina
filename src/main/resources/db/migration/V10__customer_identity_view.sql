-- Stable public-domain contract; no rewrites, deduplication or invented identities.
create schema if not exists auth;
create or replace view auth.customer_identity_v1 as
select id as customer_id,
       regexp_replace(document_number, '[^0-9]', '', 'g') as cpf,
       active
from public.customers
where document_type = 'CPF'
  and document_number ~ '^[0-9. -]+$'
  and length(regexp_replace(document_number, '[^0-9]', '', 'g')) = 11;
revoke all on schema auth from public;
revoke all on auth.customer_identity_v1 from public;
