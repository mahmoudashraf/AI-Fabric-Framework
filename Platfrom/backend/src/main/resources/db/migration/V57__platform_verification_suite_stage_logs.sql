alter table if exists platform_verification_suite_run_stages
    add column if not exists log_output text not null default '';
