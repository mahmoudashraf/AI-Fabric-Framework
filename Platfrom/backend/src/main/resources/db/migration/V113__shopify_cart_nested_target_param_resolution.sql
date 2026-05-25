update platform_marketplace_plugin_versions
set
    version = '1.0.4',
    manifest_json = replace(
        replace(
            manifest_json,
            '"version": "1.0.3"',
            '"version": "1.0.4"'
        ),
        '"query": "{{params.product_search_query|context.originalQuery}}",',
        '"query": "{{params.product_search_query|params.add_items.0.product_variant_id|context.originalQuery}}",'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"actionId": "shopify_update_cart"%'
  and manifest_json like '%"name": "product_search_query"%'
  and manifest_json like '%"query": "{{params.product_search_query|context.originalQuery}}",%';
