create table if not exists shopify_bridge_query_insights_daily (
    id varchar(32) primary key,
    shop_domain varchar(255) not null,
    usage_date date not null,
    surface_id varchar(80) not null,
    event_type varchar(80) not null,
    query_key varchar(200) not null,
    sample_query varchar(200) not null,
    query_count bigint not null default 1,
    last_queried_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index if not exists uk_shopify_bridge_query_insights_daily
    on shopify_bridge_query_insights_daily(shop_domain, usage_date, surface_id, event_type, query_key);

create index if not exists idx_shopify_bridge_query_insights_daily_shop_date
    on shopify_bridge_query_insights_daily(shop_domain, usage_date);

create index if not exists idx_shopify_bridge_query_insights_daily_shop_surface
    on shopify_bridge_query_insights_daily(shop_domain, surface_id, usage_date);
