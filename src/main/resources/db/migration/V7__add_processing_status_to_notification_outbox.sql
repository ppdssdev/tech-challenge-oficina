alter table notification_outbox
    drop constraint chk_notification_outbox_status;

alter table notification_outbox
    add constraint chk_notification_outbox_status
    check (status in ('PENDING', 'PROCESSING', 'SENT', 'FAILED'));
