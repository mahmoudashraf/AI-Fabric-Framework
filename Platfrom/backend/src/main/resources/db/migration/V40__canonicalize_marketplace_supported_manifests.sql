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
where plugin_id = 'mkp-template-commerce-shell'
  and version = '1.0.0';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-template-support-desk-shell",
  "version": "1.0.0",
  "pluginType": "TEMPLATE",
  "displayName": "Loom Support Desk Shell",
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
      "curatedModuleId": "support",
      "recommendedPluginIds": ["mkp-data-help-center", "mkp-action-notifications"],
      "security": {
        "authzMode": "ALLOW_VERIFIED"
      },
      "shell": {
        "greeting": {
          "title": "Support Desk",
          "message": "Ask about help-center guidance, troubleshooting steps, support policies, or available support actions."
        },
        "enabledModuleIds": ["docs", "ai-search", "actions", "support"],
        "starterPrompts": [
          {
            "id": "support-capabilities",
            "label": "What can you help me with?",
            "query": "What can you help me with?",
            "moduleId": "support"
          },
          {
            "id": "refund-policy",
            "label": "Summarize refund policy",
            "query": "Summarize the refund policy from the help center",
            "moduleId": "docs"
          },
          {
            "id": "notification-troubleshooting",
            "label": "Troubleshoot notifications",
            "query": "Help me troubleshoot why notifications are not sending",
            "moduleId": "support"
          }
        ],
        "defaultConversationMode": "guided-support"
      }
    }
  }
}'
where plugin_id = 'mkp-template-support-desk-shell'
  and version = '1.0.0';

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
where plugin_id = 'mkp-action-shopify-admin'
  and version = '1.0.0';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-notifications",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Notifications Actions",
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
    "amount": 19.00,
    "currency": "USD"
  },
  "installForm": [
    {
      "id": "provider",
      "label": "Notification provider",
      "type": "select",
      "required": true,
      "options": ["sendgrid", "twilio", "slack"]
    },
    {
      "id": "credentialSecretRef",
      "label": "Credential secret ref",
      "type": "secretRef",
      "required": true
    },
    {
      "id": "defaultSender",
      "label": "Default sender",
      "type": "text",
      "required": false
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
        "actionId": "send-email",
        "displayName": "Send email",
        "readOnly": false,
        "confirmationRequired": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "send-sms",
        "displayName": "Send SMS",
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
where plugin_id = 'mkp-action-notifications'
  and version = '1.0.0';

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
where plugin_id = 'mkp-data-commerce-catalog'
  and version = '1.0.0';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-data-help-center",
  "version": "1.0.0",
  "pluginType": "DATA",
  "displayName": "Help Center Data",
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
where plugin_id = 'mkp-data-help-center'
  and version = '1.0.0';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-data-policy-folder",
  "version": "1.0.0",
  "pluginType": "DATA",
  "displayName": "Policy Folder Data",
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
}'
where plugin_id = 'mkp-data-policy-folder'
  and version = '1.0.0';

delete from platform_marketplace_plugin_versions
where plugin_id = 'mkp-automation-order-retention';

delete from platform_marketplace_plugins
where id = 'mkp-automation-order-retention';
