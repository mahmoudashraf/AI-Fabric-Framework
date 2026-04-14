update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-template-support-desk-shell",
  "version": "1.0.0",
  "pluginType": "TEMPLATE",
  "displayName": "Loom Support Desk Shell",
  "capabilityProfiles": ["SURFACE"],
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
    "contributesShellPresentation": true,
    "contributesSurfaceCapabilities": true
  },
  "contributions": {
    "template": {
      "curatedModuleId": "support",
      "recommendedPluginIds": ["mkp-data-help-center", "mkp-action-notifications", "mkp-automation-order-retention"],
      "security": {
        "authzMode": "ALLOW_VERIFIED"
      },
      "shell": {
        "enabledModuleIds": ["docs", "ai-search", "actions", "support"],
        "defaultConversationMode": "guided-support"
      }
    }
  }
}'
where id = 'mkv-template-support-desk-shell-v1';
