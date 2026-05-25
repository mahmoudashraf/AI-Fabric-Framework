update platform_marketplace_plugin_versions
set
    version = '1.0.2',
    manifest_json = replace(
        replace(
            manifest_json,
            '"version": "1.0.1"',
            '"version": "1.0.2"'
        ),
        '"batchTargets": true,
                "items": {',
        '"batchTargets": true,
                "resolveFrom": {
                  "source": "READ_ACTION",
                  "actionName": "shopify_search_catalog",
                  "params": {
                    "query": "{{context.originalQuery}}",
                    "limit": 1
                  },
                  "resultPaths": ["documents.0", "results.0", "_items.0"]
                },
                "items": {'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"actionId": "shopify_update_cart"%'
  and manifest_json like '%"name": "add_items"%'
  and manifest_json not like '%"actionName": "shopify_search_catalog"%';
