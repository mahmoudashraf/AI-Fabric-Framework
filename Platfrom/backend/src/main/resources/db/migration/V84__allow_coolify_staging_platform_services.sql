-- Permit platform-managed product services only on the Coolify staging target.
-- Production remains operator-explicit and cannot host platform service lifecycle
-- operations until the production gates are intentionally opened.
update deployment_target_profiles
set platform_services_allowed = true,
    updated_at = current_timestamp
where id = 'dtp-coolify-staging';

update deployment_target_profiles
set platform_services_allowed = false,
    updated_at = current_timestamp
where id = 'dtp-coolify-production';
