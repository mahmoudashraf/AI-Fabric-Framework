-- H2 test-schema mirror of production V127. Production continues to execute
-- V121-V128 in order on PostgreSQL; this lets the H2 application-context suite
-- validate the current JPA schema without running PostgreSQL-only JSONB updates.
ALTER TABLE platform_deployment_drafts
    ADD COLUMN entity_config_contract_version VARCHAR(64) NOT NULL DEFAULT 'AI_ENTITY_CONFIG_V0_3';

ALTER TABLE platform_deployment_versions
    ADD COLUMN entity_config_contract_version VARCHAR(64) NOT NULL DEFAULT 'AI_ENTITY_CONFIG_V0_3';

ALTER TABLE platform_deployment_versions
    ADD COLUMN ai_fabric_framework_version VARCHAR(32) NOT NULL DEFAULT '0.3.1';

CREATE TABLE platform_entity_config_migration_audits (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    draft_id VARCHAR(64) NOT NULL,
    source_contract_version VARCHAR(64) NOT NULL,
    target_contract_version VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    before_hash VARCHAR(128) NOT NULL,
    after_hash VARCHAR(128),
    before_config_json TEXT NOT NULL,
    after_config_json TEXT,
    report_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_entity_config_migration_audit_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE
);

CREATE INDEX idx_platform_entity_config_migration_audit_draft
    ON platform_entity_config_migration_audits (draft_id, created_at);

-- H2 equivalent of production V128. REGEXP_REPLACE is used only in the test
-- schema because the production migration performs the update with PostgreSQL
-- JSONB operators.
UPDATE platform_marketplace_plugin_versions
SET manifest_json = REGEXP_REPLACE(
    manifest_json,
    '"product"\s*:\s*\{\s*"features"\s*:\s*\["embedding",\s*"search"\]\s*,\s*"auto-process"\s*:\s*false\s*,\s*"enable-search"\s*:\s*true\s*,\s*"auto-embedding"\s*:\s*true\s*,\s*"indexable"\s*:\s*true\s*\}',
    '"product":{"indexing":{"enabled":true,"max-characters":8000},"analysis":{"enabled":false,"after":[]},"searchable-fields":[{"name":"content","destinations":["SEMANTIC_SEARCH","RAG_CONTEXT"],"preprocessing":"CLEAN","max-length":8000,"priority":100,"required":true}],"metadata-fields":[{"name":"title","data-type":"STRING","description":"Human-readable dataset record title.","destinations":["VECTOR_METADATA","LLM_CONTEXT","API_RESPONSE"],"priority":90,"required":false,"sanitize-pii":false},{"name":"scope","data-type":"STRING","description":"Dataset-defined retrieval scope.","destinations":["VECTOR_METADATA","LLM_CONTEXT"],"priority":80,"required":false,"sanitize-pii":false},{"name":"tenantId","data-type":"ID","description":"Server-owned tenant isolation key.","destinations":["VECTOR_METADATA"],"priority":100,"required":true,"sanitize-pii":false}]}'
)
WHERE plugin_id IN ('mkp-data-commerce-catalog', 'mkp-data-shopify-catalog')
  AND version = '1.0.0'
  AND UPPER(status) = 'PUBLISHED';

UPDATE platform_marketplace_plugin_versions
SET manifest_json = REGEXP_REPLACE(
    manifest_json,
    '"faq-article"\s*:\s*\{\s*"entity-type"\s*:\s*"faq-article"\s*,\s*"auto-embedding"\s*:\s*true\s*,\s*"indexable"\s*:\s*true\s*,\s*"enable-search"\s*:\s*true\s*\}',
    '"faq-article":{"indexing":{"enabled":true,"max-characters":8000},"analysis":{"enabled":false,"after":[]},"searchable-fields":[{"name":"content","destinations":["SEMANTIC_SEARCH","RAG_CONTEXT"],"preprocessing":"CLEAN","max-length":8000,"priority":100,"required":true}],"metadata-fields":[{"name":"title","data-type":"STRING","description":"Human-readable dataset record title.","destinations":["VECTOR_METADATA","LLM_CONTEXT","API_RESPONSE"],"priority":90,"required":false,"sanitize-pii":false},{"name":"scope","data-type":"STRING","description":"Dataset-defined retrieval scope.","destinations":["VECTOR_METADATA","LLM_CONTEXT"],"priority":80,"required":false,"sanitize-pii":false},{"name":"tenantId","data-type":"ID","description":"Server-owned tenant isolation key.","destinations":["VECTOR_METADATA"],"priority":100,"required":true,"sanitize-pii":false}]}'
)
WHERE plugin_id = 'mkp-data-help-center'
  AND version = '1.0.0'
  AND UPPER(status) = 'PUBLISHED';

UPDATE platform_marketplace_plugin_versions
SET manifest_json = REGEXP_REPLACE(
    manifest_json,
    '"support-policy"\s*:\s*\{\s*"entity-type"\s*:\s*"support-policy"\s*,\s*"auto-embedding"\s*:\s*true\s*,\s*"indexable"\s*:\s*true\s*,\s*"enable-search"\s*:\s*true\s*\}',
    '"support-policy":{"indexing":{"enabled":true,"max-characters":8000},"analysis":{"enabled":false,"after":[]},"searchable-fields":[{"name":"content","destinations":["SEMANTIC_SEARCH","RAG_CONTEXT"],"preprocessing":"CLEAN","max-length":8000,"priority":100,"required":true}],"metadata-fields":[{"name":"title","data-type":"STRING","description":"Human-readable dataset record title.","destinations":["VECTOR_METADATA","LLM_CONTEXT","API_RESPONSE"],"priority":90,"required":false,"sanitize-pii":false},{"name":"scope","data-type":"STRING","description":"Dataset-defined retrieval scope.","destinations":["VECTOR_METADATA","LLM_CONTEXT"],"priority":80,"required":false,"sanitize-pii":false},{"name":"tenantId","data-type":"ID","description":"Server-owned tenant isolation key.","destinations":["VECTOR_METADATA"],"priority":100,"required":true,"sanitize-pii":false}]}'
)
WHERE plugin_id IN ('mkp-data-policy-folder', 'mkp-data-shopify-policies')
  AND version = '1.0.0'
  AND UPPER(status) = 'PUBLISHED';
