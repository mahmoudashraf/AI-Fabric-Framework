-- Keep existing Coolify target profiles on Git source, but remove
-- provider-specific Dockerfile paths from future runtime provisioning.
update deployment_target_profiles
set resource_defaults_json = replace(
        resource_defaults_json,
        'ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile',
        'ai-infrastructure-module/ai-fabric-runtime/Dockerfile'
    ),
    updated_at = current_timestamp
where id in ('dtp-coolify-staging', 'dtp-coolify-production')
  and resource_defaults_json like '%ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile%';
