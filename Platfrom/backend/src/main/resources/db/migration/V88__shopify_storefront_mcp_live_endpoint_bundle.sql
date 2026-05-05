update platform_marketplace_plugins
set display_name = 'Shopify Storefront MCP Actions',
    short_description = 'Greenfield Shopify Storefront MCP action bundle for live catalog search, product details, and policy answers.',
    updated_at = current_timestamp
where id = 'mkp-action-shopify-storefront-read-mcp';

update platform_marketplace_plugin_versions
set manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-storefront-read-mcp",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Shopify Storefront MCP Actions",
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
        "description": "Search the Shopify storefront catalog through the standard Shopify Storefront MCP endpoint.",
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
            "serverRef": "shopify-storefront",
            "endpointKind": "STOREFRONT_STANDARD",
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
        "actionId": "shopify_get_product_details",
        "displayName": "Get Shopify product details",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": false,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.product.details",
        "description": "Retrieve Shopify product details through the standard Shopify Storefront MCP endpoint.",
        "category": "shopify-companion",
        "params": [
          {"name": "product_id", "description": "Shopify product ID", "type": "STRING", "required": true},
          {"name": "country", "description": "Optional buyer country code for localization", "type": "STRING", "required": false},
          {"name": "language", "description": "Optional language code for localization", "type": "STRING", "required": false}
        ],
        "execution": {
          "adapterType": "mcp-tool",
          "mcp": {
            "serverRef": "shopify-storefront",
            "endpointKind": "STOREFRONT_STANDARD",
            "toolName": "get_product_details",
            "argumentTemplate": {
              "product_id": "{{params.product_id}}",
              "country": "{{params.country}}",
              "language": "{{params.language}}"
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
        "readActionResolutionEligible": false,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.policies.search",
        "description": "Search Shopify storefront policies and FAQs through the standard Shopify Storefront MCP endpoint.",
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
    published_at = coalesce(published_at, current_timestamp)
where id = 'mkv-action-shopify-storefront-read-mcp-v1';
