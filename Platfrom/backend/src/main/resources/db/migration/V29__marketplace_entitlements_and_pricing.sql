create table platform_deployment_marketplace_entitlements (
    id varchar(64) primary key,
    install_id varchar(64) not null,
    deployment_id varchar(64) not null,
    plugin_id varchar(64) not null,
    plugin_version_id varchar(64) not null,
    pricing_model varchar(64) not null,
    amount numeric(12,2),
    currency varchar(16),
    billing_interval varchar(32),
    status varchar(64) not null,
    grace_ends_at timestamp with time zone,
    access_ends_at timestamp with time zone,
    note text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_marketplace_entitlements_install
        foreign key (install_id) references platform_deployment_marketplace_plugin_installs (id) on delete cascade,
    constraint fk_platform_marketplace_entitlements_version
        foreign key (plugin_version_id) references platform_marketplace_plugin_versions (id) on delete cascade
);

create unique index uk_platform_marketplace_entitlements_install
    on platform_deployment_marketplace_entitlements (install_id);

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
  "pricing": {
    "pricingModel": "ONE_OFF",
    "amount": 49.00,
    "currency": "USD"
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
