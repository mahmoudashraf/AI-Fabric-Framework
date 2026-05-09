create table if not exists deployment_provider_credentials (
    id varchar(64) primary key,
    name varchar(255) not null,
    provider_type varchar(64) not null,
    secret_ref varchar(255) not null,
    status varchar(64) not null,
    rotated_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_deployment_provider_credentials_provider
    on deployment_provider_credentials (provider_type, status);

create table if not exists deployment_target_profiles (
    id varchar(64) primary key,
    name varchar(255) not null,
    provider_type varchar(64) not null,
    environment_name varchar(64) not null,
    region varchar(64),
    active boolean not null default false,
    default_for_runtime boolean not null default false,
    default_for_restartable_services boolean not null default false,
    platform_services_allowed boolean not null default false,
    source_strategy varchar(64) not null,
    credential_ref_id varchar(64),
    provider_config_json text not null default '{}',
    network_policy_json text not null default '{}',
    resource_defaults_json text not null default '{}',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_deployment_target_profiles_credential
        foreign key (credential_ref_id) references deployment_provider_credentials (id)
);

create index if not exists idx_deployment_target_profiles_provider
    on deployment_target_profiles (provider_type, active, default_for_runtime);

create index if not exists idx_deployment_target_profiles_environment
    on deployment_target_profiles (environment_name, active);

create table if not exists deployment_source_artifacts (
    id varchar(64) primary key,
    service_name varchar(255) not null,
    artifact_type varchar(64) not null,
    image_repository varchar(512),
    image_tag varchar(255),
    image_digest varchar(255),
    git_commit_sha varchar(128),
    build_run_id varchar(255),
    sbom_ref varchar(512),
    promotion_channel varchar(64),
    created_at timestamp with time zone not null,
    promoted_at timestamp with time zone
);

create index if not exists idx_deployment_source_artifacts_service
    on deployment_source_artifacts (service_name, created_at desc);

create table if not exists deployment_provider_resource_handles (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    release_id varchar(64),
    target_profile_id varchar(64) not null,
    provider_type varchar(64) not null,
    resource_kind varchar(64) not null,
    provider_resource_uuid varchar(255) not null,
    provider_project_uuid varchar(255),
    provider_environment_uuid varchar(255),
    provider_server_uuid varchar(255),
    fqdn varchar(512),
    status varchar(64) not null,
    last_observed_status varchar(128),
    last_observed_at timestamp with time zone,
    metadata_json text not null default '{}',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_deployment_provider_resource_handles_deployment
        foreign key (deployment_id) references platform_deployments (id) on delete cascade,
    constraint fk_deployment_provider_resource_handles_release
        foreign key (release_id) references platform_deployment_releases (id) on delete set null,
    constraint fk_deployment_provider_resource_handles_profile
        foreign key (target_profile_id) references deployment_target_profiles (id)
);

create index if not exists idx_deployment_provider_resource_handles_deployment
    on deployment_provider_resource_handles (deployment_id, updated_at desc);

create index if not exists idx_deployment_provider_resource_handles_provider_resource
    on deployment_provider_resource_handles (provider_type, provider_resource_uuid);

alter table platform_deployment_releases
    add column if not exists target_profile_id varchar(64);

alter table platform_deployment_releases
    add column if not exists provider_type varchar(64);

alter table platform_deployment_releases
    add column if not exists source_artifact_id varchar(64);

alter table platform_deployment_releases
    add column if not exists provider_resource_handle_id varchar(64);

create index if not exists idx_platform_deployment_releases_target_profile
    on platform_deployment_releases (target_profile_id, created_at desc);

insert into deployment_target_profiles (
    id,
    name,
    provider_type,
    environment_name,
    region,
    active,
    default_for_runtime,
    default_for_restartable_services,
    platform_services_allowed,
    source_strategy,
    credential_ref_id,
    provider_config_json,
    network_policy_json,
    resource_defaults_json,
    created_at,
    updated_at
)
select
    'dtp-railway-stub-default',
    'Railway Stub Default',
    'RAILWAY_STUB',
    'dev',
    null,
    true,
    true,
    true,
    true,
    'GIT_SOURCE',
    null,
    '{"legacyMode":"RAILWAY_STUB"}',
    '{}',
    '{}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from deployment_target_profiles where id = 'dtp-railway-stub-default'
);

insert into deployment_target_profiles (
    id,
    name,
    provider_type,
    environment_name,
    region,
    active,
    default_for_runtime,
    default_for_restartable_services,
    platform_services_allowed,
    source_strategy,
    credential_ref_id,
    provider_config_json,
    network_policy_json,
    resource_defaults_json,
    created_at,
    updated_at
)
select
    'dtp-railway-api-default',
    'Railway API Default',
    'RAILWAY_API',
    'production',
    null,
    true,
    true,
    true,
    true,
    'GIT_SOURCE',
    null,
    '{"legacyMode":"RAILWAY_API"}',
    '{}',
    '{}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from deployment_target_profiles where id = 'dtp-railway-api-default'
);

insert into deployment_provider_credentials (
    id,
    name,
    provider_type,
    secret_ref,
    status,
    rotated_at,
    created_at,
    updated_at
)
select
    'dpc-coolify-staging',
    'Coolify Staging API',
    'COOLIFY',
    'COOLIFY_STAGING_API_TOKEN',
    'PENDING_SECRET',
    null,
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from deployment_provider_credentials where id = 'dpc-coolify-staging'
);

insert into deployment_provider_credentials (
    id,
    name,
    provider_type,
    secret_ref,
    status,
    rotated_at,
    created_at,
    updated_at
)
select
    'dpc-coolify-production',
    'Coolify Production API',
    'COOLIFY',
    'COOLIFY_PRODUCTION_API_TOKEN',
    'PENDING_SECRET',
    null,
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from deployment_provider_credentials where id = 'dpc-coolify-production'
);

insert into deployment_target_profiles (
    id,
    name,
    provider_type,
    environment_name,
    region,
    active,
    default_for_runtime,
    default_for_restartable_services,
    platform_services_allowed,
    source_strategy,
    credential_ref_id,
    provider_config_json,
    network_policy_json,
    resource_defaults_json,
    created_at,
    updated_at
)
select
    'dtp-coolify-staging',
    'Coolify Staging',
    'COOLIFY',
    'staging',
    'nbg1',
    false,
    false,
    false,
    false,
    'IMAGE_SOURCE',
    'dpc-coolify-staging',
    '{"baseUrl":"http://46.224.145.148:8000","projectUuid":"id069t43frp519u5i3dg2jpr","environmentName":"staging","environmentUuid":"h1433m09ezg882q7xmf3ae0x","serverUuid":"zf25hgk9694bt7q0zwb98ado","destinationUuid":"xjhfu65nacrr30xax5cp0ry7","defaultPublicDomainSuffix":"runtime-staging.loomai.pro","apiVersionPinned":"4.0.0","deploymentPollIntervalSeconds":5,"deploymentTimeoutSeconds":600}',
    '{"dashboardAccess":"IP_ALLOWLIST_PENDING_DNS","publicHttp":true,"publicHttps":true}',
    '{"serverType":"cpx32","host":"coolify-staging-01","publicIpv4":"46.224.145.148","publicIpv6":"2a01:4f8:c2c:83e2::1"}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from deployment_target_profiles where id = 'dtp-coolify-staging'
);

insert into deployment_target_profiles (
    id,
    name,
    provider_type,
    environment_name,
    region,
    active,
    default_for_runtime,
    default_for_restartable_services,
    platform_services_allowed,
    source_strategy,
    credential_ref_id,
    provider_config_json,
    network_policy_json,
    resource_defaults_json,
    created_at,
    updated_at
)
select
    'dtp-coolify-production',
    'Coolify Production',
    'COOLIFY',
    'production',
    'nbg1',
    false,
    false,
    false,
    false,
    'IMAGE_SOURCE',
    'dpc-coolify-production',
    '{"baseUrl":"http://46.225.162.106:8000","projectUuid":"t1400k32bg9yd764chyt1slm","environmentName":"production","environmentUuid":"rn5sbycbix789i973okr9ugm","serverUuid":"kvufjk78dj4wyhjgp1mlxecr","destinationUuid":"r3thf2xmxcjn1tt2bclabebz","defaultPublicDomainSuffix":"runtime.loomai.pro","apiVersionPinned":"4.0.0","deploymentPollIntervalSeconds":5,"deploymentTimeoutSeconds":600}',
    '{"dashboardAccess":"IP_ALLOWLIST_PENDING_DNS","publicHttp":true,"publicHttps":true}',
    '{"serverType":"ccx23","host":"coolify-prod-01","publicIpv4":"46.225.162.106","publicIpv6":"2a01:4f8:1c18:c04::1"}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from deployment_target_profiles where id = 'dtp-coolify-production'
);
