create table if not exists platform_deployment_managed_vector_resources (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    deployment_version_id varchar(64),
    deployment_release_id varchar(64),
    vendor varchar(64) not null,
    vector_strategy varchar(64) not null,
    vector_provisioning_mode varchar(64) not null,
    managed_mode varchar(64) not null,
    resource_type varchar(64) not null,
    resource_name varchar(255) not null,
    resource_reference text,
    endpoint text,
    resource_status varchar(32) not null,
    provisioning_state varchar(32),
    secret_reference_names_json text not null default '[]',
    details_json text not null default '{}',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_managed_vector_resources_deployment
        foreign key (deployment_id) references platform_deployments (id) on delete cascade
);

create unique index if not exists uq_platform_managed_vector_resources_target
    on platform_deployment_managed_vector_resources (deployment_id, vendor, resource_type, resource_name);

create index if not exists idx_platform_managed_vector_resources_deployment
    on platform_deployment_managed_vector_resources (deployment_id, updated_at desc);

create index if not exists idx_platform_managed_vector_resources_status
    on platform_deployment_managed_vector_resources (resource_status);
