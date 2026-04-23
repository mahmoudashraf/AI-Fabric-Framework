create table if not exists shopify_bridge_governed_action_audits (
    id varchar(32) primary key,
    shop_domain varchar(255) not null,
    shopper_session_id varchar(120) not null,
    action_type varchar(80) not null,
    action_package varchar(80) not null,
    surface_id varchar(80) not null,
    page_type varchar(80) not null,
    product_id varchar(80),
    product_handle varchar(255),
    product_title varchar(255),
    variant_id varchar(80),
    requested_quantity integer,
    target_quantity integer,
    resulting_quantity integer,
    cart_line_key varchar(255),
    confirmation_required boolean not null default false,
    confirmation_accepted boolean not null default false,
    status varchar(40) not null,
    message varchar(500),
    expires_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_shopify_bridge_governed_action_audits_shop_created
    on shopify_bridge_governed_action_audits(shop_domain, created_at desc);

create index if not exists idx_shopify_bridge_governed_action_audits_session_created
    on shopify_bridge_governed_action_audits(shopper_session_id, created_at desc);
