update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    'Confirmation-only shopper-facing phrase with no trailing punctuation. Resolve exact product or variant title and quantity from catalog/product context, for example: Add 1 Selling Plans Ski Wax to your cart. Leave blank if unresolved. This field is not sent to the MCP tool.',
    'Presentation-only shopper-facing confirmation phrase with no trailing punctuation. Resolve exact product or variant title from the user request or storefront context. Use quantity 1 when the shopper asks to add a single product and no quantity is specified. Example: Add 1 Selling Plans Ski Wax to your cart. Leave blank if unresolved. This field is not sent to the MCP tool.'
)
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"name": "cart_update_confirmation"%'
  and manifest_json like '%Confirmation-only shopper-facing phrase with no trailing punctuation.%';
