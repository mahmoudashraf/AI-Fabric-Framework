update deployment_target_profiles
set provider_config_json = jsonb_set(
        provider_config_json::jsonb,
        '{deploymentTimeoutSeconds}',
        '1200'::jsonb,
        true
    )::text,
    updated_at = now()
where provider_type = 'COOLIFY'
  and coalesce((provider_config_json::jsonb ->> 'deploymentTimeoutSeconds')::int, 0) < 1200;
