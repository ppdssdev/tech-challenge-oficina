-- Reconcile the recovered V8 after main V8-V10, without changing their checksums.
-- v1 retains customers.active/normalized-CPF semantics for existing Lambda consumers.
-- Authentication eligibility is separate from the customer business lifecycle.
-- No customer (existing or new) is enabled implicitly.
create schema if not exists auth;
revoke all on schema auth from public;

create table auth.customer_access (
    customer_id uuid primary key references public.customers(id) on delete cascade,
    active boolean not null default false
);
revoke all on auth.customer_access from public;

-- Non-unique: tolerate legacy duplicates, but make ambiguity checks indexable.
create index idx_customers_auth_normalized_cpf
    on public.customers ((regexp_replace(document_number, '[^0-9]', '', 'g')))
    where document_type = 'CPF';

create view auth.customer_identity_v2 with (security_barrier = true) as
select c.id as customer_id,
       c.document_number as cpf,
       coalesce(a.active, false) as active
from public.customers c
left join auth.customer_access a on a.customer_id = c.id
where c.document_type = 'CPF'
  and c.document_number ~ '^[0-9]{11}$'
  -- Do not choose an identity if legacy formatted data makes it ambiguous.
  and not exists (
      select 1 from public.customers other
      where other.id <> c.id
        and other.document_type = 'CPF'
        and regexp_replace(other.document_number, '[^0-9]', '', 'g') = c.document_number
  );
revoke all on auth.customer_identity_v2 from public;

comment on table auth.customer_access is
    'Explicit Lambda authentication eligibility; absence of a row means disabled. Not a business customer status.';
comment on view auth.customer_identity_v2 is
    'Lambda SQL contract: canonical CPF, customer UUID and explicit authentication eligibility. CPF check digits are validated by the API and Lambda.';
