delete from aggregation_product_work
 where execution_id in (
     select id from aggregation_execution where status not in ('QUEUED', 'RUNNING')
 );

delete from aggregation_staff_work
 where execution_id in (
     select id from aggregation_execution where status not in ('QUEUED', 'RUNNING')
 );

delete from aggregation_monthly_work
 where execution_id in (
     select id from aggregation_execution where status not in ('QUEUED', 'RUNNING')
 );

create index idx_execution_retention
    on aggregation_execution(status, completed_at)
    where completed_at is not null;
