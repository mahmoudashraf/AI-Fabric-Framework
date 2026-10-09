create table if not exists ai_workspace_installations (
    id varchar(64) primary key,
    installation_id varchar(96) not null unique,
    customer_id varchar(64) not null,
    consumer_entity_id varchar(64) not null,
    display_name varchar(255) not null,
    status varchar(32) not null,
    experience_pack_code varchar(96) not null,
    experience_pack_version varchar(64) not null,
    connection_mode varchar(64) not null,
    connection_profile_code varchar(96) not null,
    connection_profile_version varchar(64) not null,
    connection_configuration_json text not null,
    allowed_origins_json text not null,
    configuration_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    activated_at timestamp with time zone,
    disabled_at timestamp with time zone,
    row_version bigint not null default 0,
    constraint fk_ai_workspace_installations_customer
        foreign key (customer_id) references platform_customers (id) on delete cascade,
    constraint fk_ai_workspace_installations_consumer
        foreign key (consumer_entity_id) references platform_consumers (id) on delete cascade
);

create index if not exists idx_ai_workspace_installations_customer
    on ai_workspace_installations (customer_id, created_at);

create index if not exists idx_ai_workspace_installations_consumer
    on ai_workspace_installations (consumer_entity_id, status);

create index if not exists idx_ai_workspace_installations_status
    on ai_workspace_installations (status);
