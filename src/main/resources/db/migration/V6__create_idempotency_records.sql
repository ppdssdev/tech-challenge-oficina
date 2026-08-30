create table idempotency_records (
    id uuid primary key,
    operation varchar(80) not null,
    idempotency_key varchar(160) not null,
    request_hash varchar(128) not null,
    status varchar(30) not null,
    resource_code varchar(80),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    completed_at timestamp with time zone,
    last_error text,
    constraint uk_idempotency_operation_key unique (operation, idempotency_key),
    constraint chk_idempotency_status check (status in ('PROCESSING', 'COMPLETED', 'FAILED'))
);

create index idx_idempotency_operation_key
    on idempotency_records(operation, idempotency_key);
create index idx_idempotency_status_created_at
    on idempotency_records(status, created_at);
