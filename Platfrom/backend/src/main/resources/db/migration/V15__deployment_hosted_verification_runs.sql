create table if not exists platform_deployment_hosted_verification_runs (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    release_id varchar(64) not null,
    deployment_version_id varchar(64) not null,
    verification_profile varchar(32) not null,
    runner_type varchar(32) not null,
    script_path varchar(255) not null,
    status varchar(32) not null,
    verify_write boolean not null default false,
    summary_message text not null,
    log_output text not null default '',
    exit_code integer,
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    constraint fk_platform_hosted_verification_runs_deployment
        foreign key (deployment_id) references platform_deployments (id) on delete cascade
);

create index if not exists idx_platform_hosted_verification_runs_deployment
    on platform_deployment_hosted_verification_runs (deployment_id, created_at desc);

create index if not exists idx_platform_hosted_verification_runs_status
    on platform_deployment_hosted_verification_runs (status, created_at desc);
