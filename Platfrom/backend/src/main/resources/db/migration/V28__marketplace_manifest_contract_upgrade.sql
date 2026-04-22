update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-template-commerce-shell",
  "version": "1.0.0",
  "pluginType": "TEMPLATE",
  "displayName": "Loom Commerce Shell",
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
    ],
    "supportedProviderModes": ["llm:openai"]
  },
  "permissions": {
    "contributesTemplate": true,
    "contributesShellPresentation": true
  },
  "contributions": {
    "template": {
      "curatedModuleId": "commerce",
      "recommendedPluginIds": ["mkp-action-shopify-admin", "mkp-data-commerce-catalog"],
      "shell": {
        "enabledModuleIds": ["docs", "products", "ai-search", "actions"],
        "defaultConversationMode": "guided-commerce"
      }
    }
  }
}'
where id = 'mkv-template-commerce-shell-v1';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-admin",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Shopify Admin Actions",
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
    "supportedAuthModes": ["PLATFORM_PROXY_SESSION", "PRIVATE_RUNTIME_BACKEND_MEDIATED"],
    "supportedProviderModes": ["llm:openai"]
  },
  "installForm": [
    {
      "id": "store",
      "label": "Store id",
      "type": "text",
      "required": true,
      "description": "Logical store identifier used by the action bundle."
    },
    {
      "id": "apiKey",
      "label": "Shopify admin secret ref",
      "type": "secretRef",
      "required": true,
      "description": "Secret reference containing the upstream admin API key."
    }
  ],
  "permissions": {
    "contributesActions": true,
    "contributesShellPresentation": true,
    "requiresExternalHttpExecution": true,
    "requiresDeploymentSecrets": true
  },
  "contributions": {
    "actions": [
      {
        "actionId": "shopify-order-read",
        "displayName": "Read Shopify order",
        "readOnly": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "shopify-order-cancel",
        "displayName": "Cancel Shopify order",
        "readOnly": false,
        "confirmationRequired": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      }
    ],
    "shell": {
      "moduleRefs": ["actions"]
    }
  }
}'
where id = 'mkv-action-shopify-admin-v1';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-data-commerce-catalog",
  "version": "1.0.0",
  "pluginType": "DATA",
  "displayName": "Commerce Catalog Data",
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
    "requiresSharedDatasetAccess": true
  },
  "contributions": {
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "commerce-catalog",
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
