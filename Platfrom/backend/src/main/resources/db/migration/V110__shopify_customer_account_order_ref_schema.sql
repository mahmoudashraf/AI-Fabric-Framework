update platform_marketplace_plugin_versions
set
    version = '1.0.5',
    manifest_json = replace(
        replace(
            replace(
                manifest_json,
                '"version": "1.0.4"',
                '"version": "1.0.5"'
            ),
            '{"name": "order_number", "description": "Customer-visible order number.", "type": "STRING", "required": true}',
            '{"name": "order_number", "description": "Customer-visible order number.", "type": "STRING", "required": true, "pattern": "^(?=.*[0-9])[#A-Za-z0-9_-]+$"}'
        ),
        '"type": "STRING",
                "required": true,
                "resolveFrom"',
        '"type": "STRING",
                "required": true,
                "pattern": "^(?=.*[0-9])[#A-Za-z0-9_-]+$",
                "resolveFrom"'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-customer-account-mcp-v1';
