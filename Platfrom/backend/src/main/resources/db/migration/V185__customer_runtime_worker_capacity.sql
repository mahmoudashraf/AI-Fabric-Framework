-- Productized customer deployments require a complete enforced application envelope.
-- Keep each Java service inside its reviewed CPU/memory allocation and isolate the
-- three runtime processes on separate host CPUs. The database disk value is the
-- reviewed logical allocation used by admission; Coolify enforces its CPU/RAM here.
update deployment_target_profiles
set resource_defaults_json = (
        coalesce(nullif(resource_defaults_json, ''), '{}')::jsonb
        || jsonb_build_object(
            'connectorLimitsMemory', '768m',
            'connectorLimitsMemorySwap', '768m',
            'connectorLimitsMemoryReservation', '384m',
            'connectorLimitsCpus', '0.5',
            'connectorJavaOpts', '-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m',
            'vectorizationRunnerLimitsMemory', '768m',
            'vectorizationRunnerLimitsMemorySwap', '768m',
            'vectorizationRunnerLimitsMemoryReservation', '384m',
            'vectorizationRunnerLimitsCpus', '0.5',
            'vectorizationRunnerJavaOpts', '-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m',
            'runtimeDatabaseDiskMiB', 2048
        )
        || case id
            when 'dtp-coolify-staging-behavior' then jsonb_build_object(
                'connectorLimitsCpuSet', '6',
                'vectorizationRunnerLimitsCpuSet', '5'
            )
            when 'dtp-coolify-production-behavior' then jsonb_build_object(
                'connectorLimitsCpuSet', '2',
                'vectorizationRunnerLimitsCpuSet', '1'
            )
            else '{}'::jsonb
        end
    )::text,
    updated_at = current_timestamp
where id in (
    'dtp-coolify-staging-behavior',
    'dtp-coolify-production-behavior'
);
