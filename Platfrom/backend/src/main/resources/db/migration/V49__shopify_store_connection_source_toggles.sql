alter table shopify_store_connections
    add column if not exists onboarding_status varchar(64) not null default 'NOT_STARTED';

alter table shopify_store_connections
    add column if not exists products_enabled boolean not null default true;

alter table shopify_store_connections
    add column if not exists collections_enabled boolean not null default true;

alter table shopify_store_connections
    add column if not exists pages_enabled boolean not null default true;

alter table shopify_store_connections
    add column if not exists policies_enabled boolean not null default true;
