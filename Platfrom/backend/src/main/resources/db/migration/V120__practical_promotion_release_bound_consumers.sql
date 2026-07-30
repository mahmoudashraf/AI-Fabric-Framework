alter table platform_consumers
    add column if not exists bound_release_id varchar(64);

alter table platform_consumers
    add column if not exists bound_target_profile_id varchar(64);

alter table platform_consumers
    drop constraint if exists platform_consumers_bound_deployment_id_key;

create index if not exists idx_platform_consumers_bound_deployment
    on platform_consumers (bound_deployment_id);

create index if not exists idx_platform_consumers_bound_release
    on platform_consumers (bound_release_id);

create index if not exists idx_platform_consumers_bound_target_profile
    on platform_consumers (bound_target_profile_id);

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
    'dtp-coolify-prod-staging',
    'Coolify Production Server Customer Staging',
    'COOLIFY',
    'staging',
    region,
    true,
    false,
    false,
    true,
    source_strategy,
    'dpc-coolify-production',
    '{"baseUrl":"http://46.225.162.106:8000","projectUuid":"t1400k32bg9yd764chyt1slm","environmentName":"staging","serverUuid":"kvufjk78dj4wyhjgp1mlxecr","destinationUuid":"r3thf2xmxcjn1tt2bclabebz","defaultPublicDomainSuffix":"runtime-staging.loomai.pro","apiVersionPinned":"4.0.0","deploymentPollIntervalSeconds":5,"deploymentTimeoutSeconds":600}',
    network_policy_json,
    '{"environmentIntent":"CUSTOMER_STAGING","customerProjectGroupingEnabled":true,"customerProjectEnvironmentName":"staging","runtimeDatabaseMode":"COOLIFY_POSTGRES","runtimeDatabaseNamePrefix":"ai-fabric-runtime-postgres","runtimeDatabaseName":"runtime_chat","runtimeDatabaseUsername":"runtime_user","runtimeDatabaseImage":"postgres:16-alpine","runtimeDatabasePort":"5432","runtimeDatabasePublic":false}',
    current_timestamp,
    current_timestamp
from deployment_target_profiles
where id = 'dtp-coolify-production'
  and not exists (
      select 1 from deployment_target_profiles where id = 'dtp-coolify-prod-staging'
  );
