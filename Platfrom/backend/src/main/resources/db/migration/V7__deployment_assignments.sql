create table if not exists platform_deployment_assignments (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    user_id varchar(64) not null,
    assignment_role varchar(64) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_platform_deployment_assignments_deployment
        foreign key (deployment_id) references platform_deployments (id) on delete cascade,
    constraint fk_platform_deployment_assignments_user
        foreign key (user_id) references platform_users (id) on delete cascade,
    constraint uq_platform_deployment_assignments_user_deployment
        unique (deployment_id, user_id)
);

create index if not exists idx_platform_deployment_assignments_user
    on platform_deployment_assignments (user_id);

create index if not exists idx_platform_deployment_assignments_deployment
    on platform_deployment_assignments (deployment_id);
