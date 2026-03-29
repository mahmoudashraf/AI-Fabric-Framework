alter table platform_deployments
    add column if not exists archived_at timestamp with time zone;
