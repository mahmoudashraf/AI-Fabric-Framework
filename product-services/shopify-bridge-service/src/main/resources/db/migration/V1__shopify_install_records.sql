create table if not exists shopify_install_record (
    id varchar(64) primary key,
    shop_domain varchar(255) not null unique,
    status varchar(64) not null,
    shop_url varchar(512),
    user_id varchar(255),
    app_bridge_host varchar(1024),
    access_token_secret_ref varchar(255),
    scopes_text text,
    installed_at timestamp,
    last_authenticated_at timestamp,
    last_uninstalled_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_shopify_install_record_status
    on shopify_install_record(status);
