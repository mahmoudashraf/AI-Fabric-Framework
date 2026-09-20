update deployment_target_profiles
set resource_defaults_json = (
        coalesce(nullif(resource_defaults_json, ''), '{}')::jsonb
        || jsonb_build_object(
            'runtimeLimitsCpus', '1.5',
            'runtimeHealthCheckIntervalSeconds', 10,
            'runtimeHealthCheckTimeoutSeconds', 5,
            'runtimeHealthCheckRetries', 18,
            'runtimeHealthCheckStartPeriodSeconds', 180
        )
    )::text,
    updated_at = current_timestamp
where id in (
    'dtp-coolify-staging-behavior',
    'dtp-coolify-production-behavior'
);
