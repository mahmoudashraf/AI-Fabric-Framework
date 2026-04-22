create table if not exists platform_consumers (
    id varchar(64) primary key,
    customer_id varchar(64) not null,
    consumer_id varchar(128) not null unique,
    display_name varchar(255) not null,
    description varchar(1000),
    status varchar(64) not null,
    bound_deployment_id varchar(64) unique,
    last_bound_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_consumers_customer
        foreign key (customer_id) references platform_customers (id) on delete cascade,
    constraint fk_platform_consumers_bound_deployment
        foreign key (bound_deployment_id) references platform_deployments (id)
);

create index if not exists idx_platform_consumers_customer
    on platform_consumers (customer_id);

create index if not exists idx_platform_consumers_status
    on platform_consumers (status);

create table if not exists platform_consumer_binding_history (
    id varchar(64) primary key,
    consumer_entity_id varchar(64) not null,
    consumer_id varchar(128) not null,
    customer_id varchar(64) not null,
    from_deployment_id varchar(64),
    to_deployment_id varchar(64),
    reason varchar(1000),
    actor_id varchar(255) not null,
    actor_role varchar(64) not null,
    created_at timestamp with time zone not null,
    constraint fk_platform_consumer_binding_history_consumer
        foreign key (consumer_entity_id) references platform_consumers (id) on delete cascade,
    constraint fk_platform_consumer_binding_history_customer
        foreign key (customer_id) references platform_customers (id) on delete cascade
);

create index if not exists idx_platform_consumer_binding_history_consumer
    on platform_consumer_binding_history (consumer_entity_id, created_at desc);
