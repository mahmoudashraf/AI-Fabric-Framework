-- Customer self-service targets must carry reviewed PostgreSQL container capacity.
-- These limits are part of the target profile fingerprint and therefore of admission evidence.
update deployment_target_profiles
set resource_defaults_json = (
        coalesce(nullif(resource_defaults_json, ''), '{}')::jsonb
        || jsonb_build_object(
            'runtimeDatabaseCpuMillicores', 250,
            'runtimeDatabaseMemoryMiB', 512
        )
    )::text,
    updated_at = current_timestamp
where id in (
    'dtp-coolify-staging-behavior',
    'dtp-coolify-production-behavior'
);
