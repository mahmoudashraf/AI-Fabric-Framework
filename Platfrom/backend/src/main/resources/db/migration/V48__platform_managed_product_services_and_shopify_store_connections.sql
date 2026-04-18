create table if not exists platform_managed_product_services (
    id varchar(64) primary key,
    service_ref varchar(128) not null unique,
    display_name varchar(255) not null,
    product_family varchar(64) not null,
    service_kind varchar(128) not null,
    deployment_mode varchar(64) not null,
    tenant_mode varchar(64) not null,
    environment_scope varchar(64),
    deployment_id varchar(64),
    railway_project_id varchar(128),
    railway_environment_id varchar(128),
    railway_service_id varchar(128),
    desired_replicas integer,
    actual_replicas integer,
    min_replicas integer,
    max_replicas integer,
    base_url text,
    private_network_url text,
    health_path varchar(255),
    service_root text,
    dockerfile_path text,
    secret_name varchar(255),
    status varchar(64) not null,
    details_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_platform_managed_product_services_kind
    on platform_managed_product_services(service_kind);

create index if not exists idx_platform_managed_product_services_environment
    on platform_managed_product_services(environment_scope);

create table if not exists shopify_store_connections (
    id varchar(64) primary key,
    shop_domain varchar(255) not null unique,
    display_name varchar(255),
    product_service_id varchar(64) not null,
    customer_id varchar(64),
    deployment_id varchar(64),
    consumer_id varchar(128),
    install_status varchar(64) not null,
    sync_status varchar(64) not null,
    source_readiness_status varchar(64) not null,
    widget_status varchar(64) not null,
    last_source_preflight_at timestamp,
    last_sync_at timestamp,
    last_webhook_at timestamp,
    details_json text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_shopify_store_connections_product_service
        foreign key (product_service_id) references platform_managed_product_services(id)
);

create index if not exists idx_shopify_store_connections_service
    on shopify_store_connections(product_service_id);

create index if not exists idx_shopify_store_connections_customer
    on shopify_store_connections(customer_id);

create index if not exists idx_shopify_store_connections_deployment
    on shopify_store_connections(deployment_id);

create index if not exists idx_shopify_store_connections_consumer
    on shopify_store_connections(consumer_id);
