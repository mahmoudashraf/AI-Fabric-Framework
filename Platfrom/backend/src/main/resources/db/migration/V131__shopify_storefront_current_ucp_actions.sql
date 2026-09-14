update platform_marketplace_plugins
set display_name = 'Shopify Storefront MCP Actions',
    short_description = 'Shopify UCP catalog and product actions plus standard Storefront policy search.',
    updated_at = current_timestamp
where id = 'mkp-action-shopify-storefront-read-mcp';

update platform_marketplace_plugin_versions
set version = '1.1.0',
    manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-storefront-read-mcp",
  "version": "1.1.0",
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
  "pricing": {"pricingModel": "FREE"},
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
        "llmFacts": {
          "rootPath": "toolResult.content.0.text",
          "lists": [
            {
              "sourcePath": "products.0.variants",
              "target": "documents",
              "maxItems": 5,
              "includeFields": ["id", "title", "availability.available"]
            }
          ]
        },
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.catalog.search",
        "description": "Search the Shopify storefront catalog through the current Shopify UCP endpoint.",
        "category": "shopify-companion",
        "params": [
          {"name": "query", "description": "Shopper search query", "type": "STRING", "required": true},
          {"name": "country", "description": "Optional buyer country code for catalog localization", "type": "STRING", "required": false},
          {"name": "language", "description": "Optional buyer language in IETF BCP 47 form", "type": "STRING", "required": false},
          {"name": "intent", "description": "Optional shopper intent or preference signal for relevance", "type": "STRING", "required": false},
          {"name": "limit", "description": "Maximum catalog results to request", "type": "INTEGER", "required": false, "min": 1, "max": 20, "defaultValue": 10}
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
                  "language": "{{params.language}}",
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
        "description": "Retrieve current product details through the Shopify UCP catalog endpoint.",
        "category": "shopify-companion",
        "params": [
          {"name": "product_id", "description": "Shopify Product GID", "type": "STRING", "required": true, "pattern": "^gid://shopify/Product/[0-9]+$"},
          {"name": "country", "description": "Optional buyer country code for localization", "type": "STRING", "required": false},
          {"name": "language", "description": "Optional language code for localization", "type": "STRING", "required": false}
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
                "id": "{{params.product_id}}",
                "context": {
                  "address_country": "{{params.country}}",
                  "language": "{{params.language}}"
                }
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
        "readActionResolutionEligible": false,
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
    published_at = current_timestamp
where id = 'mkv-action-shopify-storefront-read-mcp-v1';

update platform_marketplace_plugins
set display_name = 'Shopify UCP Cart Actions',
    short_description = 'Governed cart reads, creation, and updates through the current Shopify UCP cart contract.',
    updated_at = current_timestamp
where id = 'mkp-action-shopify-cart-mcp';

update platform_marketplace_plugin_versions
set version = '2.0.0',
    manifest_json = '{
  "schemaVersion": 1,
  "pluginId": "mkp-action-shopify-cart-mcp",
  "version": "2.0.0",
  "pluginType": "ACTION",
  "displayName": "Shopify UCP Cart Actions",
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
  "pricing": {"pricingModel": "FREE"},
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
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.cart.get",
        "description": "Retrieve the current cart through Shopify UCP using a trusted storefront cart handle.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "cart_id",
            "description": "Shopify cart identifier resolved from trusted owned storefront context. Never ask the shopper for this value.",
            "type": "STRING",
            "required": true,
            "visibility": "INTERNAL",
            "askUser": false,
            "resolveFrom": {
              "source": "OWNED_RESOURCE",
              "resourceType": "shopify.cart",
              "scope": "current_session",
              "handleField": "cart_id",
              "metadataKeys": ["cart_id", "cartId"]
            }
          },
          {
            "name": "shopperSessionId",
            "description": "Bridge shopper session identifier for governance and audit.",
            "type": "STRING",
            "required": true,
            "visibility": "INTERNAL",
            "askUser": false,
            "resolveFrom": {"source": "RUNTIME_CONTEXT", "field": "sessionId"}
          }
        ],
        "execution": {
          "adapterType": "mcp-tool",
          "mcp": {
            "serverRef": "shopify-storefront-ucp",
            "endpointKind": "UCP_CART",
            "toolName": "get_cart",
            "argumentTemplate": {
              "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
              "id": "{{params.cart_id}}"
            }
          }
        },
        "route": {"method": "POST", "path": "/actions/execute"}
      },
      {
        "actionId": "shopify_create_cart",
        "displayName": "Create Shopify cart",
        "readOnly": false,
        "anonymousAllowed": true,
        "requiresConfirmation": true,
        "confirmationMessage": "{{cart_update_confirmation|Create a cart with the selected items}}?",
        "groundingEligible": false,
        "readActionResolutionEligible": false,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.cart.create",
        "description": "Create a Shopify UCP cart from trusted catalog variants after shopper confirmation.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "product_search_query",
            "description": "Resolver-only product search phrase copied from the shopper request. This value is not sent to Shopify.",
            "type": "STRING",
            "required": false
          },
          {
            "name": "add_items",
            "description": "Trusted product variants and quantities to place in the new cart.",
            "type": "ARRAY",
            "required": true,
            "batchTargets": true,
            "resolveFrom": {
              "source": "READ_ACTION",
              "actionName": "shopify_search_catalog",
              "params": {
                "query": "{{params.product_search_query|params.add_items.0.product_search_query|context.originalQuery|params.add_items.0.product_variant_id}}",
                "limit": 1
              },
              "resultPaths": ["documents.0", "results.0", "_items.0"]
            },
            "items": {
              "name": "cart_item",
              "type": "OBJECT",
              "requiredProperties": ["product_variant_id", "quantity"],
              "properties": {
                "product_variant_id": {
                  "name": "product_variant_id",
                  "description": "Shopify ProductVariant GID copied from trusted selected product evidence.",
                  "type": "STRING",
                  "required": true,
                  "pattern": "^gid://shopify/ProductVariant/[0-9]+$",
                  "evidenceBound": true,
                  "evidenceKeys": ["product_variant_id", "firstAvailableVariantId", "id"],
                  "evidenceFallbackPolicy": "CLARIFY"
                },
                "quantity": {
                  "name": "quantity",
                  "description": "Quantity to add; defaults to 1.",
                  "type": "INTEGER",
                  "required": true,
                  "min": 1,
                  "defaultValue": 1
                }
              }
            }
          },
          {
            "name": "cart_update_confirmation",
            "description": "Presentation-only confirmation phrase with product title and quantity. This value is not sent to Shopify.",
            "type": "STRING",
            "required": false
          },
          {
            "name": "shopperSessionId",
            "description": "Bridge shopper session identifier for governance and audit.",
            "type": "STRING",
            "required": true,
            "visibility": "INTERNAL",
            "askUser": false,
            "resolveFrom": {"source": "RUNTIME_CONTEXT", "field": "sessionId"}
          },
          {
            "name": "confirmationAccepted",
            "description": "Explicit shopper confirmation flag.",
            "type": "BOOLEAN",
            "required": true,
            "visibility": "SYSTEM",
            "askUser": false
          }
        ],
        "execution": {
          "adapterType": "mcp-tool",
          "mcp": {
            "serverRef": "shopify-storefront-ucp",
            "endpointKind": "UCP_CART",
            "toolName": "create_cart",
            "requiredAnyArguments": ["cart.line_items"],
            "argumentTemplate": {
              "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
              "cart": {"line_items": "{{params.line_items}}"}
            }
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
        "description": "Apply confirmed cart additions, quantity updates, or removals through the full-state Shopify UCP cart contract.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "cart_id",
            "description": "Shopify cart identifier resolved from trusted owned storefront context. Never ask the shopper for this value.",
            "type": "STRING",
            "required": true,
            "visibility": "INTERNAL",
            "askUser": false,
            "resolveFrom": {
              "source": "OWNED_RESOURCE",
              "resourceType": "shopify.cart",
              "scope": "current_session",
              "handleField": "cart_id",
              "metadataKeys": ["cart_id", "cartId"]
            }
          },
          {
            "name": "product_search_query",
            "description": "Resolver-only product search phrase copied from the shopper request. This value is not sent to Shopify.",
            "type": "STRING",
            "required": false
          },
          {
            "name": "add_items",
            "description": "Trusted product variants and quantities to add to the existing cart.",
            "type": "ARRAY",
            "required": false,
            "batchTargets": true,
            "resolveFrom": {
              "source": "READ_ACTION",
              "actionName": "shopify_search_catalog",
              "params": {
                "query": "{{params.product_search_query|params.add_items.0.product_search_query|context.originalQuery|params.add_items.0.product_variant_id}}",
                "limit": 1
              },
              "resultPaths": ["documents.0", "results.0", "_items.0"]
            },
            "items": {
              "name": "cart_item",
              "type": "OBJECT",
              "requiredProperties": ["product_variant_id", "quantity"],
              "properties": {
                "product_variant_id": {
                  "name": "product_variant_id",
                  "description": "Shopify ProductVariant GID copied from trusted selected product evidence.",
                  "type": "STRING",
                  "required": true,
                  "pattern": "^gid://shopify/ProductVariant/[0-9]+$",
                  "evidenceBound": true,
                  "evidenceKeys": ["product_variant_id", "firstAvailableVariantId", "id"],
                  "evidenceFallbackPolicy": "CLARIFY"
                },
                "quantity": {
                  "name": "quantity",
                  "description": "Quantity to add; defaults to 1.",
                  "type": "INTEGER",
                  "required": true,
                  "min": 1,
                  "defaultValue": 1
                }
              }
            }
          },
          {
            "name": "update_items",
            "description": "Trusted existing cart lines and their desired quantities.",
            "type": "ARRAY",
            "required": false,
            "items": {
              "name": "cart_line_update",
              "type": "OBJECT",
              "requiredProperties": ["line_id", "quantity"],
              "properties": {
                "line_id": {
                  "name": "line_id",
                  "description": "Existing line identifier from trusted current-cart evidence.",
                  "type": "STRING",
                  "required": true,
                  "evidenceBound": true,
                  "evidenceKeys": ["line_id", "lineId", "id"],
                  "evidenceFallbackPolicy": "CLARIFY"
                },
                "quantity": {
                  "name": "quantity",
                  "description": "Desired quantity. Use zero to remove the line.",
                  "type": "INTEGER",
                  "required": true,
                  "min": 0
                }
              }
            }
          },
          {
            "name": "remove_line_ids",
            "description": "Existing line identifiers from trusted current-cart evidence to remove.",
            "type": "ARRAY",
            "required": false,
            "items": {
              "name": "line_id",
              "type": "STRING",
              "evidenceBound": true,
              "evidenceKeys": ["line_id", "lineId", "id"],
              "evidenceFallbackPolicy": "CLARIFY"
            }
          },
          {
            "name": "cart_update_confirmation",
            "description": "Presentation-only confirmation phrase with the exact requested cart change. This value is not sent to Shopify.",
            "type": "STRING",
            "required": false
          },
          {
            "name": "shopperSessionId",
            "description": "Bridge shopper session identifier for governance and audit.",
            "type": "STRING",
            "required": true,
            "visibility": "INTERNAL",
            "askUser": false,
            "resolveFrom": {"source": "RUNTIME_CONTEXT", "field": "sessionId"}
          },
          {
            "name": "confirmationAccepted",
            "description": "Explicit shopper confirmation flag.",
            "type": "BOOLEAN",
            "required": true,
            "visibility": "SYSTEM",
            "askUser": false
          }
        ],
        "execution": {
          "adapterType": "mcp-tool",
          "mcp": {
            "serverRef": "shopify-storefront-ucp",
            "endpointKind": "UCP_CART",
            "toolName": "update_cart",
            "argumentTemplate": {
              "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
              "id": "{{params.cart_id}}",
              "cart": {"line_items": "{{params.line_items}}"}
            }
          }
        },
        "route": {"method": "POST", "path": "/actions/execute"}
      }
    ],
    "shell": {"moduleRefs": ["actions"]}
  }
}',
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1';
