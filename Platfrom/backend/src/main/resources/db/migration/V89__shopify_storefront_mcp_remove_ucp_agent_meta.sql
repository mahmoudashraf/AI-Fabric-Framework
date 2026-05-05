update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '            "argumentTemplate": {
              "meta": {"ucp-agent": {"profileRef": "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"}},
              "catalog": {',
    '            "argumentTemplate": {
              "catalog": {'
)
where id = 'mkv-action-shopify-storefront-read-mcp-v1'
  and manifest_json like '%"actionId": "shopify_search_catalog"%'
  and manifest_json like '%"endpointKind": "STOREFRONT_STANDARD"%'
  and manifest_json like '%"ucp-agent"%';
