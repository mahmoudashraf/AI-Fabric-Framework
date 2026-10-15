alter table deployment_source_artifacts
    add column if not exists artifact_role varchar(64) not null default 'RUNTIME';

update deployment_source_artifacts
set artifact_role = 'RUNTIME'
where artifact_role is null or trim(artifact_role) = '';

create index if not exists idx_deployment_source_artifacts_role_service
    on deployment_source_artifacts (artifact_role, service_name, created_at desc);
