create table batch_job_instance (
    job_instance_id bigint primary key,
    version bigint,
    job_name varchar(100) not null,
    job_key varchar(32) not null,
    constraint job_inst_un unique (job_name, job_key)
);

create table batch_job_execution (
    job_execution_id bigint primary key,
    version bigint,
    job_instance_id bigint not null references batch_job_instance(job_instance_id),
    create_time timestamp not null,
    start_time timestamp,
    end_time timestamp,
    status varchar(10),
    exit_code varchar(2500),
    exit_message varchar(2500),
    last_updated timestamp
);

create table batch_job_execution_params (
    job_execution_id bigint not null references batch_job_execution(job_execution_id),
    parameter_name varchar(100) not null,
    parameter_type varchar(100) not null,
    parameter_value varchar(2500),
    identifying char(1) not null
);

create table batch_step_execution (
    step_execution_id bigint primary key,
    version bigint not null,
    step_name varchar(100) not null,
    job_execution_id bigint not null references batch_job_execution(job_execution_id),
    create_time timestamp not null,
    start_time timestamp,
    end_time timestamp,
    status varchar(10),
    commit_count bigint,
    read_count bigint,
    filter_count bigint,
    write_count bigint,
    read_skip_count bigint,
    write_skip_count bigint,
    process_skip_count bigint,
    rollback_count bigint,
    exit_code varchar(2500),
    exit_message varchar(2500),
    last_updated timestamp
);

create table batch_step_execution_context (
    step_execution_id bigint primary key references batch_step_execution(step_execution_id),
    short_context varchar(2500) not null,
    serialized_context text
);

create table batch_job_execution_context (
    job_execution_id bigint primary key references batch_job_execution(job_execution_id),
    short_context varchar(2500) not null,
    serialized_context text
);

create sequence batch_step_execution_seq maxvalue 9223372036854775807 no cycle;
create sequence batch_job_execution_seq maxvalue 9223372036854775807 no cycle;
create sequence batch_job_seq maxvalue 9223372036854775807 no cycle;
