update platform_marketplace_plugin_versions
set manifest_json = replace(
    replace(
        manifest_json,
        '"confirmationMessage": "Update your cart?",',
        '"confirmationMessage": "{{cart_update_confirmation|Update your cart}}?",'
    ),
    '{"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}',
    '{"name": "cart_update_confirmation", "description": "Presentation-only shopper-facing confirmation phrase with no trailing punctuation. Resolve exact product or variant title from the user request or storefront context. Use quantity 1 when the shopper asks to add a single product and no quantity is specified. Example: Add 1 Selling Plans Ski Wax to your cart. Leave blank if unresolved. This field is not sent to the MCP tool.", "type": "STRING", "required": false},
          {"name": "confirmationAccepted", "description": "Explicit shopper confirmation flag", "type": "BOOLEAN", "required": true}'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"toolName": "update_cart"%'
  and manifest_json not like '%"name": "cart_update_confirmation"%';
