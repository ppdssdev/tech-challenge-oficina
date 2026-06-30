create table app_users (
    id uuid primary key,
    username varchar(80) not null unique,
    password_hash varchar(120) not null,
    role varchar(30) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table customers (
    id uuid primary key,
    full_name varchar(120) not null,
    document_type varchar(4) not null,
    document_number varchar(14) not null unique,
    email varchar(160),
    phone varchar(20),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint chk_customer_document_type check (document_type in ('CPF', 'CNPJ'))
);

create table vehicles (
    id uuid primary key,
    customer_id uuid not null references customers(id),
    plate varchar(7) not null unique,
    brand varchar(60) not null,
    model varchar(80) not null,
    manufacturing_year integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table service_catalog_items (
    id uuid primary key,
    name varchar(100) not null,
    description varchar(500),
    base_price numeric(12, 2) not null,
    estimated_minutes integer not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table parts (
    id uuid primary key,
    name varchar(120) not null,
    sku varchar(40) not null unique,
    unit_price numeric(12, 2) not null,
    quantity_in_stock integer not null,
    minimum_stock integer not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table work_orders (
    id uuid primary key,
    code varchar(30) not null unique,
    customer_id uuid not null references customers(id),
    vehicle_id uuid not null references vehicles(id),
    status varchar(30) not null,
    diagnostic_notes varchar(2000),
    customer_authorized_at timestamp with time zone,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    delivered_at timestamp with time zone,
    total_services numeric(12, 2) not null,
    total_parts numeric(12, 2) not null,
    total_amount numeric(12, 2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint chk_work_order_status check (status in ('RECEIVED', 'IN_DIAGNOSIS', 'WAITING_APPROVAL', 'IN_EXECUTION', 'FINALIZED', 'DELIVERED'))
);

create table work_order_service_items (
    id uuid primary key,
    work_order_id uuid not null references work_orders(id) on delete cascade,
    service_id uuid not null references service_catalog_items(id),
    service_name varchar(100) not null,
    unit_price numeric(12, 2) not null,
    quantity integer not null,
    estimated_minutes integer not null,
    line_total numeric(12, 2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table work_order_part_items (
    id uuid primary key,
    work_order_id uuid not null references work_orders(id) on delete cascade,
    part_id uuid not null references parts(id),
    part_name varchar(120) not null,
    sku varchar(40) not null,
    unit_price numeric(12, 2) not null,
    quantity integer not null,
    line_total numeric(12, 2) not null,
    stock_reserved boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_customers_document_number on customers(document_number);
create index idx_vehicles_customer_id on vehicles(customer_id);
create index idx_vehicles_plate on vehicles(plate);
create index idx_work_orders_code on work_orders(code);
create index idx_work_orders_status on work_orders(status);
create index idx_work_orders_customer_id on work_orders(customer_id);
create index idx_work_orders_vehicle_id on work_orders(vehicle_id);
create index idx_parts_sku on parts(sku);
