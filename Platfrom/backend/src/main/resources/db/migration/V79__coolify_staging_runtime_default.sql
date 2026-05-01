-- Make Coolify staging the default runtime/restartable target after live staging
-- runtime plus connector verification passed. Production is active for explicit
-- operator targeting only; it remains non-default until DNS/access/backup gates
-- are complete.
update deployment_target_profiles
set default_for_runtime = false,
    default_for_restartable_services = false,
    updated_at = current_timestamp
where default_for_runtime = true
   or default_for_restartable_services = true;

update deployment_target_profiles
set active = true,
    default_for_runtime = true,
    default_for_restartable_services = true,
    updated_at = current_timestamp
where id = 'dtp-coolify-staging';

update deployment_target_profiles
set active = true,
    default_for_runtime = false,
    default_for_restartable_services = false,
    updated_at = current_timestamp
where id = 'dtp-coolify-production';
