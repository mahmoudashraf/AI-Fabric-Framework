update platform_marketplace_plugin_versions
set version = '2.0.3',
    manifest_json = replace(
        replace(
            replace(
                replace(
                    replace(
                        replace(
                            manifest_json,
                            '"version": "2.0.2"',
                            '"version": "2.0.3"'
                        ),
                        '"version":"2.0.2"',
                        '"version":"2.0.3"'
                    ),
                    '"toolName": "create_cart",',
                    '"toolName": "create_cart", "dispatchMode": "CONNECTOR",'
                ),
                '"toolName":"create_cart",',
                '"toolName":"create_cart","dispatchMode":"CONNECTOR",'
            ),
            '"toolName": "update_cart",',
            '"toolName": "update_cart", "dispatchMode": "CONNECTOR",'
        ),
        '"toolName":"update_cart",',
        '"toolName":"update_cart","dispatchMode":"CONNECTOR",'
    ),
    published_at = current_timestamp
where id = 'mkv-action-shopify-cart-mcp-v1'
  and manifest_json like '%shopify_create_cart%';
