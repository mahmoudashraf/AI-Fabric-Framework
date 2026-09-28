update deployment_target_profiles
set provider_config_json = jsonb_set(
        coalesce(nullif(provider_config_json, ''), '{}')::jsonb,
        '{forceHttps}',
        'true'::jsonb,
        true
    )::text,
    updated_at = current_timestamp
where id in (
    'dtp-coolify-staging',
    'dtp-coolify-production',
    'dtp-coolify-prod-staging',
    'dtp-coolify-staging-behavior',
    'dtp-coolify-production-behavior'
);
