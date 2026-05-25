update platform_marketplace_plugin_versions
set version = '1.0.6',
    manifest_json = replace(
        replace(
            manifest_json,
            '"version": "1.0.5"',
            '"version": "1.0.6"'
        ),
        '"query": "{{params.product_search_query|params.add_items.0.product_variant_id|context.originalQuery}}"',
        '"query": "{{params.product_search_query|context.originalQuery|params.add_items.0.product_variant_id}}"'
    )
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"query": "{{params.product_search_query|params.add_items.0.product_variant_id|context.originalQuery}}"%';
