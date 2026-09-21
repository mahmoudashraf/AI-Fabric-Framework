update platform_marketplace_plugin_versions
set version = '2.0.2',
    manifest_json = replace(
        replace(
            replace(
                replace(
                    replace(
                        replace(
                            manifest_json,
                            '"version": "2.0.1"',
                            '"version": "2.0.2"'
                        ),
                        '"version":"2.0.1"',
                        '"version":"2.0.2"'
                    ),
                    '"requiredAnyArguments": ["add_items"]',
                    '"requiredAnyParams": ["add_items"], "requiredAnyArguments": ["cart.line_items"]'
                ),
                '"requiredAnyArguments":["add_items"]',
                '"requiredAnyParams":["add_items"],"requiredAnyArguments":["cart.line_items"]'
            ),
            '"toolName": "update_cart",
            "argumentTemplate"',
            '"toolName": "update_cart",
            "requiredAnyParams": ["add_items", "update_items", "remove_line_ids"],
            "requiredAnyArguments": ["cart.line_items"],
            "argumentTemplate"'
        ),
        '"toolName":"update_cart","argumentTemplate"',
        '"toolName":"update_cart","requiredAnyParams":["add_items","update_items","remove_line_ids"],"requiredAnyArguments":["cart.line_items"],"argumentTemplate"'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%shopify_create_cart%';
