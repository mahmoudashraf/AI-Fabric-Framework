-- Owned-resource parameter resolution is intentionally metadata-driven, not text-matching.
-- cart_id values are hidden internal handles and must only be resolved from trusted Bridge/session context
-- or allowlisted read-action results; shopper-supplied cart_id values are not ownership proof.
update platform_marketplace_plugin_versions
set
    version = '1.0.1',
    manifest_json = '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-cart-mcp",
      "version": "1.0.1",
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
            "description": "Retrieve the current Shopify cart through the standard Storefront MCP endpoint after Bridge session and tier checks. Use for current-cart questions when trusted storefront context provides the cart handle.",
            "category": "shopify-companion",
            "params": [
              {
                "name": "cart_id",
                "description": "Shopify cart identifier resolved from trusted owned storefront context. Never ask the shopper to provide this value.",
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
              {
                "name": "cart_id",
                "description": "Shopify cart identifier resolved from trusted owned storefront context when available.",
                "type": "STRING",
                "required": false,
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
                "name": "add_items",
                "description": "Items to add to the cart. For selected product targets, use product_variant_id from attachment metadata and quantity 1 unless the shopper specifies a quantity.",
                "type": "ARRAY",
                "required": false,
                "batchTargets": true,
                "items": {
                  "name": "cart_item",
                  "type": "OBJECT",
                  "requiredProperties": ["product_variant_id", "quantity"],
                  "properties": {
                    "product_variant_id": {
                      "name": "product_variant_id",
                      "description": "Shopify ProductVariant GID from selected product attachment metadata. The runtime must only use values copied from trusted selected product evidence.",
                      "type": "STRING",
                      "required": true,
                      "pattern": "^gid://shopify/ProductVariant/[0-9]+$",
                      "evidenceBound": true,
                      "evidenceKeys": ["product_variant_id", "firstAvailableVariantId"],
                      "evidenceFallbackPolicy": "CLARIFY"
                    },
                    "quantity": {
                      "name": "quantity",
                      "description": "Quantity to add; default to 1 when the shopper does not specify quantity.",
                      "type": "INTEGER",
                      "required": true,
                      "min": 1,
                      "defaultValue": 1
                    }
                  }
                }
              },
              {"name": "update_items", "description": "Existing line items to update", "type": "ARRAY", "required": false},
              {"name": "remove_line_ids", "description": "Existing cart line identifiers to remove", "type": "ARRAY", "required": false},
              {
                "name": "cart_update_confirmation",
                "description": "Presentation-only shopper-facing confirmation phrase with no trailing punctuation. Resolve exact product or variant title from the user request or storefront context. Use quantity 1 when the shopper asks to add a single product and no quantity is specified. Example: Add 1 Selling Plans Ski Wax to your cart. Leave blank if unresolved. This field is not sent to the MCP tool.",
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
                "serverRef": "shopify-storefront",
                "endpointKind": "STOREFRONT_STANDARD",
                "toolName": "update_cart",
                "requiredAnyArguments": ["add_items", "update_items", "remove_line_ids"],
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
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1';

update platform_marketplace_plugin_versions
set
    version = '1.0.4',
    manifest_json = '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-customer-account-mcp",
      "version": "1.0.4",
      "pluginType": "ACTION",
      "displayName": "Shopify Customer Account MCP Actions",
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
          "PUBLIC_RUNTIME_AUTHENTICATED"
        ],
        "supportedProviderModes": ["llm:openai"]
      },
      "pricing": {"pricingModel": "FREE"},
      "permissions": {
        "contributesActions": true,
        "requiresExternalHttpExecution": true,
        "usesMcpTools": true,
        "requiresCustomerAuthentication": true,
        "requiresProtectedCustomerData": true,
        "requiresGovernedActionAudit": true
      },
      "contributions": {
        "actions": [
          {
            "actionId": "shopify_get_most_recent_order_status",
            "displayName": "Get most recent order status",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.orders.recent_status",
            "description": "Retrieve only the authenticated shopper owned most recent order status through Shopify Customer Account MCP. Use for owned order-status questions when the shopper does not name a specific order.",
            "category": "shopify-companion",
            "params": [],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "auth": {
                  "mode": "CUSTOMER_OAUTH_PKCE",
                  "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                  "tokenBroker": {
                    "baseUrlProfileRef": "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_TOKEN_BROKER_BASE_URL",
                    "pathTemplate": "/api/admin/customer-account/shops/{{trace.shopDomain}}/token/resolve",
                    "apiKeyHeader": "X-BRIDGE-API-KEY",
                    "apiKeySecretRef": "MCP_SECRET_SHOPIFY_BRIDGE_TOKEN_BROKER_API_KEY"
                  }
                },
                "toolName": "get_most_recent_order_status",
                "argumentTemplate": {}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_get_order_status",
            "displayName": "Get order status",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.orders.status",
            "description": "Retrieve the authenticated shopper owned status for a specific Shopify order number through Customer Account MCP. Use for owned order-status lookup, not generic account profile or store-credit questions.",
            "category": "shopify-companion",
            "params": [
              {"name": "order_number", "description": "Customer-visible order number.", "type": "STRING", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "auth": {
                  "mode": "CUSTOMER_OAUTH_PKCE",
                  "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                  "tokenBroker": {
                    "baseUrlProfileRef": "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_TOKEN_BROKER_BASE_URL",
                    "pathTemplate": "/api/admin/customer-account/shops/{{trace.shopDomain}}/token/resolve",
                    "apiKeyHeader": "X-BRIDGE-API-KEY",
                    "apiKeySecretRef": "MCP_SECRET_SHOPIFY_BRIDGE_TOKEN_BROKER_API_KEY"
                  }
                },
                "toolName": "get_order_status",
                "argumentTemplate": {"order_number": "{{params.order_number}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_get_store_credit_balances",
            "displayName": "Get store credit balances",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": true,
            "readActionResolutionEligible": true,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.store_credit.balances",
            "description": "Retrieve the authenticated shopper owned Shopify store credit balances through Customer Account MCP. Use for owned store-credit balance questions, not order status or account profile questions.",
            "category": "shopify-companion",
            "params": [],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "auth": {
                  "mode": "CUSTOMER_OAUTH_PKCE",
                  "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                  "tokenBroker": {
                    "baseUrlProfileRef": "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_TOKEN_BROKER_BASE_URL",
                    "pathTemplate": "/api/admin/customer-account/shops/{{trace.shopDomain}}/token/resolve",
                    "apiKeyHeader": "X-BRIDGE-API-KEY",
                    "apiKeySecretRef": "MCP_SECRET_SHOPIFY_BRIDGE_TOKEN_BROKER_API_KEY"
                  }
                },
                "toolName": "get_store_credit_balances",
                "argumentTemplate": {}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_request_return",
            "displayName": "Request return",
            "readOnly": false,
            "anonymousAllowed": false,
            "requiresConfirmation": true,
            "confirmationMessage": "Request a return for {{order_number|this order}}?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.returns.request",
            "description": "Submit a Shopify Customer Account MCP return request for an authenticated shopper order after the shopper explicitly asks to start a return and confirms the action. Use for return requests, not general return policy questions.",
            "category": "shopify-companion",
            "params": [
              {
                "name": "order_number",
                "description": "Customer-visible order number for the return request. If the shopper asks for the last or most recent order, resolve it with the configured read action before asking.",
                "type": "STRING",
                "required": true,
                "resolveFrom": {
                  "source": "READ_ACTION",
                  "actionName": "shopify_get_most_recent_order_status",
                  "resultPaths": [
                    "order_number",
                    "orderNumber",
                    "name",
                    "order.number",
                    "order.name",
                    "latestOrder.order_number",
                    "latestOrder.orderNumber",
                    "_items.0.order_number",
                    "_items.0.orderNumber"
                  ]
                }
              },
              {"name": "reason", "description": "Optional shopper-provided return reason.", "type": "STRING", "required": false}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "auth": {
                  "mode": "CUSTOMER_OAUTH_PKCE",
                  "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                  "tokenBroker": {
                    "baseUrlProfileRef": "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_TOKEN_BROKER_BASE_URL",
                    "pathTemplate": "/api/admin/customer-account/shops/{{trace.shopDomain}}/token/resolve",
                    "apiKeyHeader": "X-BRIDGE-API-KEY",
                    "apiKeySecretRef": "MCP_SECRET_SHOPIFY_BRIDGE_TOKEN_BROKER_API_KEY"
                  }
                },
                "toolName": "request_return",
                "argumentTemplate": {"order_number": "{{params.order_number}}", "reason": "{{params.reason}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          }
        ],
        "shell": {"moduleRefs": ["actions"]}
      }
    }',
    published_at = current_timestamp
where id = 'mkv-action-shopify-customer-account-mcp-v1';
