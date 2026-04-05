alter table platform_deployments
    add column if not exists deletion_status varchar(64);

alter table platform_deployments
    add column if not exists deletion_operation_id varchar(64);

alter table platform_deployments
    add column if not exists deletion_requested_at timestamp;

alter table platform_deployments
    add column if not exists deletion_failure_message text;

alter table platform_deployment_managed_vector_resources
    add column if not exists deletion_status varchar(64);

alter table platform_deployment_managed_vector_resources
    add column if not exists deletion_operation_id varchar(64);

alter table platform_deployment_managed_vector_resources
    add column if not exists deletion_requested_at timestamp;

create table if not exists platform_deployment_deletion_operations (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    deployment_name varchar(255) not null,
    environment_name varchar(255) not null,
    customer_id varchar(64),
    tenant_id varchar(64),
    status varchar(64) not null,
    hard_delete boolean not null,
    approval_id varchar(64),
    request_reason text,
    requested_by_actor_id varchar(255) not null,
    requested_by_role varchar(64) not null,
    request_details_json text not null,
    result_details_json text not null,
    error_message text,
    created_at timestamp not null,
    started_at timestamp,
    completed_at timestamp,
    updated_at timestamp not null
);

create index if not exists idx_platform_deployment_delete_ops_deployment
    on platform_deployment_deletion_operations (deployment_id, created_at desc);

create index if not exists idx_platform_deployment_delete_ops_status
    on platform_deployment_deletion_operations (status, created_at desc);
