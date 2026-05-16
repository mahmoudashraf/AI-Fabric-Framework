update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '"description": "Quantity to add; default to 1 when the shopper does not specify quantity.",
                  "type": "INTEGER",
                  "required": true,
                  "min": 1',
    '"description": "Quantity to add; default to 1 when the shopper does not specify quantity.",
                  "type": "INTEGER",
                  "required": true,
                  "min": 1,
                  "defaultValue": 1'
    ),
    published_at = coalesce(published_at, current_timestamp)
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"name": "quantity"%'
  and manifest_json not like '%"defaultValue": 1%';
