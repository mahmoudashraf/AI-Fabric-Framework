create table if not exists platform_public_api_deployments (
    id varchar(64) primary key,
    client_id varchar(128) not null,
    external_deployment_key varchar(255) not null,
    deployment_id varchar(64) not null unique,
    callback_metadata_json text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists uq_platform_public_api_deployments_client_external_key
    on platform_public_api_deployments (client_id, external_deployment_key);
