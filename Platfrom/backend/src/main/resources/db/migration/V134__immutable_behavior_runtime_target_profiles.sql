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
    'dtp-coolify-staging-behavior',
    'Coolify Staging Immutable Behavior Runtime',
    provider_type,
    'staging',
    region,
    true,
    false,
    false,
    false,
    'IMAGE_SOURCE',
    credential_ref_id,
    provider_config_json,
    network_policy_json,
    '{"sourceStrategy":"IMAGE_SOURCE","serviceName":"ai-fabric-runtime","promotionChannel":"staging","portsExposes":"8080","healthCheckPath":"/actuator/health/liveness","healthCheckPort":"8080","customerProjectGroupingEnabled":true,"customerProjectNamePrefix":"customer","runtimeDatabaseMode":"COOLIFY_POSTGRES","runtimeDatabaseNamePrefix":"ai-fabric-runtime-postgres","runtimeDatabaseName":"runtime_chat","runtimeDatabaseUsername":"runtime_user","runtimeDatabaseImage":"postgres:16-alpine","runtimeDatabasePort":"5432","runtimeDatabasePublic":false}',
    current_timestamp,
    current_timestamp
from deployment_target_profiles
where id = 'dtp-coolify-staging'
  and not exists (
      select 1
      from deployment_target_profiles
      where id = 'dtp-coolify-staging-behavior'
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
    'dtp-coolify-production-behavior',
    'Coolify Production Immutable Behavior Runtime',
    provider_type,
    'production',
    region,
    true,
    false,
    false,
    false,
    'IMAGE_SOURCE',
    credential_ref_id,
    provider_config_json,
    network_policy_json,
    '{"sourceStrategy":"IMAGE_SOURCE","serviceName":"ai-fabric-runtime","promotionChannel":"production","portsExposes":"8080","healthCheckPath":"/actuator/health/liveness","healthCheckPort":"8080","customerProjectGroupingEnabled":true,"customerProjectNamePrefix":"customer","runtimeDatabaseMode":"COOLIFY_POSTGRES","runtimeDatabaseNamePrefix":"ai-fabric-runtime-postgres","runtimeDatabaseName":"runtime_chat","runtimeDatabaseUsername":"runtime_user","runtimeDatabaseImage":"postgres:16-alpine","runtimeDatabasePort":"5432","runtimeDatabasePublic":false}',
    current_timestamp,
    current_timestamp
from deployment_target_profiles
where id = 'dtp-coolify-production'
  and not exists (
      select 1
      from deployment_target_profiles
      where id = 'dtp-coolify-production-behavior'
  );
