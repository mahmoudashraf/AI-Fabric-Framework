update platform_marketplace_plugin_versions
set manifest_json = replace(
        replace(
            manifest_json,
            '"defaultConversationMode": "guided-support"',
            '"defaultConversationMode": "thinker_deep"'
        ),
        '"defaultConversationMode":"guided-support"',
        '"defaultConversationMode":"thinker_deep"'
    ),
    published_at = current_timestamp
where plugin_id = 'mkp-template-support-desk-shell'
  and manifest_json like '%"defaultConversationMode"%guided-support%';
