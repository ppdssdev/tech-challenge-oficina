create table work_order_status_history (
    id uuid primary key,
    work_order_id uuid not null references work_orders(id),
    previous_status varchar(30),
    new_status varchar(30) not null,
    occurred_at timestamp with time zone not null,
    event_order integer not null check (event_order > 0),
    constraint uq_work_order_history_sequence unique (work_order_id, event_order),
    constraint chk_history_previous_status check (previous_status in
        ('RECEIVED', 'IN_DIAGNOSIS', 'WAITING_APPROVAL', 'IN_EXECUTION', 'FINALIZED', 'DELIVERED', 'BUDGET_REJECTED')),
    constraint chk_history_new_status check (new_status in
        ('RECEIVED', 'IN_DIAGNOSIS', 'WAITING_APPROVAL', 'IN_EXECUTION', 'FINALIZED', 'DELIVERED', 'BUDGET_REJECTED')),
    constraint chk_history_transition check (previous_status is distinct from new_status),
    constraint chk_history_creation check (previous_status is not null or (new_status = 'RECEIVED' and event_order = 1))
);
-- Existing orders intentionally have no fabricated historical events.
-- The unique sequence index also serves ordered lookups by work_order_id.
