create table shopify_bridge_usage_daily (
    id varchar(64) primary key,
    shop_domain varchar(255) not null,
    usage_date date not null,
    event_type varchar(120) not null,
    event_count bigint not null,
    last_event_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index ux_shopify_bridge_usage_daily_shop_date_event
    on shopify_bridge_usage_daily (shop_domain, usage_date, event_type);

create index idx_shopify_bridge_usage_daily_shop_date
    on shopify_bridge_usage_daily (shop_domain, usage_date);
