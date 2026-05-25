update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '"name": "query", "description": "Shopper search query", "type": "STRING", "required": true',
    '"name": "query", "description": "Required shopper product-search phrase. Copy the shopper-facing product/category/preference wording from the request, including price or size constraints when no dedicated structured parameter exists.", "type": "STRING", "required": true'
)
where id = 'mkv-action-shopify-storefront-read-mcp-v1'
  and manifest_json like '%"actionId": "shopify_search_catalog"%'
  and manifest_json like '%"name": "query", "description": "Shopper search query"%';
