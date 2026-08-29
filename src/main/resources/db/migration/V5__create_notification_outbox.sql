create table notification_outbox (
    id uuid primary key,
    type varchar(50) not null,
    channel varchar(50) not null,
    status varchar(30) not null,
    recipient varchar(255) not null,
    subject varchar(255) not null,
    body text not null,
    work_order_code varchar(50),
    approve_url text,
    reject_url text,
    attempts integer not null default 0,
    last_error text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    sent_at timestamp with time zone,
    constraint chk_notification_outbox_status check (status in ('PENDING', 'SENT', 'FAILED')),
    constraint chk_notification_outbox_type check (type in ('BUDGET_DECISION')),
    constraint chk_notification_outbox_channel check (channel in ('MAILPIT_EMAIL'))
);

create index idx_notification_outbox_status on notification_outbox(status);
create index idx_notification_outbox_status_created_at on notification_outbox(status, created_at);
