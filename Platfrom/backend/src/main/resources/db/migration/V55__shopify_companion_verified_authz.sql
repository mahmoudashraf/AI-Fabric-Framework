update platform_marketplace_plugin_versions
set manifest_json = '{
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
        "mkp-inference-shared-embeddings"
      ],
      "security": {
        "authzMode": "ALLOW_VERIFIED"
      },
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
}'
where id = 'mkv-template-shopify-companion-v1'
  and manifest_json not like '%"authzMode": "ALLOW_VERIFIED"%';
