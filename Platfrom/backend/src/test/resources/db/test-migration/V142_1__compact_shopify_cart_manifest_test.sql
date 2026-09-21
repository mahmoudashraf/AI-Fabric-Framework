update platform_marketplace_plugin_versions
set manifest_json = replace(
        replace(
            replace(
                manifest_json,
                '"version": "2.0.0"',
                '"version":"2.0.0"'
            ),
            '"actionId": "shopify_create_cart"',
            '"actionId":"shopify_create_cart"'
        ),
        '"requiredAnyArguments": ["cart.line_items"]',
        '"requiredAnyArguments":["cart.line_items"]'
    )
where id = 'mkv-action-shopify-cart-mcp-v1';
