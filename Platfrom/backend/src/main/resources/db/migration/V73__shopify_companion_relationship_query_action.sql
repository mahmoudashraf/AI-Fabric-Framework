update platform_marketplace_plugin_versions
set manifest_json = replace(
    manifest_json,
    '"actions": [',
    '"actions": [
      {
        "actionId": "relationship_query",
        "displayName": "Query live Shopify catalog relationships",
        "readOnly": true,
        "anonymousAllowed": true,
        "requiresConfirmation": false,
        "groundingEligible": true,
        "readActionResolutionEligible": true,
        "adapterType": "connector-http",
        "description": "Run a live Shopify catalog or policy relationship query for comparison, similar-product, and policy grounding flows.",
        "category": "shopify-companion",
        "params": [
          {
            "name": "query",
            "description": "Natural-language product, comparison, similar item, or policy query",
            "type": "STRING",
            "required": true
          },
          {
            "name": "entityTypes",
            "description": "Optional entity types such as product or policy",
            "type": "STRING",
            "required": false
          },
          {
            "name": "limit",
            "description": "Maximum results to return",
            "type": "INTEGER",
            "required": false,
            "min": 1,
            "max": 20
          }
        ],
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      },'
)
where id = 'mkv-action-shopify-companion-read-v1'
  and manifest_json not like '%"actionId": "relationship_query"%';
