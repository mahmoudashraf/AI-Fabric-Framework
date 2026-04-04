create table if not exists platform_vectorization_verification_runs (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    verification_type varchar(64) not null,
    execution_mode varchar(64) not null,
    status varchar(64) not null,
    entity_scope_json text not null,
    summary_json text not null,
    linked_vectorization_run_id varchar(64),
    requested_by_actor_id varchar(255),
    request_note text,
    created_at timestamp not null,
    started_at timestamp,
    completed_at timestamp,
    updated_at timestamp not null
);

create index if not exists idx_platform_vectorization_verification_runs_deployment
    on platform_vectorization_verification_runs (deployment_id, created_at desc);

create table if not exists platform_vectorization_verification_steps (
    id varchar(64) primary key,
    verification_run_id varchar(64) not null,
    step_key varchar(128) not null,
    step_name varchar(255) not null,
    status varchar(64) not null,
    details_json text not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_platform_vectorization_verification_steps_run
    on platform_vectorization_verification_steps (verification_run_id, created_at asc);

create unique index if not exists idx_platform_vectorization_verification_steps_run_key
    on platform_vectorization_verification_steps (verification_run_id, step_key);

alter table platform_vectorization_verification_runs
    add constraint fk_platform_vectorization_verification_runs_deployment
        foreign key (deployment_id) references platform_deployments (id);

alter table platform_vectorization_verification_runs
    add constraint fk_platform_vectorization_verification_runs_customer
        foreign key (customer_id) references platform_customers (id);

alter table platform_vectorization_verification_runs
    add constraint fk_platform_vectorization_verification_runs_tenant
        foreign key (tenant_id) references platform_customer_tenants (id);

alter table platform_vectorization_verification_runs
    add constraint fk_platform_vectorization_verification_runs_linked_run
        foreign key (linked_vectorization_run_id) references platform_vectorization_runs (id);

alter table platform_vectorization_verification_steps
    add constraint fk_platform_vectorization_verification_steps_run
        foreign key (verification_run_id) references platform_vectorization_verification_runs (id);
