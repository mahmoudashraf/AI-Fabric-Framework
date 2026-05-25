update platform_marketplace_plugin_versions
set
    version = '1.0.3',
    manifest_json = replace(
        replace(
            replace(
                manifest_json,
                '"version": "1.0.2"',
                '"version": "1.0.3"'
            ),
            '              },
              {
                "name": "add_items",',
            '              },
              {
                "name": "product_search_query",
                "description": "Resolver-only product search phrase for cart mutations. Copy only the shopper-facing target product/category words from the request, without cart verbs or action wording. Example: Selling Plans Ski Wax. This field is used only to resolve trusted catalog evidence and is not sent to the MCP cart tool.",
                "type": "STRING",
                "required": false
              },
              {
                "name": "add_items",'
        ),
        '"query": "{{context.originalQuery}}",',
        '"query": "{{params.product_search_query|context.originalQuery}}",'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%"actionId": "shopify_update_cart"%'
  and manifest_json like '%"actionName": "shopify_search_catalog"%'
  and manifest_json not like '%"name": "product_search_query"%';
