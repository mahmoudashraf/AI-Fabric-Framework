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
    'mkp-template-shopify-companion',
    'shopify-companion-template',
    'Shopify Companion Template',
    'TEMPLATE',
    'loom',
    'Loom AI',
    'Opinionated Shopify Companion template for storefront search, guidance, and shopper-facing assistant UX.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-template-shopify-companion'
);

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
    'mkp-action-shopify-companion-read',
    'shopify-companion-read-actions',
    'Shopify Companion Read Actions',
    'ACTION',
    'loom',
    'Loom AI',
    'Read-first storefront action bundle for product discovery and shopper guidance.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-action-shopify-companion-read'
);

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
    'mkp-data-shopify-catalog',
    'shopify-catalog-data',
    'Shopify Catalog Data',
    'DATA',
    'loom',
    'Loom AI',
    'Shopify Companion catalog knowledge source for product and collection discovery.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-data-shopify-catalog'
);

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
    'mkp-data-shopify-policies',
    'shopify-policies-data',
    'Shopify Policies Data',
    'DATA',
    'loom',
    'Loom AI',
    'Shopify Companion policy and storefront content knowledge source for policy answers and informational pages.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-data-shopify-policies'
);

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
    'mkp-inference-shopify-companion-default',
    'shopify-companion-default-profile',
    'Shopify Companion Default Profile',
    'INFERENCE_PROFILE',
    'loom',
    'Loom AI',
    'Default Shopify Companion inference profile with OpenAI orchestration and bundled local embeddings.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-inference-shopify-companion-default'
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
)
select
    'mkv-template-shopify-companion-v1',
    'mkp-template-shopify-companion',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-template-shopify-companion",
      "version": "1.0.0",
      "pluginType": "TEMPLATE",
      "displayName": "Shopify Companion Template",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["templates", "shellConfig"],
        "supportedDeploymentTargets": [
          "custom-start-from-scratch",
          "dev-openai-lucene",
          "dev-openai-memory",
          "dev-openai-qdrant",
          "dev-openai-pinecone",
          "dev-openai-weaviate",
          "dev-openai-milvus"
        ]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesTemplate": true,
        "contributesShellPresentation": true
      },
      "contributions": {
        "template": {
          "curatedModuleId": "commerce",
          "recommendedPluginIds": [
            "mkp-action-shopify-companion-read",
            "mkp-data-shopify-catalog",
            "mkp-data-shopify-policies",
            "mkp-inference-shopify-companion-default"
          ],
          "shell": {
            "greeting": {
              "title": "Shopify Companion",
              "message": "Ask about products, compare options, and get policy guidance before you buy."
            },
            "enabledModuleIds": ["docs", "products", "ai-search", "actions"],
            "starterPrompts": [
              {
                "id": "find-products",
                "label": "Find products",
                "query": "Help me find the right product for travel",
                "moduleId": "products"
              },
              {
                "id": "compare-products",
                "label": "Compare options",
                "query": "Compare two good travel bags for carry-on use",
                "moduleId": "products"
              },
              {
                "id": "explain-policies",
                "label": "Explain policies",
                "query": "Explain the store refund and shipping policies",
                "moduleId": "docs"
              }
            ],
            "defaultConversationMode": "shopify-companion"
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-template-shopify-companion-v1'
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
)
select
    'mkv-action-shopify-companion-read-v1',
    'mkp-action-shopify-companion-read',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-companion-read",
      "version": "1.0.0",
      "pluginType": "ACTION",
      "displayName": "Shopify Companion Read Actions",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["actions"],
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
          "PUBLIC_RUNTIME_AUTHENTICATED",
          "PUBLIC_RUNTIME_ANONYMOUS"
        ],
        "supportedProviderModes": ["llm:openai"]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesActions": true,
        "contributesShellPresentation": true,
        "requiresExternalHttpExecution": true
      },
      "contributions": {
        "actions": [
          {
            "actionId": "list_products",
            "displayName": "List products",
            "readOnly": true,
            "adapterType": "connector-http",
            "route": {
              "method": "POST",
              "path": "/actions/execute"
            }
          },
          {
            "actionId": "search_products",
            "displayName": "Search products",
            "readOnly": true,
            "adapterType": "connector-http",
            "route": {
              "method": "POST",
              "path": "/actions/execute"
            }
          },
          {
            "actionId": "get_product_details",
            "displayName": "Get product details",
            "readOnly": true,
            "adapterType": "connector-http",
            "route": {
              "method": "POST",
              "path": "/actions/execute"
            }
          }
        ],
        "shell": {
          "moduleRefs": ["actions", "products"]
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-action-shopify-companion-read-v1'
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
)
select
    'mkv-data-shopify-catalog-v1',
    'mkp-data-shopify-catalog',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-data-shopify-catalog",
      "version": "1.0.0",
      "pluginType": "DATA",
      "displayName": "Shopify Catalog Data",
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
          "PUBLIC_RUNTIME_AUTHENTICATED",
          "PUBLIC_RUNTIME_ANONYMOUS"
        ]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesKnowledgeSources": true,
        "contributesShellPresentation": true,
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
            "datasetId": "shopify-catalog",
            "entityType": "product",
            "storageScope": "PLUGIN_SCOPED",
            "sharingScope": "TENANT_SHARED",
            "ingestionMode": "PACKAGED_SEED",
            "updateStrategy": "UPSERT_BY_ID",
            "seedDatasetRef": "classpath:marketplace/datasets/shopify-companion/catalog-placeholder.jsonl"
          }
        ],
        "knowledgeSources": [
          {
            "sourceType": "shared-index",
            "sourceKey": "shopify-catalog",
            "datasetRef": "shopify-catalog",
            "entityType": "product",
            "attributionLabel": "Shopify catalog",
            "authModes": [
              "PLATFORM_PROXY_SESSION",
              "PRIVATE_RUNTIME_BACKEND_MEDIATED",
              "PUBLIC_RUNTIME_AUTHENTICATED",
              "PUBLIC_RUNTIME_ANONYMOUS"
            ]
          }
        ],
        "shell": {
          "moduleRefs": ["docs", "products", "ai-search"]
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-data-shopify-catalog-v1'
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
)
select
    'mkv-data-shopify-policies-v1',
    'mkp-data-shopify-policies',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-data-shopify-policies",
      "version": "1.0.0",
      "pluginType": "DATA",
      "displayName": "Shopify Policies Data",
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
          "PUBLIC_RUNTIME_AUTHENTICATED",
          "PUBLIC_RUNTIME_ANONYMOUS"
        ]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesKnowledgeSources": true,
        "contributesShellPresentation": true,
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
            "datasetId": "shopify-policies",
            "entityType": "support-policy",
            "storageScope": "PLUGIN_SCOPED",
            "sharingScope": "TENANT_SHARED",
            "ingestionMode": "PACKAGED_SEED",
            "updateStrategy": "UPSERT_BY_ID",
            "seedDatasetRef": "classpath:marketplace/datasets/shopify-companion/policies-placeholder.jsonl"
          }
        ],
        "knowledgeSources": [
          {
            "sourceType": "shared-index",
            "sourceKey": "shopify-policies",
            "datasetRef": "shopify-policies",
            "entityType": "support-policy",
            "attributionLabel": "Shopify store policies and pages",
            "authModes": [
              "PLATFORM_PROXY_SESSION",
              "PRIVATE_RUNTIME_BACKEND_MEDIATED",
              "PUBLIC_RUNTIME_AUTHENTICATED",
              "PUBLIC_RUNTIME_ANONYMOUS"
            ]
          }
        ],
        "shell": {
          "moduleRefs": ["docs", "ai-search"]
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-data-shopify-policies-v1'
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
)
select
    'mkv-inference-shopify-companion-default-v1',
    'mkp-inference-shopify-companion-default',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-inference-shopify-companion-default",
      "version": "1.0.0",
      "pluginType": "INFERENCE_PROFILE",
      "displayName": "Shopify Companion Default Profile",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["providers"],
        "supportedDeploymentTargets": [
          "custom-start-from-scratch",
          "dev-openai-lucene",
          "dev-openai-memory",
          "dev-openai-qdrant",
          "dev-openai-pinecone",
          "dev-openai-weaviate",
          "dev-openai-milvus"
        ],
        "supportedProviderModes": ["llm:openai", "embedding:onnx"]
      },
      "pricing": {
        "pricingModel": "FREE"
      },
      "permissions": {
        "contributesProviders": true
      },
      "contributions": {
        "inferenceProfile": {
          "profileId": "shopify-companion-default",
          "orchestration": {
            "provider": "openai",
            "endpointProfileRef": "openai-cloud-orchestration",
            "model": "gpt-4.1-mini",
            "maxTokens": 700,
            "temperature": 0.1,
            "timeout": 30
          },
          "generation": {
            "provider": "openai",
            "endpointProfileRef": "openai-cloud-default",
            "model": "gpt-4.1-mini",
            "maxTokens": 1800,
            "temperature": 0.3,
            "timeout": 60
          },
          "embedding": {
            "provider": "onnx",
            "endpointProfileRef": "onnx-bundled",
            "modelAlias": "bge-small-en-v1.5",
            "maxSequenceLength": 512,
            "useGpu": false
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-inference-shopify-companion-default-v1'
);
