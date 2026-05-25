update platform_marketplace_plugin_versions
set manifest_json = '{
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
        "confirmationMessage": "Update your cart?",
        "groundingEligible": false,
        "readActionResolutionEligible": false,
        "adapterType": "mcp-tool",
        "capabilityRef": "shopify.storefront.cart.update",
        "description": "Update a Shopify cart through the standard Storefront MCP endpoint after Bridge tier, confirmation, rate-limit, and audit checks.",
        "category": "shopify-companion",
        "params": [
          {"name": "cart_id", "description": "Shopify cart identifier. If omitted, Shopify may create a new cart.", "type": "STRING", "required": false},
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
                  "description": "Shopify ProductVariant GID from selected product attachment metadata.",
                  "type": "STRING",
                  "required": true
                },
                "quantity": {
                  "name": "quantity",
                  "description": "Quantity to add; default to 1 when the shopper does not specify quantity.",
                  "type": "INTEGER",
                  "required": true,
                  "min": 1
                }
              }
            }
          },
          {"name": "update_items", "description": "Existing line items to update", "type": "ARRAY", "required": false},
          {"name": "remove_line_ids", "description": "Existing cart line identifiers to remove", "type": "ARRAY", "required": false},
          {"name": "shopperSessionId", "description": "Bridge shopper session identifier for governance and audit", "type": "STRING", "required": true},
          {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}
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
