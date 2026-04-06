create table if not exists platform_tenant_scoped_vector_resources (
    id varchar(64) primary key,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    deployment_id varchar(64),
    deployment_version_id varchar(64),
    deployment_release_id varchar(64),
    vendor varchar(128) not null,
    vector_strategy varchar(128) not null,
    vector_provisioning_mode varchar(128) not null,
    vector_storage_posture varchar(128) not null,
    resource_status varchar(64) not null,
    scope_type varchar(128) not null,
    registry_key varchar(512) not null,
    root_resource_label varchar(128),
    root_resource_value varchar(512),
    scope_prefix varchar(512),
    tenant_handle varchar(255),
    scope_pattern varchar(512),
    lifecycle_owner varchar(128),
    details_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_tenant_vector_resources_customer
        foreign key (customer_id) references platform_customers (id),
    constraint fk_platform_tenant_vector_resources_tenant
        foreign key (tenant_id) references platform_customer_tenants (id),
    constraint uq_platform_tenant_vector_resources_tenant_registry_key
        unique (tenant_id, registry_key)
);

create index if not exists idx_platform_tenant_vector_resources_deployment
    on platform_tenant_scoped_vector_resources (deployment_id);

create index if not exists idx_platform_tenant_vector_resources_tenant_updated
    on platform_tenant_scoped_vector_resources (tenant_id, updated_at desc);
