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
where id = 'mkv-template-support-desk-shell-v1';
