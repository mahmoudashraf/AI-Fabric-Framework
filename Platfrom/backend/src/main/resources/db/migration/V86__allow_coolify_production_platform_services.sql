-- Production Coolify is available for explicit Platform-managed product-service
-- placement, but remains non-default. Services without a targetProfileId keep
-- using the restartable-services default profile.
update deployment_target_profiles
set active = true,
    platform_services_allowed = true,
    default_for_runtime = false,
    default_for_restartable_services = false,
    source_strategy = 'GIT_SOURCE',
    updated_at = current_timestamp
where id = 'dtp-coolify-production';

-- Keep staging as the implicit managed-service target for staging-first work.
update deployment_target_profiles
set active = true,
    platform_services_allowed = true,
    default_for_restartable_services = true,
    updated_at = current_timestamp
where id = 'dtp-coolify-staging';
