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
    'mkp-action-shopify-storefront-read-mcp',
    'shopify-storefront-read-mcp-actions',
    'Shopify Storefront Read MCP Actions',
    'ACTION',
    'loom',
    'Loom AI',
    'Greenfield Shopify Storefront MCP read bundle for UCP catalog and policy tools.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-action-shopify-storefront-read-mcp'
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
    'mkp-action-shopify-cart-mcp',
    'shopify-cart-mcp-actions',
    'Shopify Cart MCP Actions',
    'ACTION',
    'loom',
    'Loom AI',
    'Elite Shopify Storefront MCP cart bundle with governed cart read and mutation tools.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-action-shopify-cart-mcp'
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
    'mkv-action-shopify-storefront-read-mcp-v1',
    'mkp-action-shopify-storefront-read-mcp',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-storefront-read-mcp",
      "version": "1.0.0",
      "pluginType": "ACTION",
      "displayName": "Shopify Storefront Read MCP Actions",
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
        "requiresExternalHttpExecution": true,
        "usesMcpTools": true
      },
      "contributions": {
        "actions": [
          {
            "actionId": "shopify_search_catalog",
            "displayName": "Search Shopify catalog",
            "readOnly": true,
            "anonymousAllowed": true,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.storefront.catalog.search",
            "description": "Search the Shopify storefront catalog through the Shopify Storefront MCP UCP catalog endpoint.",
            "category": "shopify-companion",
            "params": [
              {"name": "query", "description": "Shopper search query", "type": "STRING", "required": true},
              {"name": "country", "description": "Optional buyer country code for catalog localization", "type": "STRING", "required": false},
              {"name": "intent", "description": "Optional shopper intent or preference signal for relevance", "type": "STRING", "required": false},
              {"name": "limit", "description": "Maximum catalog results to request", "type": "INTEGER", "required": false, "min": 1, "max": 20}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-storefront-ucp",
                "endpointKind": "UCP_CATALOG",
                "toolName": "search_catalog",
                "argumentTemplate": {
                  "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
                  "catalog": {
                    "query": "{{params.query}}",
                    "context": {
                      "address_country": "{{params.country}}",
                      "intent": "{{params.intent}}"
                    },
                    "pagination": {"limit": "{{params.limit}}"}
                  }
                }
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_lookup_catalog",
            "displayName": "Lookup Shopify catalog items",
            "readOnly": true,
            "anonymousAllowed": true,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.storefront.catalog.lookup",
            "description": "Lookup Shopify products or variants by identifier through the Storefront MCP UCP catalog endpoint.",
            "category": "shopify-companion",
            "params": [
              {"name": "ids", "description": "Shopify product or variant identifiers", "type": "ARRAY", "required": true},
              {"name": "country", "description": "Optional buyer country code for catalog localization", "type": "STRING", "required": false}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-storefront-ucp",
                "endpointKind": "UCP_CATALOG",
                "toolName": "lookup_catalog",
                "argumentTemplate": {
                  "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
                  "catalog": {
                    "ids": "{{params.ids}}",
                    "context": {"address_country": "{{params.country}}"}
                  }
                }
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_get_product",
            "displayName": "Get Shopify product",
            "readOnly": true,
            "anonymousAllowed": true,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.storefront.catalog.product.get",
            "description": "Retrieve a Shopify product by product or variant identifier through the Storefront MCP UCP catalog endpoint.",
            "category": "shopify-companion",
            "params": [
              {"name": "id", "description": "Shopify product or variant identifier", "type": "STRING", "required": true},
              {"name": "selected", "description": "Optional variant option selections", "type": "ARRAY", "required": false},
              {"name": "country", "description": "Optional buyer country code for catalog localization", "type": "STRING", "required": false}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-storefront-ucp",
                "endpointKind": "UCP_CATALOG",
                "toolName": "get_product",
                "argumentTemplate": {
                  "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
                  "catalog": {
                    "id": "{{params.id}}",
                    "selected": "{{params.selected}}",
                    "context": {"address_country": "{{params.country}}"}
                  }
                }
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_search_policies",
            "displayName": "Search Shopify policies",
            "readOnly": true,
            "anonymousAllowed": true,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.storefront.policies.search",
            "description": "Search Shopify storefront policies and FAQs through the standard Storefront MCP endpoint.",
            "category": "shopify-companion",
            "params": [
              {"name": "query", "description": "Shopper policy or FAQ question", "type": "STRING", "required": true},
              {"name": "context", "description": "Optional product or shopper context", "type": "STRING", "required": false}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-storefront",
                "endpointKind": "STOREFRONT_STANDARD",
                "toolName": "search_shop_policies_and_faqs",
                "argumentTemplate": {
                  "query": "{{params.query}}",
                  "context": "{{params.context}}"
                }
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          }
        ],
        "shell": {"moduleRefs": ["actions", "products"]}
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-action-shopify-storefront-read-mcp-v1'
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
    'mkv-action-shopify-cart-mcp-v1',
    'mkp-action-shopify-cart-mcp',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-cart-mcp",
      "version": "1.0.0",
      "pluginType": "ACTION",
      "displayName": "Shopify Cart MCP Actions",
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
        "requiresExternalHttpExecution": true,
        "usesMcpTools": true,
        "requiresGovernedActionAudit": true
      },
      "contributions": {
        "actions": [
          {
            "actionId": "shopify_get_cart",
            "displayName": "Get Shopify cart",
            "readOnly": true,
            "anonymousAllowed": true,
            "requiresConfirmation": false,
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.storefront.cart.get",
            "description": "Retrieve the current Shopify cart through the standard Storefront MCP endpoint after Bridge session and tier checks.",
            "category": "shopify-companion",
            "params": [
              {"name": "cart_id", "description": "Shopify cart identifier", "type": "STRING", "required": true},
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-storefront",
                "endpointKind": "STOREFRONT_STANDARD",
                "toolName": "get_cart",
                "argumentTemplate": {"cart_id": "{{params.cart_id}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_update_cart",
            "displayName": "Update Shopify cart",
            "readOnly": false,
            "anonymousAllowed": true,
            "requiresConfirmation": true,
            "confirmationMessage": "{{cart_update_confirmation|Update your cart}}?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.storefront.cart.update",
            "description": "Update a Shopify cart through the standard Storefront MCP endpoint after Bridge tier, confirmation, rate-limit, and audit checks.",
            "category": "shopify-companion",
            "params": [
              {"name": "cart_id", "description": "Shopify cart identifier. If omitted, Shopify may create a new cart.", "type": "STRING", "required": false},
              {"name": "add_items", "description": "Items to add to the cart", "type": "ARRAY", "required": false},
              {"name": "update_items", "description": "Existing line items to update", "type": "ARRAY", "required": false},
              {"name": "remove_line_ids", "description": "Existing cart line identifiers to remove", "type": "ARRAY", "required": false},
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true},
              {"name": "cart_update_confirmation", "description": "Confirmation-only shopper-facing phrase with no trailing punctuation. Resolve exact product or variant title and quantity from catalog/product context, for example: Add 1 Selling Plans Ski Wax to your cart. Leave blank if unresolved. This field is not sent to the MCP tool.", "type": "STRING", "required": false},
              {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-storefront",
                "endpointKind": "STOREFRONT_STANDARD",
                "toolName": "update_cart",
                "argumentTemplate": {
                  "cart_id": "{{params.cart_id}}",
                  "add_items": "{{params.add_items}}",
                  "update_items": "{{params.update_items}}",
                  "remove_line_ids": "{{params.remove_line_ids}}"
                }
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          }
        ],
        "shell": {"moduleRefs": ["actions"]}
      }
    }',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-action-shopify-cart-mcp-v1'
);

update platform_marketplace_plugin_versions
set manifest_json = replace(
    replace(manifest_json, '"mkp-action-shopify-companion-read"', '"mkp-action-shopify-storefront-read-mcp"'),
    '"mkp-inference-shopify-companion-default"',
    '"mkp-inference-shared-embeddings"'
)
where id = 'mkv-template-shopify-companion-v1';

update shopify_companion_package_profiles
set details_json = '{"merchantVisible":true,"requiresBilling":false,"requiresReindexOnEmbeddingChange":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-local-embeddings"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"]}',
    updated_at = current_timestamp
where profile_key = 'LOW_COST';

update shopify_companion_package_profiles
set details_json = '{"merchantVisible":true,"requiresBilling":false,"requiresReindexOnEmbeddingChange":false,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-shared-embeddings"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"]}',
    updated_at = current_timestamp
where profile_key = 'BALANCED';

update shopify_companion_package_profiles
set details_json = '{"merchantVisible":true,"requiresBilling":true,"requiresReindexOnEmbeddingChange":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-premium-hybrid"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"]}',
    updated_at = current_timestamp
where profile_key = 'HIGH_QUALITY';

update shopify_companion_package_profiles
set details_json = '{"merchantVisible":false,"requiresBilling":true,"requiresOperatorApproval":true,"requiresReindexOnEmbeddingChange":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-dedicated-embedding-worker"],"disabledPluginIds":["mkp-action-shopify-companion-read","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp"]}',
    updated_at = current_timestamp
where profile_key = 'ENTERPRISE_DEDICATED';
