-- Temporary runtime domains while loomai.pro DNS automation is intentionally skipped.
-- Replace these suffixes with runtime-staging/runtime.loomai.pro after DNS credentials or delegation are available.
update deployment_target_profiles
set provider_config_json = replace(
        provider_config_json,
        '"defaultPublicDomainSuffix":"runtime-staging.loomai.pro"',
        '"defaultPublicDomainSuffix":"46.224.145.148.sslip.io"'
    ),
    network_policy_json = '{"dashboardAccess":"IP_ALLOWLIST_PENDING_DNS","publicHttp":true,"publicHttps":true,"temporaryRuntimeDns":"sslip.io"}',
    updated_at = current_timestamp
where id = 'dtp-coolify-staging'
  and provider_config_json like '%"defaultPublicDomainSuffix":"runtime-staging.loomai.pro"%';

update deployment_target_profiles
set provider_config_json = replace(
        provider_config_json,
        '"defaultPublicDomainSuffix":"runtime.loomai.pro"',
        '"defaultPublicDomainSuffix":"46.225.162.106.sslip.io"'
    ),
    network_policy_json = '{"dashboardAccess":"IP_ALLOWLIST_PENDING_DNS","publicHttp":true,"publicHttps":true,"temporaryRuntimeDns":"sslip.io"}',
    updated_at = current_timestamp
where id = 'dtp-coolify-production'
  and provider_config_json like '%"defaultPublicDomainSuffix":"runtime.loomai.pro"%';
