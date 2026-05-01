update partner_store_assignments
set permissions_json = '["STORE_READ","CATALOG_READ","PRODUCT_CONFIG_READ","PRODUCT_CONFIG_WRITE","PRODUCT_CONFIG_PUBLISH","STOREFRONT_SURFACE_CONTROL","KNOWLEDGE_SOURCE_CONTROL","KNOWLEDGE_SYNC_TRIGGER","APP_OWNED_CONTENT_WRITE","VERIFICATION_READ","VERIFICATION_RUN","EVIDENCE_READ","EVIDENCE_EXPORT","ESCALATION_CREATE","ESCALATION_REPLY","SUPPORT_MANAGE","AGGREGATE_ANALYTICS_READ","PRODUCT_PAUSE_RESUME"]',
    updated_at = current_timestamp
where status = 'ACTIVE'
  and (
    permissions_json not like '%PRODUCT_CONFIG_READ%'
    or permissions_json not like '%PRODUCT_CONFIG_WRITE%'
    or permissions_json not like '%PRODUCT_CONFIG_PUBLISH%'
    or permissions_json not like '%STOREFRONT_SURFACE_CONTROL%'
    or permissions_json not like '%KNOWLEDGE_SOURCE_CONTROL%'
    or permissions_json not like '%KNOWLEDGE_SYNC_TRIGGER%'
    or permissions_json not like '%APP_OWNED_CONTENT_WRITE%'
    or permissions_json not like '%SUPPORT_MANAGE%'
    or permissions_json not like '%PRODUCT_PAUSE_RESUME%'
  );
