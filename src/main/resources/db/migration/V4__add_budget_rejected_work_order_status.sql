alter table work_orders drop constraint chk_work_order_status;

alter table work_orders
    add constraint chk_work_order_status
    check (status in (
        'RECEIVED',
        'IN_DIAGNOSIS',
        'WAITING_APPROVAL',
        'IN_EXECUTION',
        'BUDGET_REJECTED',
        'FINALIZED',
        'DELIVERED'
    ));
