create table aggregation_settings (
    id bigint primary key,
    tax_mode varchar(20) not null,
    tax_rate numeric(9,4) not null check (tax_rate between 0 and 100),
    auto_enabled boolean not null,
    execution_time time not null,
    time_zone varchar(50) not null,
    version bigint not null default 0,
    updated_at timestamptz not null,
    updated_by varchar(100) not null
);

insert into aggregation_settings
    (id,tax_mode,tax_rate,auto_enabled,execution_time,time_zone,version,updated_at,updated_by)
values
    (1,'INCLUSIVE',10.0000,true,'21:00:00','Asia/Tokyo',0,current_timestamp,'system');

create table aggregation_execution (
    id uuid primary key,
    trigger_type varchar(20) not null,
    status varchar(40) not null,
    tax_mode varchar(20) not null,
    tax_rate numeric(9,4) not null,
    settings_version bigint not null,
    requested_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    source_count bigint not null default 0,
    valid_count bigint not null default 0,
    invalid_count bigint not null default 0,
    error_code varchar(100),
    summary varchar(500)
);

create index idx_aggregation_execution_requested on aggregation_execution(requested_at desc);
create index idx_aggregation_execution_status_completed on aggregation_execution(status,completed_at desc);

create table aggregation_row_error (
    id bigserial primary key,
    execution_id uuid not null references aggregation_execution(id) on delete cascade,
    row_number integer not null,
    field varchar(50) not null,
    error_code varchar(100) not null,
    guidance varchar(300) not null
);
create index idx_row_error_execution on aggregation_row_error(execution_id,row_number);

create table aggregation_product_work (
    execution_id uuid not null references aggregation_execution(id) on delete cascade,
    aggregation_key varchar(100) not null,
    amount numeric(30,0) not null,
    primary key(execution_id,aggregation_key)
);

create table aggregation_staff_work (
    execution_id uuid not null references aggregation_execution(id) on delete cascade,
    aggregation_key varchar(100) not null,
    amount numeric(30,0) not null,
    primary key(execution_id,aggregation_key)
);

create table aggregation_monthly_work (
    execution_id uuid not null references aggregation_execution(id) on delete cascade,
    aggregation_key varchar(7) not null,
    amount numeric(30,0) not null,
    primary key(execution_id,aggregation_key)
);
