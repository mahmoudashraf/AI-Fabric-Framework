create table if not exists shopify_customer_account_auth_claims (
    id varchar(80) primary key,
    shop_domain varchar(255) not null,
    source_shopper_session_id_hash varchar(128) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create index if not exists idx_shopify_customer_account_auth_claims_shop_expiry
    on shopify_customer_account_auth_claims(shop_domain, expires_at);

create index if not exists idx_shopify_customer_account_auth_claims_consumed
    on shopify_customer_account_auth_claims(consumed_at);
