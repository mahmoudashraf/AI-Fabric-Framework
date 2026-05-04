update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '    "actions": [
',
    '    "actions": [
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
        "description": "Search the Shopify storefront catalog through Shopify Storefront MCP UCP catalog tools.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Shopper search query",
            "type": "STRING",
            "required": true
          },
          {
            "name": "country",
            "description": "Optional buyer country code for catalog localization",
            "type": "STRING",
            "required": false
          },
          {
            "name": "intent",
            "description": "Optional shopper intent or preference signal for relevance",
            "type": "STRING",
            "required": false
          },
          {
            "name": "limit",
            "description": "Maximum catalog results to request",
            "type": "INTEGER",
            "required": false,
            "min": 1,
            "max": 20
          }
        ],
        "execution": {
          "adapterType": "mcp-tool",
          "mcp": {
            "serverRef": "shopify-storefront-ucp",
            "endpointKind": "UCP_CATALOG",
            "toolName": "search_catalog",
            "argumentTemplate": {
              "meta": {
                "ucp-agent": {
                  "profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"
                }
              },
              "catalog": {
                "query": "{{params.query}}",
                "context": {
                  "address_country": "{{params.country}}",
                  "intent": "{{params.intent}}"
                },
                "pagination": {
                  "limit": "{{params.limit}}"
                }
              }
            }
          }
        },
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
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
        "description": "Search Shopify storefront policies and FAQs through Shopify Storefront MCP.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Shopper policy or FAQ question",
            "type": "STRING",
            "required": true
          },
          {
            "name": "context",
            "description": "Optional product or shopper context",
            "type": "STRING",
            "required": false
          }
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
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
      {
        "actionId": "shopify_get_product_details",
        "displayName": "Get Shopify product details",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.product.details",
        "description": "Retrieve Shopify storefront product details through Shopify Storefront MCP.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "product_id",
            "description": "Shopify product ID",
            "type": "STRING",
            "required": true
          },
          {
            "name": "country",
            "description": "Optional buyer country code for localization",
            "type": "STRING",
            "required": false
          },
          {
            "name": "language",
            "description": "Optional language code for localization",
            "type": "STRING",
            "required": false
          }
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
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },
'
)
where id = 'mkv-action-shopify-companion-read-v1'
  and manifest_json not like '%"actionId": "shopify_search_catalog"%';
