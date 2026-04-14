create table platform_marketplace_plugins (
    id varchar(64) primary key,
    slug varchar(128) not null,
    display_name varchar(255) not null,
    plugin_type varchar(64) not null,
    publisher_slug varchar(128) not null,
    publisher_display_name varchar(255) not null,
    short_description text not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uk_platform_marketplace_plugins_slug
    on platform_marketplace_plugins (slug);

create table platform_marketplace_plugin_versions (
    id varchar(64) primary key,
    plugin_id varchar(64) not null,
    version varchar(64) not null,
    release_channel varchar(64) not null,
    status varchar(64) not null,
    manifest_json text not null,
    created_at timestamp with time zone not null,
    published_at timestamp with time zone not null,
    constraint fk_platform_marketplace_plugin_versions_plugin
        foreign key (plugin_id) references platform_marketplace_plugins (id) on delete cascade
);

create unique index uk_platform_marketplace_plugin_versions_plugin_version
    on platform_marketplace_plugin_versions (plugin_id, version);

create index idx_platform_marketplace_plugin_versions_plugin_published
    on platform_marketplace_plugin_versions (plugin_id, published_at desc);

insert into platform_marketplace_plugins (
    id,
    slug,
    display_name,
    plugin_type,
    publisher_slug,
    publisher_display_name,
    short_description,
    status,
    created_at,
    updated_at
) values
    (
        'mkp-template-commerce-shell',
        'loom-commerce-shell',
        'Loom Commerce Shell',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Commerce-oriented assistant shell starter with branded shell defaults, prompt baselines, and first-party module layout.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-action-shopify-admin',
        'shopify-admin-actions',
        'Shopify Admin Actions',
        'ACTION',
        'loom',
        'Loom AI',
        'First-party read and write Shopify admin actions packaged for deployment-level install and release-safe action composition.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-data-commerce-catalog',
        'commerce-catalog-data',
        'Commerce Catalog Data',
        'DATA',
        'loom',
        'Loom AI',
        'Shared commerce catalog and policy search source intended for marketplace-style retrieval composition in the orchestrator.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    );

insert into platform_marketplace_plugin_versions (
    id,
    plugin_id,
    version,
    release_channel,
    status,
    manifest_json,
    created_at,
    published_at
) values
    (
        'mkv-template-commerce-shell-v1',
        'mkp-template-commerce-shell',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginType": "TEMPLATE",
          "contributions": {
            "template": {
              "curatedModuleId": "commerce",
              "shell": {
                "enabledModuleIds": ["docs", "products", "ai-search", "actions"],
                "defaultConversationMode": "guided-commerce"
              }
            }
          }
        }',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-action-shopify-admin-v1',
        'mkp-action-shopify-admin',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginType": "ACTION",
          "contributions": {
            "actions": [
              {
                "actionId": "shopify-order-read",
                "readOnly": true
              },
              {
                "actionId": "shopify-order-cancel",
                "readOnly": false,
                "confirmationRequired": true
              }
            ],
            "shell": {
              "moduleRefs": ["actions"]
            }
          }
        }',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-data-commerce-catalog-v1',
        'mkp-data-commerce-catalog',
        '1.0.0',
        'GA',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginType": "DATA",
          "contributions": {
            "knowledgeSources": [
              {
                "sourceType": "shared-index",
                "sourceKey": "commerce-catalog"
              }
            ],
            "shell": {
              "moduleRefs": ["docs", "products", "ai-search"]
            }
          }
        }',
        current_timestamp,
        current_timestamp
    );
