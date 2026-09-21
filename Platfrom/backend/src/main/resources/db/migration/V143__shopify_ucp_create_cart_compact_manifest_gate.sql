update platform_marketplace_plugin_versions
set version = '2.0.1',
    manifest_json = replace(
        replace(
            replace(
                replace(
                    manifest_json,
                    '"version": "2.0.0"',
                    '"version": "2.0.1"'
                ),
                '"version":"2.0.0"',
                '"version":"2.0.1"'
            ),
            '"requiredAnyArguments": ["cart.line_items"]',
            '"requiredAnyArguments": ["add_items"]'
        ),
        '"requiredAnyArguments":["cart.line_items"]',
        '"requiredAnyArguments":["add_items"]'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%shopify_create_cart%';
