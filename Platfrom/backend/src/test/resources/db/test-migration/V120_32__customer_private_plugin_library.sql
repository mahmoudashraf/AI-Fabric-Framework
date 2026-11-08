ALTER TABLE platform_marketplace_plugins ADD COLUMN owner_customer_id VARCHAR(64) REFERENCES platform_customers(id);
ALTER TABLE platform_marketplace_plugins ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE platform_marketplace_plugins ADD COLUMN draft_manifest_json TEXT;
ALTER TABLE platform_marketplace_plugins ADD COLUMN draft_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_marketplace_plugins ADD COLUMN catalogue_family VARCHAR(255);
ALTER TABLE platform_marketplace_plugins ADD COLUMN catalogue_audience VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE platform_marketplace_plugins ADD COLUMN adapter_contract VARCHAR(255);
ALTER TABLE platform_marketplace_plugins ADD CONSTRAINT marketplace_plugin_owner_visibility CHECK (
    (visibility = 'PUBLIC' AND owner_customer_id IS NULL) OR
    (visibility = 'CUSTOMER_PRIVATE' AND owner_customer_id IS NOT NULL)
);
CREATE INDEX idx_marketplace_plugins_owner ON platform_marketplace_plugins(owner_customer_id);
