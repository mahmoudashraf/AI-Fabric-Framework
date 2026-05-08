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
    'mkp-action-shopify-customer-account-mcp',
    'shopify-customer-account-mcp-actions',
    'Shopify Customer Account MCP Actions',
    'ACTION',
    'loom',
    'Loom AI',
    'Authenticated Shopify Customer Accounts MCP bundle for account, order, and return actions.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-action-shopify-customer-account-mcp'
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
    'mkp-action-shopify-checkout-mcp',
    'shopify-checkout-mcp-actions',
    'Shopify Checkout MCP Actions',
    'ACTION',
    'loom',
    'Loom AI',
    'Governed Shopify Checkout MCP bundle for checkout creation, continuation, and approved lifecycle operations.',
    'ACTIVE',
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_marketplace_plugins where id = 'mkp-action-shopify-checkout-mcp'
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
    'mkv-action-shopify-customer-account-mcp-v1',
    'mkp-action-shopify-customer-account-mcp',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-customer-account-mcp",
      "version": "1.0.0",
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
            "actionId": "shopify_get_customer_orders",
            "displayName": "Get customer orders",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.orders.list",
            "description": "Retrieve authenticated customer order history through Shopify Customer Accounts MCP after Bridge customer/session binding.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier bound to the customer account token", "type": "STRING", "required": true},
              {"name": "limit", "description": "Maximum orders to request", "type": "INTEGER", "required": false, "min": 1, "max": 20}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "toolName": "get_customer_orders",
                "argumentTemplate": {"limit": "{{params.limit}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_lookup_order",
            "displayName": "Lookup customer order",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.orders.lookup",
            "description": "Lookup a specific authenticated customer order through Shopify Customer Accounts MCP.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier bound to the customer account token", "type": "STRING", "required": true},
              {"name": "order_id", "description": "Customer order identifier", "type": "STRING", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "toolName": "lookup_order",
                "argumentTemplate": {"order_id": "{{params.order_id}}"}
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
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier bound to the customer account token", "type": "STRING", "required": true},
              {"name": "order_id", "description": "Customer order identifier", "type": "STRING", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "toolName": "get_order_status",
                "argumentTemplate": {"order_id": "{{params.order_id}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_get_return_eligibility",
            "displayName": "Get return eligibility",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.returns.eligibility",
            "description": "Check authenticated customer return eligibility through Shopify Customer Accounts MCP.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier bound to the customer account token", "type": "STRING", "required": true},
              {"name": "order_id", "description": "Customer order identifier", "type": "STRING", "required": true},
              {"name": "line_item_ids", "description": "Optional order line item identifiers", "type": "ARRAY", "required": false}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "toolName": "get_return_eligibility",
                "argumentTemplate": {"order_id": "{{params.order_id}}", "line_item_ids": "{{params.line_item_ids}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_start_return_request",
            "displayName": "Start return request",
            "readOnly": false,
            "anonymousAllowed": false,
            "requiresConfirmation": true,
            "confirmationMessage": "Start this return request?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.customer_account.returns.start",
            "description": "Start an authenticated customer return request through Shopify Customer Accounts MCP after Bridge confirmation and audit.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier bound to the customer account token", "type": "STRING", "required": true},
              {"name": "order_id", "description": "Customer order identifier", "type": "STRING", "required": true},
              {"name": "line_item_ids", "description": "Order line item identifiers to return", "type": "ARRAY", "required": true},
              {"name": "reason", "description": "Return reason", "type": "STRING", "required": false},
              {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-customer-account",
                "endpointKind": "CUSTOMER_ACCOUNT",
                "authMode": "CUSTOMER_OAUTH_PKCE",
                "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                "toolName": "start_return_request",
                "argumentTemplate": {"order_id": "{{params.order_id}}", "line_item_ids": "{{params.line_item_ids}}", "reason": "{{params.reason}}"}
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
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-action-shopify-customer-account-mcp-v1'
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
    'mkv-action-shopify-checkout-mcp-v1',
    'mkp-action-shopify-checkout-mcp',
    '1.0.0',
    'GA',
    'PUBLISHED',
    '{
      "schemaVersion": 1,
      "pluginId": "mkp-action-shopify-checkout-mcp",
      "version": "1.0.0",
      "pluginType": "ACTION",
      "displayName": "Shopify Checkout MCP Actions",
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
        "requiresGovernedActionAudit": true
      },
      "contributions": {
        "actions": [
          {
            "actionId": "shopify_create_checkout",
            "displayName": "Create checkout",
            "readOnly": false,
            "anonymousAllowed": false,
            "requiresConfirmation": true,
            "confirmationMessage": "Create checkout and hand off to checkout?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.checkout.create",
            "description": "Create a Shopify checkout session through Checkout MCP and return continuation evidence.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true},
              {"name": "cart_id", "description": "Optional Shopify cart identifier to convert into checkout", "type": "STRING", "required": false},
              {"name": "checkout", "description": "Checkout payload when no cart is supplied", "type": "OBJECT", "required": false},
              {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-checkout",
                "endpointKind": "CHECKOUT_UCP",
                "authMode": "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                "toolName": "create_checkout",
                "argumentTemplate": {
                  "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
                  "cart_id": "{{params.cart_id}}",
                  "checkout": "{{params.checkout}}"
                }
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_get_checkout",
            "displayName": "Get checkout",
            "readOnly": true,
            "anonymousAllowed": false,
            "requiresConfirmation": false,
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.checkout.get",
            "description": "Retrieve checkout state through Shopify Checkout MCP.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance", "type": "STRING", "required": true},
              {"name": "id", "description": "Checkout session identifier", "type": "STRING", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-checkout",
                "endpointKind": "CHECKOUT_UCP",
                "authMode": "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                "toolName": "get_checkout",
                "argumentTemplate": {"meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}}, "id": "{{params.id}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_update_checkout",
            "displayName": "Update checkout",
            "readOnly": false,
            "anonymousAllowed": false,
            "requiresConfirmation": true,
            "confirmationMessage": "Update this checkout?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.checkout.update",
            "description": "Update checkout state through Shopify Checkout MCP after Bridge confirmation and audit.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true},
              {"name": "id", "description": "Checkout session identifier", "type": "STRING", "required": true},
              {"name": "checkout", "description": "Checkout update payload", "type": "OBJECT", "required": true},
              {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-checkout",
                "endpointKind": "CHECKOUT_UCP",
                "authMode": "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                "toolName": "update_checkout",
                "argumentTemplate": {"meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}}, "id": "{{params.id}}", "checkout": "{{params.checkout}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_complete_checkout",
            "displayName": "Complete checkout",
            "readOnly": false,
            "anonymousAllowed": false,
            "requiresConfirmation": true,
            "confirmationMessage": "Complete this checkout?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.checkout.complete",
            "description": "Complete checkout through Shopify Checkout MCP only when terminal operations are explicitly enabled in Bridge.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true},
              {"name": "id", "description": "Checkout session identifier", "type": "STRING", "required": true},
              {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-checkout",
                "endpointKind": "CHECKOUT_UCP",
                "authMode": "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                "requiresTerminalCheckoutEnablement": true,
                "requiresIdempotencyKey": true,
                "toolName": "complete_checkout",
                "argumentTemplate": {"meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}, "idempotency-key": "{{idempotencyKey}}"}, "id": "{{params.id}}"}
              }
            },
            "route": {"method": "POST", "path": "/actions/execute"}
          },
          {
            "actionId": "shopify_cancel_checkout",
            "displayName": "Cancel checkout",
            "readOnly": false,
            "anonymousAllowed": false,
            "requiresConfirmation": true,
            "confirmationMessage": "Cancel this checkout?",
            "groundingEligible": false,
            "readActionResolutionEligible": false,
            "adapterType": "mcp-tool",
            "capabilityRef": "shopify.checkout.cancel",
            "description": "Cancel checkout through Shopify Checkout MCP only when terminal operations are explicitly enabled in Bridge.",
            "category": "shopify-companion",
            "params": [
              {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true},
              {"name": "id", "description": "Checkout session identifier", "type": "STRING", "required": true},
              {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
            ],
            "execution": {
              "adapterType": "mcp-tool",
              "mcp": {
                "serverRef": "shopify-checkout",
                "endpointKind": "CHECKOUT_UCP",
                "authMode": "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                "requiresTerminalCheckoutEnablement": true,
                "requiresIdempotencyKey": true,
                "toolName": "cancel_checkout",
                "argumentTemplate": {"meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}, "idempotency-key": "{{idempotencyKey}}"}, "id": "{{params.id}}"}
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
    select 1 from platform_marketplace_plugin_versions where id = 'mkv-action-shopify-checkout-mcp-v1'
);
