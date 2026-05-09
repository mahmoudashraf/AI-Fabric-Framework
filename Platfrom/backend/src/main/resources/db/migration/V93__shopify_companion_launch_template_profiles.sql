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
    'mkp-template-shopify-companion-staging',
    'shopify-companion-staging-template',
    'Shopify Companion Staging Template',
    'TEMPLATE',
    'loom',
    'Loom AI',
    'Staging launch template for merchant-approved Loom Companion setup, verification, and evidence capture.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-template-shopify-companion-staging'
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
    'mkp-template-shopify-companion-production',
    'shopify-companion-production-template',
    'Shopify Companion Production Template',
    'TEMPLATE',
    'loom',
    'Loom AI',
    'Production launch template for verified Loom Companion promotion with production target-profile policy.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-template-shopify-companion-production'
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
    'mkv-template-shopify-companion-staging-v1',
    'mkp-template-shopify-companion-staging',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-template-shopify-companion-staging",
      "version": "1.0.0",
      "pluginType": "TEMPLATE",
      "displayName": "Shopify Companion Staging Template",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["templates", "shellConfig"],
        "supportedDeploymentTargets": ["dev-openai-qdrant"],
        "supportedProviderModes": ["llm:openai", "embedding:onnx"]
      },
      "pricing": {"pricingModel": "FREE"},
      "permissions": {
        "contributesTemplate": true,
        "contributesShellPresentation": true
      },
      "contributions": {
        "template": {
          "curatedModuleId": "commerce",
          "templateId": "dev-openai-qdrant",
          "launchProfile": {
            "environment": "staging",
            "targetProfileId": "dtp-coolify-staging",
            "billingMode": "test-or-design-partner",
            "requiresVerificationBeforeProduction": true
          },
          "recommendedPluginIds": [
            "mkp-action-shopify-storefront-read-mcp",
            "mkp-data-shopify-catalog",
            "mkp-data-shopify-policies",
            "mkp-inference-shared-embeddings"
          ],
          "shell": {
            "greeting": {
              "title": "Loom Companion",
              "message": "Ask about products, compare options, and get policy guidance before you buy."
            },
            "enabledModuleIds": ["docs", "products", "ai-search", "actions"],
            "starterPrompts": [
              {"id": "find-products", "label": "Find products", "query": "Help me find the right product for travel", "moduleId": "products"},
              {"id": "compare-products", "label": "Compare options", "query": "Compare two good travel bags for carry-on use", "moduleId": "products"},
              {"id": "explain-policies", "label": "Explain policies", "query": "Explain the store refund and shipping policies", "moduleId": "docs"}
            ],
            "defaultConversationMode": "shopify-companion"
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-template-shopify-companion-staging-v1'
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
    'mkv-template-shopify-companion-production-v1',
    'mkp-template-shopify-companion-production',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-template-shopify-companion-production",
      "version": "1.0.0",
      "pluginType": "TEMPLATE",
      "displayName": "Shopify Companion Production Template",
      "compatibility": {
        "minPlatformVersion": "0.1.0",
        "requiredCapabilities": ["templates", "shellConfig"],
        "supportedDeploymentTargets": ["dev-openai-qdrant"],
        "supportedProviderModes": ["llm:openai", "embedding:onnx"]
      },
      "pricing": {"pricingModel": "FREE"},
      "permissions": {
        "contributesTemplate": true,
        "contributesShellPresentation": true
      },
      "contributions": {
        "template": {
          "curatedModuleId": "commerce",
          "templateId": "dev-openai-qdrant",
          "launchProfile": {
            "environment": "production",
            "targetProfileId": "dtp-coolify-production",
            "billingMode": "merchant-approved",
            "requiresVerifiedStagingEvidence": true,
            "failurePolicy": "leave-staging-untouched"
          },
          "recommendedPluginIds": [
            "mkp-action-shopify-storefront-read-mcp",
            "mkp-data-shopify-catalog",
            "mkp-data-shopify-policies",
            "mkp-inference-shared-embeddings"
          ],
          "shell": {
            "greeting": {
              "title": "Loom Companion",
              "message": "Ask about products, compare options, and get policy guidance before you buy."
            },
            "enabledModuleIds": ["docs", "products", "ai-search", "actions"],
            "starterPrompts": [
              {"id": "find-products", "label": "Find products", "query": "Help me find the right product for travel", "moduleId": "products"},
              {"id": "compare-products", "label": "Compare options", "query": "Compare two good travel bags for carry-on use", "moduleId": "products"},
              {"id": "explain-policies", "label": "Explain policies", "query": "Explain the store refund and shipping policies", "moduleId": "docs"}
            ],
            "defaultConversationMode": "shopify-companion"
          }
        }
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-template-shopify-companion-production-v1'
);

update shopify_companion_package_profiles
set template_plugin_id = 'mkp-template-shopify-companion-staging',
    details_json = '{"merchantVisible":true,"requiresBilling":false,"requiresReindexOnEmbeddingChange":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-local-embeddings"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"],"stagingTemplatePluginId":"mkp-template-shopify-companion-staging","productionTemplatePluginId":"mkp-template-shopify-companion-production","productionTargetProfileId":"dtp-coolify-production"}',
    updated_at = current_timestamp
where profile_key = 'LOW_COST';

update shopify_companion_package_profiles
set template_plugin_id = 'mkp-template-shopify-companion-staging',
    details_json = '{"merchantVisible":true,"requiresBilling":false,"requiresReindexOnEmbeddingChange":false,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-shared-embeddings"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"],"stagingTemplatePluginId":"mkp-template-shopify-companion-staging","productionTemplatePluginId":"mkp-template-shopify-companion-production","productionTargetProfileId":"dtp-coolify-production"}',
    updated_at = current_timestamp
where profile_key = 'BALANCED';

update shopify_companion_package_profiles
set template_plugin_id = 'mkp-template-shopify-companion-staging',
    details_json = '{"merchantVisible":true,"requiresBilling":true,"requiresReindexOnEmbeddingChange":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-premium-hybrid"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"],"stagingTemplatePluginId":"mkp-template-shopify-companion-staging","productionTemplatePluginId":"mkp-template-shopify-companion-production","productionTargetProfileId":"dtp-coolify-production"}',
    updated_at = current_timestamp
where profile_key = 'HIGH_QUALITY';

update shopify_companion_package_profiles
set template_plugin_id = 'mkp-template-shopify-companion-staging',
    details_json = '{"merchantVisible":false,"requiresBilling":true,"requiresOperatorApproval":true,"requiresReindexOnEmbeddingChange":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-dedicated-embedding-worker"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"],"stagingTemplatePluginId":"mkp-template-shopify-companion-staging","productionTemplatePluginId":"mkp-template-shopify-companion-production","productionTargetProfileId":"dtp-coolify-production"}',
    updated_at = current_timestamp
where profile_key = 'ENTERPRISE_DEDICATED';
