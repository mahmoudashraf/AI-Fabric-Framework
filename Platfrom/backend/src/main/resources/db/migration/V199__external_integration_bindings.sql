-- Metadata and immutable credential references only. No provider/business-resource lifecycle.
create table deployment_external_integration_approvals (
    id varchar(64) primary key,
    deployment_id varchar(64) not null references platform_deployments(id),
    version_id varchar(64) not null references platform_deployment_versions(id),
    target_profile_id varchar(64) not null references deployment_target_profiles(id),
    operation_key varchar(128) not null,
    evidence_json text not null,
    integrity_mac varchar(64) not null,
    created_at timestamptz not null default current_timestamp,
    unique (deployment_id, operation_key)
);
create table deployment_external_integration_bindings (
    id varchar(64) primary key,
    approval_id varchar(64) not null references deployment_external_integration_approvals(id),
    deployment_id varchar(64) not null references platform_deployments(id),
    version_id varchar(64) not null references platform_deployment_versions(id),
    target_profile_id varchar(64) not null references deployment_target_profiles(id),
    slot_id varchar(128) not null,
    evidence_json text not null,
    integrity_mac varchar(64) not null,
    status varchar(32) not null check (status in ('CONFIGURED', 'REVOKED')),
    revision bigint not null check (revision >= 0),
    created_at timestamptz not null default current_timestamp,
    unique (deployment_id, version_id, target_profile_id, slot_id)
);
create table deployment_external_integration_snapshots (
    release_id varchar(64) primary key references platform_deployment_releases(id),
    deployment_id varchar(64) not null references platform_deployments(id),
    version_id varchar(64) not null references platform_deployment_versions(id),
    target_profile_id varchar(64) not null references deployment_target_profiles(id),
    evidence_json text not null,
    integrity_mac varchar(64) not null,
    created_at timestamptz not null default current_timestamp
);
alter table platform_customer_entitlement_commands drop constraint ck_customer_entitlement_command_type;
alter table platform_customer_entitlement_commands add constraint ck_customer_entitlement_command_type check (
    command_type in ('INVITE','ACCEPT','REVOKE','BOOTSTRAP_CONFIG','BIND_S3','REVOKE_S3','PUBLISH_VERSION',
                     'APPLY_STAGING_VERSION','CREATE_STAGING_CONSUMER','BIND_EXTERNAL_SERVICE','REVOKE_EXTERNAL_SERVICE','BIND_AI_STATE')
);
