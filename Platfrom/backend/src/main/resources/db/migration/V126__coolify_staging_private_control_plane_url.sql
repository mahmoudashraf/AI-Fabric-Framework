update deployment_target_profiles
set provider_config_json = jsonb_set(
        provider_config_json::jsonb,
        '{baseUrl}',
        to_jsonb('http://10.44.0.2:8000'::text),
        true
    )::text,
    updated_at = now()
where id = 'dtp-coolify-staging'
  and provider_config_json::jsonb ->> 'baseUrl' = 'http://46.224.145.148:8000';
