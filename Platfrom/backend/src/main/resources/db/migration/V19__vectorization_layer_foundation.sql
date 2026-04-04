create table if not exists platform_vectorization_plans (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    status varchar(64) not null,
    runner_mode varchar(64) not null,
    sync_state varchar(64) not null,
    sync_reason_codes_json text not null,
    sync_reason_details_json text not null,
    source_connection_id varchar(64),
    active_revision_id varchar(64),
    active_indexed_output_hash varchar(128),
    last_successful_indexed_output_hash varchar(128),
    last_run_id varchar(64),
    last_successful_run_id varchar(64),
    manual_confirmation_note text,
    manual_confirmation_actor_id varchar(255),
    manual_confirmation_hash varchar(128),
    manually_confirmed_at timestamp,
    deferred_reindex_at timestamp,
    deferred_reindex_note text,
    deferred_reindex_hash varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_platform_vectorization_plans_deployment
    on platform_vectorization_plans (deployment_id);

create index if not exists idx_platform_vectorization_plans_customer
    on platform_vectorization_plans (customer_id);

create table if not exists platform_vectorization_source_connections (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    adapter_type varchar(64) not null,
    auth_mode varchar(64) not null,
    status varchar(64) not null,
    connection_config_json text not null,
    secret_references_json text not null,
    discovery_summary_json text not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_platform_vectorization_connections_deployment
    on platform_vectorization_source_connections (deployment_id);

create table if not exists platform_vectorization_plan_revisions (
    id varchar(64) primary key,
    plan_id varchar(64) not null,
    deployment_id varchar(64) not null,
    revision_number integer not null,
    status varchar(64) not null,
    source_connection_id varchar(64),
    entity_scope_json text not null,
    mapping_config_json text not null,
    execution_config_json text not null,
    indexed_output_hash varchar(128),
    created_by_actor_id varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_platform_vectorization_revision_plan_number
    on platform_vectorization_plan_revisions (plan_id, revision_number);

create index if not exists idx_platform_vectorization_revision_deployment
    on platform_vectorization_plan_revisions (deployment_id);

create table if not exists platform_vectorization_runner_registrations (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    runner_mode varchar(64) not null,
    status varchar(64) not null,
    token_hash varchar(128) not null,
    token_hint varchar(32),
    token_expires_at timestamp,
    last_connected_at timestamp,
    runner_instance_id varchar(255),
    product_version varchar(128),
    compatibility_version varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_platform_vectorization_runner_registration_deployment
    on platform_vectorization_runner_registrations (deployment_id);

create index if not exists idx_platform_vectorization_runner_registration_token_hash
    on platform_vectorization_runner_registrations (token_hash);

create table if not exists platform_vectorization_runner_sessions (
    id varchar(64) primary key,
    registration_id varchar(64) not null,
    deployment_id varchar(64) not null,
    status varchar(64) not null,
    session_token_hash varchar(128) not null,
    runner_instance_id varchar(255) not null,
    product_version varchar(128),
    compatibility_version varchar(128),
    run_id varchar(64),
    lease_expires_at timestamp,
    expires_at timestamp not null,
    last_heartbeat_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_platform_vectorization_runner_session_token_hash
    on platform_vectorization_runner_sessions (session_token_hash);

create index if not exists idx_platform_vectorization_runner_session_registration
    on platform_vectorization_runner_sessions (registration_id);

create table if not exists platform_vectorization_runs (
    id varchar(64) primary key,
    plan_id varchar(64) not null,
    plan_revision_id varchar(64) not null,
    deployment_id varchar(64) not null,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    reason varchar(64) not null,
    requested_status varchar(64) not null,
    status varchar(64) not null,
    runner_mode varchar(64) not null,
    entity_scope_json text not null,
    progress_summary_json text not null,
    checkpoint_summary_json text not null,
    error_summary_json text not null,
    claimed_by_registration_id varchar(64),
    claimed_by_session_id varchar(64),
    runner_instance_id varchar(255),
    product_version varchar(128),
    compatibility_version varchar(128),
    lease_expires_at timestamp,
    requested_by_actor_id varchar(255),
    request_note text,
    created_at timestamp not null,
    started_at timestamp,
    completed_at timestamp,
    updated_at timestamp not null
);

create index if not exists idx_platform_vectorization_runs_deployment
    on platform_vectorization_runs (deployment_id, created_at desc);

create index if not exists idx_platform_vectorization_runs_status
    on platform_vectorization_runs (deployment_id, requested_status, status, created_at asc);

create table if not exists platform_vectorization_checkpoints (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    entity_type varchar(255),
    checkpoint_type varchar(64) not null,
    checkpoint_value varchar(1000),
    progress_json text not null,
    details_json text not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_platform_vectorization_checkpoints_run
    on platform_vectorization_checkpoints (run_id, updated_at desc);

create table if not exists platform_vectorization_failure_buckets (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    entity_type varchar(255),
    error_code varchar(128) not null,
    summary varchar(1000) not null,
    sample_json text not null,
    occurrences integer not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_platform_vectorization_failure_buckets_run
    on platform_vectorization_failure_buckets (run_id, updated_at desc);

alter table platform_vectorization_plans
    add constraint fk_platform_vectorization_plans_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_plans
    add constraint fk_platform_vectorization_plans_customer
        foreign key (customer_id) references platform_customers (id);

alter table platform_vectorization_plans
    add constraint fk_platform_vectorization_plans_tenant
        foreign key (tenant_id) references platform_customer_tenants (id);

alter table platform_vectorization_source_connections
    add constraint fk_platform_vectorization_connections_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_source_connections
    add constraint fk_platform_vectorization_connections_customer
        foreign key (customer_id) references platform_customers (id);

alter table platform_vectorization_source_connections
    add constraint fk_platform_vectorization_connections_tenant
        foreign key (tenant_id) references platform_customer_tenants (id);

alter table platform_vectorization_plan_revisions
    add constraint fk_platform_vectorization_revisions_plan
        foreign key (plan_id) references platform_vectorization_plans (id);

alter table platform_vectorization_plan_revisions
    add constraint fk_platform_vectorization_revisions_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_runner_registrations
    add constraint fk_platform_vectorization_runner_registration_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_runner_registrations
    add constraint fk_platform_vectorization_runner_registration_customer
        foreign key (customer_id) references platform_customers (id);

alter table platform_vectorization_runner_registrations
    add constraint fk_platform_vectorization_runner_registration_tenant
        foreign key (tenant_id) references platform_customer_tenants (id);

alter table platform_vectorization_runner_sessions
    add constraint fk_platform_vectorization_runner_sessions_registration
        foreign key (registration_id) references platform_vectorization_runner_registrations (id);

alter table platform_vectorization_runner_sessions
    add constraint fk_platform_vectorization_runner_sessions_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_runs
    add constraint fk_platform_vectorization_runs_plan
        foreign key (plan_id) references platform_vectorization_plans (id);

alter table platform_vectorization_runs
    add constraint fk_platform_vectorization_runs_revision
        foreign key (plan_revision_id) references platform_vectorization_plan_revisions (id);

alter table platform_vectorization_runs
    add constraint fk_platform_vectorization_runs_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_runs
    add constraint fk_platform_vectorization_runs_customer
        foreign key (customer_id) references platform_customers (id);

alter table platform_vectorization_runs
    add constraint fk_platform_vectorization_runs_tenant
        foreign key (tenant_id) references platform_customer_tenants (id);

alter table platform_vectorization_checkpoints
    add constraint fk_platform_vectorization_checkpoints_run
        foreign key (run_id) references platform_vectorization_runs (id);

alter table platform_vectorization_failure_buckets
    add constraint fk_platform_vectorization_failure_buckets_run
        foreign key (run_id) references platform_vectorization_runs (id);
