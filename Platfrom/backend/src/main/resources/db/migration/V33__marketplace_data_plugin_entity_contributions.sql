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
        },
        "policy": {
          "features": ["embedding", "search"],
          "auto-process": false,
          "enable-search": true,
          "auto-embedding": true,
          "indexable": true
        },
        "review": {
          "features": ["embedding", "search"],
          "auto-process": false,
          "enable-search": true,
          "auto-embedding": true,
          "indexable": true
        }
      }
    },
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "commerce-catalog",
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
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "help-center",
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
