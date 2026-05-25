update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '"description": "Shopify ProductVariant GID from selected product attachment metadata.",
                  "type": "STRING",
                  "required": true',
    '"description": "Shopify ProductVariant GID from selected product attachment metadata.",
                  "type": "STRING",
                  "required": true,
                  "pattern": "^gid://shopify/ProductVariant/[0-9]+$"'
    ),
    published_at = coalesce(published_at, current_timestamp)
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"name": "product_variant_id"%'
  and manifest_json not like '%"pattern": "^gid://shopify/ProductVariant/[0-9]+$"%';
