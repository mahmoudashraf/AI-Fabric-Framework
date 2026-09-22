update platform_marketplace_plugin_versions
set manifest_json = replace(
        replace(
            manifest_json,
            '"defaultConversationMode": "shopify-companion"',
            '"defaultConversationMode": "thinker_deep"'
        ),
        '"defaultConversationMode":"shopify-companion"',
        '"defaultConversationMode":"thinker_deep"'
    ),
    published_at = current_timestamp
where plugin_id in (
        'mkp-template-shopify-companion',
        'mkp-template-shopify-companion-staging',
        'mkp-template-shopify-companion-production'
    )
  and manifest_json like '%"defaultConversationMode"%shopify-companion%';
