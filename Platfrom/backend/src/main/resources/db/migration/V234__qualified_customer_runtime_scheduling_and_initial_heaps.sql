-- Retain hard envelopes and explicit administrator overrides; only replace seeded defaults.
UPDATE deployment_target_profiles
SET resource_defaults_json = (
    resource_defaults_json::jsonb
    || CASE WHEN resource_defaults_json::jsonb->>'runtimeJavaOpts' = '-XX:ActiveProcessorCount=1 -Xms256m -Xmx768m'
       THEN jsonb_build_object('runtimeJavaOpts', '-XX:ActiveProcessorCount=1 -Xms64m -Xmx768m') ELSE '{}'::jsonb END
    || CASE WHEN resource_defaults_json::jsonb->>'connectorJavaOpts' = '-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m'
       THEN jsonb_build_object('connectorJavaOpts', '-XX:ActiveProcessorCount=1 -Xms32m -Xmx512m') ELSE '{}'::jsonb END
    || CASE WHEN (id = 'dtp-coolify-staging-behavior' AND resource_defaults_json::jsonb->>'runtimeLimitsCpuSet' = '7')
              OR (id = 'dtp-coolify-production-behavior' AND resource_defaults_json::jsonb->>'runtimeLimitsCpuSet' = '3')
       THEN jsonb_build_object('runtimeLimitsCpuSet', '') ELSE '{}'::jsonb END
    || CASE WHEN (id = 'dtp-coolify-staging-behavior' AND resource_defaults_json::jsonb->>'connectorLimitsCpuSet' = '6')
              OR (id = 'dtp-coolify-production-behavior' AND resource_defaults_json::jsonb->>'connectorLimitsCpuSet' = '2')
       THEN jsonb_build_object('connectorLimitsCpuSet', '') ELSE '{}'::jsonb END
)::text,
updated_at = CURRENT_TIMESTAMP
WHERE id IN ('dtp-coolify-staging-behavior', 'dtp-coolify-production-behavior');
