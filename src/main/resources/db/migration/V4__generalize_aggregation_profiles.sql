alter table aggregation_settings rename to aggregation_profile;

alter table aggregation_profile
    add column profile_name varchar(100),
    add column spreadsheet_id varchar(255) not null default '',
    add column source_sheet_name varchar(100) not null default '売上データ',
    add column result_sheet_name varchar(100) not null default '集計結果',
    add column error_sheet_name varchar(100) not null default 'エラーログ',
    add column date_column varchar(100) not null default '日付',
    add column staff_column varchar(100) not null default '担当者',
    add column product_column varchar(100) not null default '商品名',
    add column quantity_column varchar(100) not null default '数量',
    add column unit_price_column varchar(100) not null default '単価',
    add column created_at timestamptz;

update aggregation_profile
   set profile_name = case when id = 1 then '既存設定' else '既存設定-' || id end,
       created_at = updated_at
 where profile_name is null or created_at is null;

alter table aggregation_profile
    alter column profile_name set not null,
    alter column created_at set not null;

create sequence aggregation_profile_id_seq;
select setval('aggregation_profile_id_seq', coalesce((select max(id) from aggregation_profile), 1));
alter sequence aggregation_profile_id_seq owned by aggregation_profile.id;
alter table aggregation_profile alter column id set default nextval('aggregation_profile_id_seq');

create unique index ux_aggregation_profile_name
    on aggregation_profile (lower(btrim(profile_name)));
create unique index ux_aggregation_profile_source
    on aggregation_profile (spreadsheet_id, source_sheet_name)
    where btrim(spreadsheet_id) <> '';

alter table aggregation_profile add constraint ck_aggregation_profile_sheet_names_distinct check (
    source_sheet_name <> result_sheet_name
    and source_sheet_name <> error_sheet_name
    and result_sheet_name <> error_sheet_name
);

alter table aggregation_execution
    add column profile_id bigint,
    add column profile_name_snapshot varchar(100),
    add column spreadsheet_id_snapshot varchar(255),
    add column source_sheet_name_snapshot varchar(100),
    add column result_sheet_name_snapshot varchar(100),
    add column error_sheet_name_snapshot varchar(100),
    add column time_zone_snapshot varchar(50),
    add column date_column_snapshot varchar(100),
    add column staff_column_snapshot varchar(100),
    add column product_column_snapshot varchar(100),
    add column quantity_column_snapshot varchar(100),
    add column unit_price_column_snapshot varchar(100);

update aggregation_execution e
   set profile_id = 1,
       profile_name_snapshot = p.profile_name,
       spreadsheet_id_snapshot = p.spreadsheet_id,
       source_sheet_name_snapshot = p.source_sheet_name,
       result_sheet_name_snapshot = p.result_sheet_name,
       error_sheet_name_snapshot = p.error_sheet_name,
       time_zone_snapshot = p.time_zone,
       date_column_snapshot = p.date_column,
       staff_column_snapshot = p.staff_column,
       product_column_snapshot = p.product_column,
       quantity_column_snapshot = p.quantity_column,
       unit_price_column_snapshot = p.unit_price_column
  from aggregation_profile p
 where p.id = 1;

alter table aggregation_execution
    alter column profile_id set not null,
    add constraint fk_aggregation_execution_profile
        foreign key (profile_id) references aggregation_profile(id) on delete restrict;

create index idx_aggregation_execution_profile_requested
    on aggregation_execution(profile_id, requested_at desc);
create index idx_aggregation_execution_profile_status_completed
    on aggregation_execution(profile_id, status, completed_at desc);
