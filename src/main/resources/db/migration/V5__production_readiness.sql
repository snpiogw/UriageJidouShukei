alter table aggregation_profile
    add column active boolean not null default true;

create table aggregation_execution_attempt (
    id bigserial primary key,
    execution_id uuid not null references aggregation_execution(id) on delete cascade,
    attempt_number integer not null check (attempt_number > 0),
    status varchar(40) not null,
    requested_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    error_code varchar(100),
    summary varchar(500),
    unique (execution_id, attempt_number)
);

insert into aggregation_execution_attempt
    (execution_id, attempt_number, status, requested_at, started_at, completed_at, error_code, summary)
select id, 1, status, requested_at, started_at, completed_at, error_code, summary
  from aggregation_execution;

create index idx_execution_attempt_execution
    on aggregation_execution_attempt(execution_id, attempt_number desc);
