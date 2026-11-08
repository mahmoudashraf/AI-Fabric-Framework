alter table platform_marketplace_plugins add column owner_customer_id varchar(64);
alter table platform_marketplace_plugins add constraint fk_marketplace_plugin_owner
    foreign key (owner_customer_id) references platform_customers(id);
create index idx_marketplace_plugin_owner on platform_marketplace_plugins(owner_customer_id);

create table platform_plugin_definition_drafts (
    id varchar(64) primary key,
    plugin_id varchar(128) not null unique references platform_marketplace_plugins(id),
    owner_customer_id varchar(64) not null references platform_customers(id),
    name varchar(160) not null,
    plugin_type varchar(32) not null,
    manifest_source text not null,
    revision bigint not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create index idx_plugin_definition_draft_owner on platform_plugin_definition_drafts(owner_customer_id, updated_at desc);
