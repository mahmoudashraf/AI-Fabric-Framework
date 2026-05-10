update platform_marketplace_plugin_versions
set manifest_json = replace(
    replace(
        replace(
            manifest_json,
            '"serverRef": "shopify-storefront-ucp",
                "endpointKind": "UCP_CATALOG",
                "toolName": "search_catalog"',
            '"serverRef": "shopify-storefront",
                "endpointKind": "STOREFRONT_STANDARD",
                "toolName": "search_catalog"'
        ),
        '"serverRef": "shopify-storefront-ucp",
            "endpointKind": "UCP_CATALOG",
            "toolName": "search_catalog"',
        '"serverRef": "shopify-storefront",
            "endpointKind": "STOREFRONT_STANDARD",
            "toolName": "search_catalog"'
    ),
    'Shopify Storefront MCP UCP catalog endpoint',
    'Shopify Storefront MCP endpoint'
)
where manifest_json like '%"actionId": "shopify_search_catalog"%'
  and manifest_json like '%"toolName": "search_catalog"%';
