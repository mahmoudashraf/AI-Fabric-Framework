-- Keep historical seed migrations intact while upgrading the active first-party
-- DATA manifests to the explicit AI Fabric 0.4 projection contract.
with entity_contracts(plugin_id, entity_type) as (
    values
        ('mkp-data-commerce-catalog', 'product'),
        ('mkp-data-help-center', 'faq-article'),
        ('mkp-data-policy-folder', 'support-policy'),
        ('mkp-data-shopify-catalog', 'product'),
        ('mkp-data-shopify-policies', 'support-policy')
)
update platform_marketplace_plugin_versions version
set manifest_json = jsonb_set(
    version.manifest_json::jsonb,
    '{contributions,entityConfig}',
    jsonb_build_object(
        'ai-entities',
        jsonb_build_object(
            entity_contracts.entity_type,
            '{
              "indexing": {
                "enabled": true,
                "max-characters": 8000
              },
              "analysis": {
                "enabled": false,
                "after": []
              },
              "searchable-fields": [
                {
                  "name": "content",
                  "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                  "preprocessing": "CLEAN",
                  "max-length": 8000,
                  "priority": 100,
                  "required": true
                }
              ],
              "metadata-fields": [
                {
                  "name": "title",
                  "data-type": "STRING",
                  "description": "Human-readable dataset record title.",
                  "destinations": ["VECTOR_METADATA", "LLM_CONTEXT", "API_RESPONSE"],
                  "priority": 90,
                  "required": false,
                  "sanitize-pii": false
                },
                {
                  "name": "scope",
                  "data-type": "STRING",
                  "description": "Dataset-defined retrieval scope.",
                  "destinations": ["VECTOR_METADATA", "LLM_CONTEXT"],
                  "priority": 80,
                  "required": false,
                  "sanitize-pii": false
                },
                {
                  "name": "tenantId",
                  "data-type": "ID",
                  "description": "Server-owned tenant isolation key.",
                  "destinations": ["VECTOR_METADATA"],
                  "priority": 100,
                  "required": true,
                  "sanitize-pii": false
                }
              ]
            }'::jsonb
        )
    ),
    true
)::text
from entity_contracts
where version.plugin_id = entity_contracts.plugin_id
  and version.version = '1.0.0'
  and upper(version.status) = 'PUBLISHED';
