update deployment_target_profiles
set provider_config_json = jsonb_set(
        provider_config_json::jsonb,
        '{apiVersionPinned}',
        to_jsonb('4.1.1'::text),
        true
    )::text,
    updated_at = current_timestamp
where id in ('dtp-coolify-production', 'dtp-coolify-prod-staging')
  and provider_config_json::jsonb ->> 'apiVersionPinned' = '4.0.0';
