update deployment_target_profiles
set provider_config_json = jsonb_set(
        provider_config_json::jsonb,
        '{defaultPublicDomainSuffix}',
        to_jsonb('46.225.162.106.sslip.io'::text),
        true
    )::text,
    network_policy_json = '{"dashboardAccess":"IP_ALLOWLIST_PENDING_DNS","publicHttp":true,"publicHttps":true,"temporaryRuntimeDns":"sslip.io"}',
    updated_at = current_timestamp
where id = 'dtp-coolify-prod-staging'
  and provider_config_json::jsonb ->> 'defaultPublicDomainSuffix' = 'runtime-staging.loomai.pro';
