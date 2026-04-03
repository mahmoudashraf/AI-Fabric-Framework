alter table platform_deployments
    add column if not exists approval_required_for_apply boolean not null default false;

alter table platform_deployments
    add column if not exists approval_required_for_delete boolean not null default false;

create table if not exists platform_deployment_operation_approvals (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    operation_type varchar(64) not null,
    target_version_id varchar(64),
    status varchar(32) not null,
    requested_by_actor_id varchar(255) not null,
    requested_by_display_name varchar(255),
    requested_reason text not null,
    approved_by_actor_id varchar(255),
    approved_by_display_name varchar(255),
    resolution_note text,
    created_at timestamp not null,
    updated_at timestamp not null,
    approved_at timestamp,
    rejected_at timestamp,
    expires_at timestamp,
    consumed_at timestamp,
    constraint fk_platform_deployment_operation_approvals_deployment
        foreign key (deployment_id) references platform_deployments (id) on delete cascade
);

create index if not exists idx_platform_deployment_operation_approvals_deployment
    on platform_deployment_operation_approvals (deployment_id);

create index if not exists idx_platform_deployment_operation_approvals_requested_by
    on platform_deployment_operation_approvals (requested_by_actor_id);

create index if not exists idx_platform_deployment_operation_approvals_status
    on platform_deployment_operation_approvals (status);
