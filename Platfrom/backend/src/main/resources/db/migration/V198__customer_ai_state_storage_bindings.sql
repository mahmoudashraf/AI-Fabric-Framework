-- A retained connection owns neither the PostgreSQL server, database nor schemas.
-- One physical pair of AI schemas belongs to one deployment/target; version approvals are append-only.
create unique index uq_active_external_ai_state_store
    on deployment_provider_resource_handles(provider_resource_uuid)
    where resource_kind = 'EXTERNAL_AI_STATE_STORAGE_BINDING' and status = 'ACTIVE';

create unique index uq_external_ai_state_target
    on deployment_provider_resource_handles(deployment_id, target_profile_id)
    where resource_kind = 'EXTERNAL_AI_STATE_STORAGE_BINDING';

create table deployment_ai_state_storage_approvals (
    binding_id varchar(64) not null references deployment_provider_resource_handles(id) on delete cascade,
    version_id varchar(64) not null references platform_deployment_versions(id) on delete cascade,
    config_hash varchar(128) not null,
    binding_fingerprint varchar(128) not null,
    approval_reference varchar(255) not null,
    approved_at timestamp with time zone not null,
    primary key (binding_id, version_id)
);

create table deployment_ai_state_schema_claims (
    schema_identity varchar(128) primary key,
    -- A retained schema may still contain AI state after control-plane deletion. Do not silently release its claim.
    binding_id varchar(64) not null,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    deployment_id varchar(64) not null,
    target_profile_id varchar(64) not null
);

alter table platform_deployment_releases add column ai_state_storage_snapshot_json text;
alter table platform_deployment_releases add column ai_state_storage_fingerprint varchar(64);
