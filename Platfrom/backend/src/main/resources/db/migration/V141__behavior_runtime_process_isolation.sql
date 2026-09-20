update deployment_target_profiles
set resource_defaults_json = (
        coalesce(nullif(resource_defaults_json, ''), '{}')::jsonb
        || jsonb_build_object(
            'runtimeLimitsMemory', '1g',
            'runtimeLimitsMemorySwap', '1g',
            'runtimeLimitsMemoryReservation', '512m',
            'runtimeLimitsCpus', '1.0',
            'runtimeJavaOpts', '-XX:ActiveProcessorCount=1 -Xms256m -Xmx768m'
        )
        || case id
            when 'dtp-coolify-staging-behavior' then jsonb_build_object('runtimeLimitsCpuSet', '7')
            when 'dtp-coolify-production-behavior' then jsonb_build_object('runtimeLimitsCpuSet', '3')
            else '{}'::jsonb
        end
    )::text,
    updated_at = current_timestamp
where id in (
    'dtp-coolify-staging-behavior',
    'dtp-coolify-production-behavior'
);
