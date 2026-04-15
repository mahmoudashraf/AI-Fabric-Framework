alter table platform_deployment_drafts
    add column if not exists marketplace_dataset_config_json text not null
        default '{"contractVersion":"MARKETPLACE_DATASET_CONFIG_V1","datasets":[]}';

alter table platform_deployment_versions
    add column if not exists marketplace_dataset_config_json text not null
        default '{"contractVersion":"MARKETPLACE_DATASET_CONFIG_V1","datasets":[]}';

create table if not exists platform_marketplace_plugin_datasets (
    id varchar(64) primary key,
    plugin_id varchar(64) not null,
    plugin_version_id varchar(64) not null,
    dataset_id varchar(128) not null,
    entity_type varchar(128) not null,
    storage_scope varchar(64) not null,
    sharing_scope varchar(64) not null,
    ingestion_mode varchar(64) not null,
    update_strategy varchar(64) not null,
    vectorization_profile varchar(128),
    handle_template varchar(256),
    seed_dataset_ref varchar(512),
    connector_type varchar(64),
    connector_config_json text not null,
    dataset_hash varchar(128) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_marketplace_plugin_dataset unique (plugin_version_id, dataset_id)
);

create table if not exists platform_marketplace_dataset_handles (
    id varchar(64) primary key,
    plugin_id varchar(64) not null,
    plugin_version_id varchar(64) not null,
    dataset_id varchar(128) not null,
    deployment_id varchar(64) not null,
    customer_id varchar(128) not null,
    tenant_id varchar(128) not null,
    storage_scope varchar(64) not null,
    sharing_scope varchar(64) not null,
    handle_ref varchar(256) not null,
    entity_type varchar(128) not null,
    status varchar(64) not null,
    dataset_hash varchar(128) not null,
    last_sync_at timestamp,
    last_error text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_marketplace_dataset_handle unique (plugin_id, tenant_id, dataset_id)
);

create table if not exists platform_marketplace_dataset_sync_runs (
    id varchar(64) primary key,
    dataset_handle_id varchar(64) not null,
    release_id varchar(64) not null,
    deployment_id varchar(64) not null,
    sync_type varchar(64) not null,
    status varchar(64) not null,
    document_count integer not null,
    details_json text,
    started_at timestamp,
    completed_at timestamp,
    error_message text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists platform_marketplace_demo_sql_documents (
    id varchar(128) primary key,
    entity_type varchar(64) not null,
    scope varchar(64) not null,
    title varchar(256) not null,
    content text not null,
    metadata_json text not null default '{}',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

insert into platform_marketplace_demo_sql_documents (id, entity_type, scope, title, content, metadata_json, created_at, updated_at)
select
    'catalog-alienware-m18-r2',
    'product',
    'catalog',
    'Alienware m18 R2',
    'Alienware m18 R2 is a high-performance gaming laptop with a large 18-inch display, premium cooling, and configuration options aimed at enthusiast buyers who need top-end graphics and CPU performance.',
    '{"category":"gaming-laptop","brand":"Alienware"}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from platform_marketplace_demo_sql_documents
    where id = 'catalog-alienware-m18-r2'
);

insert into platform_marketplace_demo_sql_documents (id, entity_type, scope, title, content, metadata_json, created_at, updated_at)
select
    'catalog-refund-policy',
    'product',
    'refund-policy',
    'Refund Policy',
    'Refund policy: customers can return unopened hardware within 30 days of delivery. Opened items may be eligible for exchange or store credit when they are defective or materially not as described.',
    '{"documentType":"policy","policyTopic":"refund"}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from platform_marketplace_demo_sql_documents
    where id = 'catalog-refund-policy'
);

insert into platform_marketplace_demo_sql_documents (id, entity_type, scope, title, content, metadata_json, created_at, updated_at)
select
    'catalog-gaming-buying-guide',
    'product',
    'catalog',
    'Gaming Laptop Buying Guide',
    'Choose a gaming laptop by balancing GPU tier, sustained cooling, display refresh rate, and power delivery. Premium models trade battery life for thermal headroom and stronger frame stability under load.',
    '{"documentType":"guide","category":"gaming-laptop"}',
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from platform_marketplace_demo_sql_documents
    where id = 'catalog-gaming-buying-guide'
);

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-data-commerce-catalog",
  "version": "1.0.0",
  "pluginType": "DATA",
  "displayName": "Commerce Catalog Data",
  "capabilityProfiles": ["SURFACE"],
  "compatibility": {
    "minPlatformVersion": "0.1.0",
    "requiredCapabilities": ["knowledgeSources", "shellConfig"],
    "supportedDeploymentTargets": [
      "custom-start-from-scratch",
      "dev-openai-lucene",
      "dev-openai-memory",
      "dev-openai-qdrant",
      "dev-openai-pinecone",
      "dev-openai-weaviate",
      "dev-openai-milvus"
    ],
    "supportedAuthModes": [
      "PLATFORM_PROXY_SESSION",
      "PRIVATE_RUNTIME_BACKEND_MEDIATED",
      "PUBLIC_RUNTIME_AUTHENTICATED"
    ]
  },
  "pricing": {
    "pricingModel": "SUBSCRIPTION",
    "amount": 29.00,
    "currency": "USD",
    "billingInterval": "MONTHLY",
    "trialDays": 7
  },
  "installForm": [
    {
      "id": "scope",
      "label": "Source scope",
      "type": "select",
      "required": true,
      "options": ["refund-policy", "catalog", "all"]
    }
  ],
  "permissions": {
    "contributesKnowledgeSources": true,
    "contributesShellPresentation": true,
    "contributesSurfaceCapabilities": true,
    "requiresSharedDatasetAccess": true
  },
  "contributions": {
    "entityConfig": {
      "ai-entities": {
        "product": {
          "features": ["embedding", "search"],
          "auto-process": false,
          "enable-search": true,
          "auto-embedding": true,
          "indexable": true
        }
      }
    },
    "datasets": [
      {
        "datasetId": "commerce-catalog-sql",
        "entityType": "product",
        "storageScope": "PLUGIN_SCOPED",
        "sharingScope": "TENANT_SHARED",
        "ingestionMode": "EXTERNAL_SYNC_SQL",
        "updateStrategy": "UPSERT_BY_ID",
        "syncConnector": {
          "connectorType": "SQL_QUERY",
          "connectionRef": "platform-marketplace-demo-sql",
          "query": "select id, scope, title, content, metadata_json from platform_marketplace_demo_sql_documents order by id"
        }
      }
    ],
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "commerce-catalog",
        "datasetRef": "commerce-catalog-sql",
        "entityType": "product",
        "attributionLabel": "Commerce catalog marketplace data",
        "authModes": [
          "PLATFORM_PROXY_SESSION",
          "PRIVATE_RUNTIME_BACKEND_MEDIATED",
          "PUBLIC_RUNTIME_AUTHENTICATED"
        ]
      }
    ],
    "shell": {
      "moduleRefs": ["docs", "products", "ai-search"]
    }
  }
}'
where id = 'mkv-data-commerce-catalog-v1';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-data-help-center",
  "version": "1.0.0",
  "pluginType": "DATA",
  "displayName": "Help Center Data",
  "capabilityProfiles": ["SURFACE"],
  "compatibility": {
    "minPlatformVersion": "0.1.0",
    "requiredCapabilities": ["knowledgeSources", "shellConfig"],
    "supportedDeploymentTargets": [
      "custom-start-from-scratch",
      "dev-openai-lucene",
      "dev-openai-memory",
      "dev-openai-qdrant",
      "dev-openai-pinecone",
      "dev-openai-weaviate",
      "dev-openai-milvus"
    ],
    "supportedAuthModes": [
      "PLATFORM_PROXY_SESSION",
      "PRIVATE_RUNTIME_BACKEND_MEDIATED",
      "PUBLIC_RUNTIME_AUTHENTICATED"
    ]
  },
  "pricing": {
    "pricingModel": "FREE"
  },
  "installForm": [
    {
      "id": "scope",
      "label": "Help center scope",
      "type": "select",
      "required": true,
      "options": ["faq", "policy", "all"]
    }
  ],
  "permissions": {
    "contributesKnowledgeSources": true,
    "contributesShellPresentation": true,
    "contributesSurfaceCapabilities": true,
    "requiresSharedDatasetAccess": true
  },
  "contributions": {
    "entityConfig": {
      "ai-entities": {
        "faq-article": {
          "entity-type": "faq-article",
          "auto-embedding": true,
          "indexable": true,
          "enable-search": true
        }
      }
    },
    "datasets": [
      {
        "datasetId": "help-center-seed",
        "entityType": "faq-article",
        "storageScope": "PLUGIN_SCOPED",
        "sharingScope": "TENANT_SHARED",
        "ingestionMode": "PACKAGED_SEED",
        "updateStrategy": "UPSERT_BY_ID",
        "seedDatasetRef": "classpath:marketplace/datasets/help-center/help-center.jsonl"
      }
    ],
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "help-center",
        "datasetRef": "help-center-seed",
        "entityType": "faq-article",
        "attributionLabel": "Help center marketplace data",
        "authModes": [
          "PLATFORM_PROXY_SESSION",
          "PRIVATE_RUNTIME_BACKEND_MEDIATED",
          "PUBLIC_RUNTIME_AUTHENTICATED"
        ]
      }
    ],
    "shell": {
      "moduleRefs": ["docs", "ai-search", "support"]
    }
  }
}'
where id = 'mkv-data-help-center-v1';

update platform_marketplace_plugins
set slug = 'policy-folder-data',
    display_name = 'Policy Folder Data',
    plugin_type = 'DATA',
    publisher_slug = 'loom',
    publisher_display_name = 'Loom AI',
    short_description = 'Folder-synced policy knowledge source packaged as a plugin-scoped shared dataset.',
    status = 'ACTIVE',
    updated_at = current_timestamp
where id = 'mkp-data-policy-folder';

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
)
select
    'mkp-data-policy-folder',
    'policy-folder-data',
    'Policy Folder Data',
    'DATA',
    'loom',
    'Loom AI',
    'Folder-synced policy knowledge source packaged as a plugin-scoped shared dataset.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from platform_marketplace_plugins
    where id = 'mkp-data-policy-folder'
);

update platform_marketplace_plugin_versions
set manifest_json = '{
      "schemaVersion": 1,
      "pluginId": "mkp-data-policy-folder",
      "version": "1.0.0",
      "pluginType": "DATA",
      "displayName": "Policy Folder Data",
      "capabilityProfiles": ["SURFACE"],
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["knowledgeSources", "shellConfig"],
        "supportedDeploymentTargets": [
          "custom-start-from-scratch",
          "dev-openai-lucene",
          "dev-openai-memory",
          "dev-openai-qdrant",
          "dev-openai-pinecone",
          "dev-openai-weaviate",
          "dev-openai-milvus"
        ],
        "supportedAuthModes": [
          "PLATFORM_PROXY_SESSION",
          "PRIVATE_RUNTIME_BACKEND_MEDIATED",
          "PUBLIC_RUNTIME_AUTHENTICATED"
        ]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesKnowledgeSources": true,
        "contributesShellPresentation": true,
        "contributesSurfaceCapabilities": true,
        "requiresSharedDatasetAccess": true
      },
      "contributions": {
        "entityConfig": {
          "ai-entities": {
            "support-policy": {
              "entity-type": "support-policy",
              "auto-embedding": true,
              "indexable": true,
              "enable-search": true
            }
          }
        },
        "datasets": [
          {
            "datasetId": "policy-folder-pack",
            "entityType": "support-policy",
            "storageScope": "PLUGIN_SCOPED",
            "sharingScope": "TENANT_SHARED",
            "ingestionMode": "EXTERNAL_SYNC_FOLDER",
            "updateStrategy": "UPSERT_BY_ID",
            "syncConnector": {
              "connectorType": "FILE_FOLDER",
              "folderRef": "classpath*:marketplace/folders/policy-pack/*.md"
            }
          }
        ],
        "knowledgeSources": [
          {
            "sourceType": "shared-index",
            "sourceKey": "policy-folder",
            "datasetRef": "policy-folder-pack",
            "entityType": "support-policy",
            "attributionLabel": "Policy folder marketplace data",
            "authModes": [
              "PLATFORM_PROXY_SESSION",
              "PRIVATE_RUNTIME_BACKEND_MEDIATED",
              "PUBLIC_RUNTIME_AUTHENTICATED"
            ]
          }
        ],
        "shell": {
          "moduleRefs": ["docs", "ai-search", "support"]
        }
      }
    }',
    reviewed_at = current_timestamp,
    review_notes = 'Seeded first-party marketplace folder data plugin.',
    bundle_sha256 = 'seeded-data-policy-folder-v1',
    published_at = current_timestamp
where id = 'mkv-data-policy-folder-v1';

insert into platform_marketplace_plugin_versions (
    id,
    plugin_id,
    version,
    release_channel,
    status,
    manifest_json,
    submitted_by_publisher_id,
    submitted_by_actor_id,
    reviewed_by_actor_id,
    reviewed_at,
    review_notes,
    bundle_sha256,
    created_at,
    published_at
)
select
    'mkv-data-policy-folder-v1',
    'mkp-data-policy-folder',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-data-policy-folder",
      "version": "1.0.0",
      "pluginType": "DATA",
      "displayName": "Policy Folder Data",
      "capabilityProfiles": ["SURFACE"],
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["knowledgeSources", "shellConfig"],
        "supportedDeploymentTargets": [
          "custom-start-from-scratch",
          "dev-openai-lucene",
          "dev-openai-memory",
          "dev-openai-qdrant",
          "dev-openai-pinecone",
          "dev-openai-weaviate",
          "dev-openai-milvus"
        ],
        "supportedAuthModes": [
          "PLATFORM_PROXY_SESSION",
          "PRIVATE_RUNTIME_BACKEND_MEDIATED",
          "PUBLIC_RUNTIME_AUTHENTICATED"
        ]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesKnowledgeSources": true,
        "contributesShellPresentation": true,
        "contributesSurfaceCapabilities": true,
        "requiresSharedDatasetAccess": true
      },
      "contributions": {
        "entityConfig": {
          "ai-entities": {
            "support-policy": {
              "entity-type": "support-policy",
              "auto-embedding": true,
              "indexable": true,
              "enable-search": true
            }
          }
        },
        "datasets": [
          {
            "datasetId": "policy-folder-pack",
            "entityType": "support-policy",
            "storageScope": "PLUGIN_SCOPED",
            "sharingScope": "TENANT_SHARED",
            "ingestionMode": "EXTERNAL_SYNC_FOLDER",
            "updateStrategy": "UPSERT_BY_ID",
            "syncConnector": {
              "connectorType": "FILE_FOLDER",
              "folderRef": "classpath*:marketplace/folders/policy-pack/*.md"
            }
          }
        ],
        "knowledgeSources": [
          {
            "sourceType": "shared-index",
            "sourceKey": "policy-folder",
            "datasetRef": "policy-folder-pack",
            "entityType": "support-policy",
            "attributionLabel": "Policy folder marketplace data",
            "authModes": [
              "PLATFORM_PROXY_SESSION",
              "PRIVATE_RUNTIME_BACKEND_MEDIATED",
              "PUBLIC_RUNTIME_AUTHENTICATED"
            ]
          }
        ],
        "shell": {
          "moduleRefs": ["docs", "ai-search", "support"]
        }
      }
    }',
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Seeded first-party marketplace folder data plugin.',
    'seeded-data-policy-folder-v1',
    current_timestamp,
    current_timestamp
where not exists (
    select 1
    from platform_marketplace_plugin_versions
    where id = 'mkv-data-policy-folder-v1'
);
