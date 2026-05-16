update platform_marketplace_plugin_versions
set
    version = '1.0.2',
    manifest_json = '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-customer-account-mcp",
      "version": "1.0.2",
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
            "description": "Retrieve the authenticated customer''s most recent order status through Shopify Customer Accounts MCP after Bridge customer/session binding.",
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
            "description": "Retrieve authenticated customer order status through Shopify Customer Accounts MCP.",
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
          }
        ],
        "shell": {"moduleRefs": ["actions"]}
      }
    }',
    published_at = current_timestamp
where id = 'mkv-action-shopify-customer-account-mcp-v1';
