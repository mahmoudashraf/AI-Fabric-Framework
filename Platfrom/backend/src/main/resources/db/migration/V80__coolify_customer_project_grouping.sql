-- Coolify grouping is operational UI structure only. Platform remains the
-- deployment source of truth and customers do not receive Coolify access.
update deployment_target_profiles
set resource_defaults_json = replace(
        resource_defaults_json,
        '"healthCheckPort":"8080"}',
        '"healthCheckPort":"8080","customerProjectGroupingEnabled":true,"customerProjectNamePrefix":"customer"}'
    ),
    updated_at = current_timestamp
where provider_type = 'COOLIFY'
  and resource_defaults_json not like '%customerProjectGroupingEnabled%';
