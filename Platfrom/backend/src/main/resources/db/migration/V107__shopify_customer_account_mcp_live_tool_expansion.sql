update platform_marketplace_plugin_versions
set
    version = '1.0.3',
    manifest_json = '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-customer-account-mcp",
      "version": "1.0.3",
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
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.orders.recent_status",
            "description": "Retrieve only the authenticated shopper''s most recent order status through Shopify Customer Account MCP. Use for owned order-status questions when the shopper does not name a specific order.",
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
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.orders.status",
            "description": "Retrieve the authenticated shopper''s status for a specific Shopify order number through Customer Account MCP. Use for owned order-status lookup, not generic account profile or store-credit questions.",
            "category": "shopify-companion",
            "params": [
              {"name": "order_number", "description": "Customer-visible order number", "type": "STRING", "required": true}
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
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.store_credit.balances",
            "description": "Retrieve the authenticated shopper''s Shopify store credit balances through Customer Account MCP. Use for owned store-credit balance questions, not order status or account profile questions.",
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
            "confirmationMessage": "Request a return for this order?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.returns.request",
            "description": "Submit a Shopify Customer Account MCP return request for an authenticated shopper order after the shopper explicitly asks to start a return and confirms the action. Use for return requests, not general return policy questions.",
            "category": "shopify-companion",
            "params": [
              {"name": "order_number", "description": "Customer-visible order number for the return request", "type": "STRING", "required": true},
              {"name": "reason", "description": "Optional shopper-provided return reason", "type": "STRING", "required": false}
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
