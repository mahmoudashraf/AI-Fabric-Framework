create table if not exists shopify_customer_account_sessions (
    id varchar(64) primary key,
    shop_domain varchar(255) not null,
    shopper_session_id_hash varchar(128) not null,
    token_endpoint varchar(1024),
    access_token_ciphertext text not null,
    refresh_token_ciphertext text,
    id_token_ciphertext text,
    token_type varchar(64),
    scopes_text text,
    access_token_expires_at timestamp with time zone,
    refresh_token_expires_at timestamp with time zone,
    session_expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists idx_shopify_customer_account_sessions_shop_session
    on shopify_customer_account_sessions(shop_domain, shopper_session_id_hash);

create index if not exists idx_shopify_customer_account_sessions_expiry
    on shopify_customer_account_sessions(session_expires_at);
