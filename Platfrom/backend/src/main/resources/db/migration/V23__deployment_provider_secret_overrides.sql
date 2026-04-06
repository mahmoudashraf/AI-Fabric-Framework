alter table platform_secrets
    add column if not exists scope_type varchar(64);

alter table platform_secrets
    add column if not exists deployment_id varchar(64);

alter table platform_secrets
    add column if not exists secret_purpose varchar(128);

alter table platform_secrets
    add column if not exists owner_type varchar(64);

alter table platform_secrets
    add column if not exists managed_by_platform boolean not null default false;

alter table platform_secrets
    add column if not exists cleanup_policy varchar(64);

create table if not exists platform_deployment_provider_secret_bindings (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    secret_purpose varchar(128) not null,
    binding_mode varchar(64) not null,
    secret_name varchar(255),
    secondary_secret_name varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_platform_deployment_provider_secret_bindings_unique
    on platform_deployment_provider_secret_bindings (deployment_id, secret_purpose);

create index if not exists idx_platform_deployment_provider_secret_bindings_deployment
    on platform_deployment_provider_secret_bindings (deployment_id, updated_at desc);

update platform_secrets
set scope_type = 'DEPLOYMENT_MANAGED',
    owner_type = 'PLATFORM_MANAGED',
    managed_by_platform = true,
    cleanup_policy = coalesce(cleanup_policy, 'DELETE_ON_HARD_DELETE')
where scope_type is null
  and name like 'MANAGED\_%' escape '\';

update platform_secrets
set scope_type = 'GLOBAL_PLATFORM',
    owner_type = 'PLATFORM',
    managed_by_platform = false,
    cleanup_policy = coalesce(cleanup_policy, 'KEEP')
where scope_type is null;

update platform_secrets
set secret_purpose = case
    when name like 'MANAGED_PINECONE_API_KEY_DEP_%' then 'PINECONE_API_KEY'
    when name like 'MANAGED_QDRANT_DB_API_KEY_DEP_%' then 'QDRANT_API_KEY'
    when name like 'MANAGED_MILVUS_USERNAME_DEP_%' then 'MILVUS_RUNTIME_CREDENTIALS'
    when name like 'MANAGED_MILVUS_PASSWORD_DEP_%' then 'MILVUS_RUNTIME_CREDENTIALS'
    when name like 'MANAGED_VECTORIZATION_RUNNER_TOKEN_DEP_%' then 'VECTORIZATION_RUNNER_REGISTRATION_TOKEN'
    else coalesce(secret_purpose, name)
end
where secret_purpose is null;
