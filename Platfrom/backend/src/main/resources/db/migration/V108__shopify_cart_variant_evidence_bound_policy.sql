update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '"description": "Shopify ProductVariant GID from selected product attachment metadata.",
                  "type": "STRING",
                  "required": true,
                  "pattern": "^gid://shopify/ProductVariant/[0-9]+$"',
    '"description": "Shopify ProductVariant GID from selected product attachment metadata. The runtime must only use values copied from trusted selected product evidence.",
                  "type": "STRING",
                  "required": true,
                  "pattern": "^gid://shopify/ProductVariant/[0-9]+$",
                  "evidenceBound": true,
                  "evidenceKeys": ["product_variant_id", "firstAvailableVariantId"],
                  "evidenceFallbackPolicy": "CLARIFY"'
    ),
    published_at = coalesce(published_at, current_timestamp)
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"name": "product_variant_id"%'
  and manifest_json like '%"pattern": "^gid://shopify/ProductVariant/[0-9]+$"%'
  and manifest_json not like '%"evidenceBound": true%';
