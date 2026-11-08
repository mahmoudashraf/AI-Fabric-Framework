ALTER TABLE platform_marketplace_plugins ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE platform_marketplace_plugins ADD COLUMN draft_manifest_json TEXT;
ALTER TABLE platform_marketplace_plugins ADD COLUMN draft_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_marketplace_plugins ADD COLUMN catalogue_family VARCHAR(255);
ALTER TABLE platform_marketplace_plugins ADD COLUMN catalogue_audience VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE platform_marketplace_plugins ADD COLUMN adapter_contract VARCHAR(255);

-- Preserve V236's ownership and source documents before retiring its separate draft representation.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM platform_plugin_definition_drafts d
        JOIN platform_marketplace_plugins p ON p.id = d.plugin_id
        WHERE p.owner_customer_id IS NOT NULL AND p.owner_customer_id <> d.owner_customer_id
    ) THEN
        RAISE EXCEPTION 'Private plugin draft ownership conflicts with its definition; explicit reconciliation is required';
    END IF;
END $$;

UPDATE platform_marketplace_plugins p
SET owner_customer_id = d.owner_customer_id,
    draft_manifest_json = d.manifest_source,
    draft_revision = d.revision,
    display_name = d.name,
    updated_at = GREATEST(p.updated_at, d.updated_at)
FROM platform_plugin_definition_drafts d
WHERE p.id = d.plugin_id;

UPDATE platform_marketplace_plugins p
SET draft_manifest_json = latest.manifest_json
FROM (
    SELECT DISTINCT ON (plugin_id) plugin_id, manifest_json
    FROM platform_marketplace_plugin_versions
    WHERE status = 'PUBLISHED'
    ORDER BY plugin_id, published_at DESC, created_at DESC, id DESC
) latest
WHERE p.id = latest.plugin_id AND p.owner_customer_id IS NOT NULL AND p.draft_manifest_json IS NULL;

UPDATE platform_marketplace_plugins SET visibility = 'CUSTOMER_PRIVATE' WHERE owner_customer_id IS NOT NULL;
UPDATE platform_marketplace_plugins SET status = 'ARCHIVED'
WHERE owner_customer_id IS NOT NULL AND plugin_type <> 'DATA';

ALTER TABLE platform_marketplace_plugins ADD CONSTRAINT marketplace_plugin_owner_visibility CHECK (
    (visibility = 'PUBLIC' AND owner_customer_id IS NULL) OR
    (visibility = 'CUSTOMER_PRIVATE' AND owner_customer_id IS NOT NULL)
);
DROP TABLE platform_plugin_definition_drafts;

-- Presentation is curated by exact catalogue identity, never by title/description matching.
UPDATE platform_marketplace_plugins SET catalogue_family = 'DOCUMENT_STORAGE', adapter_contract = 'S3_COMPATIBLE'
WHERE id IN ('mkp-data-document-knowledge-s3', 'mkp-data-dealership-knowledge-v1', 'mkp-data-ai-support-documents');
UPDATE platform_marketplace_plugins SET catalogue_audience = 'DEMO'
WHERE id IN ('mkp-data-help-center', 'mkp-data-policy-folder', 'mkp-data-shopify-catalog', 'mkp-data-shopify-policies', 'mkp-data-document-knowledge-mounted-demo',
    'mkp-data-commerce-catalog', 'mkp-data-dealership-knowledge-mounted-demo-v1', 'mkp-data-simulated-dealership-stock-v1');
UPDATE platform_marketplace_plugins SET catalogue_family = 'HTTP_JSON', adapter_contract = 'HTTP_JSON'
WHERE id IN ('mkp-data-dealership-external-stock-v1', 'mkp-data-autotrader-dealership-stock-v1');
UPDATE platform_marketplace_plugins SET catalogue_family = 'HTTP_JSON', adapter_contract = 'HTTP_JSON'
WHERE id = 'mkp-data-simulated-dealership-stock-v1';
UPDATE platform_marketplace_plugins SET catalogue_family = 'SQL_QUERY', adapter_contract = 'SQL_QUERY'
WHERE id = 'mkp-data-commerce-catalog';
UPDATE platform_marketplace_plugins SET catalogue_family = 'DOCUMENT_STORAGE', adapter_contract = 'MOUNTED_DOCUMENT_FOLDER'
WHERE id IN ('mkp-data-document-knowledge-mounted-demo', 'mkp-data-dealership-knowledge-mounted-demo-v1');
