create table if not exists platform_verification_suite_runs (
    id varchar(64) primary key,
    suite_key varchar(64) not null,
    suite_label varchar(255) not null,
    status varchar(32) not null,
    release_blocking boolean not null default true,
    summary_message text not null,
    requested_by_actor_id varchar(128) not null,
    requested_by_role varchar(64) not null,
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone
);

create index if not exists idx_platform_verification_suite_runs_created
    on platform_verification_suite_runs (created_at desc);

create index if not exists idx_platform_verification_suite_runs_status
    on platform_verification_suite_runs (status, created_at desc);

create index if not exists idx_platform_verification_suite_runs_suite
    on platform_verification_suite_runs (suite_key, created_at desc);

create table if not exists platform_verification_suite_run_stages (
    id varchar(64) primary key,
    suite_run_id varchar(64) not null,
    stage_order integer not null,
    stage_key varchar(64) not null,
    stage_label varchar(255) not null,
    stage_type varchar(64) not null,
    target_ref varchar(255),
    blocking boolean not null default true,
    status varchar(32) not null,
    summary_message text not null,
    details_json text not null default '{}',
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    constraint fk_platform_verification_suite_run_stages_run
        foreign key (suite_run_id) references platform_verification_suite_runs (id) on delete cascade
);

create index if not exists idx_platform_verification_suite_run_stages_run
    on platform_verification_suite_run_stages (suite_run_id, stage_order asc);

create index if not exists idx_platform_verification_suite_run_stages_status
    on platform_verification_suite_run_stages (status, created_at desc);
