-- Legacy receipts keep their exact content and have no inferred customer ownership.
ALTER TABLE dealership_lead_request ADD COLUMN receiver_binding_id VARCHAR(80);
ALTER TABLE dealership_lead_request ADD COLUMN receiver_binding_fingerprint VARCHAR(64);
ALTER TABLE dealership_lead_request ADD COLUMN receiver_customer_id VARCHAR(64);
ALTER TABLE dealership_lead_request ADD COLUMN receiver_tenant_id VARCHAR(64);
ALTER TABLE dealership_lead_request ADD COLUMN receiver_deployment_id VARCHAR(64);
ALTER TABLE dealership_lead_request ADD COLUMN receiver_advertiser_id VARCHAR(160);
ALTER TABLE dealership_lead_request ADD COLUMN receiver_request_key VARCHAR(180);

ALTER TABLE dealership_lead_request ADD CONSTRAINT ck_lead_receiver_scope CHECK (
    (receiver_binding_id IS NULL AND receiver_binding_fingerprint IS NULL AND receiver_customer_id IS NULL
     AND receiver_tenant_id IS NULL AND receiver_deployment_id IS NULL AND receiver_advertiser_id IS NULL
     AND receiver_request_key IS NULL)
    OR
    (receiver_binding_id IS NOT NULL AND CHAR_LENGTH(TRIM(receiver_binding_id)) > 0
     AND receiver_binding_fingerprint IS NOT NULL AND CHAR_LENGTH(receiver_binding_fingerprint) = 64
     AND receiver_customer_id IS NOT NULL AND CHAR_LENGTH(TRIM(receiver_customer_id)) > 0
     AND receiver_tenant_id IS NOT NULL AND CHAR_LENGTH(TRIM(receiver_tenant_id)) > 0
     AND receiver_deployment_id IS NOT NULL AND CHAR_LENGTH(TRIM(receiver_deployment_id)) > 0
     AND receiver_advertiser_id IS NOT NULL AND CHAR_LENGTH(TRIM(receiver_advertiser_id)) > 0
     AND receiver_request_key IS NOT NULL AND CHAR_LENGTH(TRIM(receiver_request_key)) > 0
     AND provider_stock_id IS NOT NULL AND provider_advertiser_id IS NOT NULL
     AND receiver_advertiser_id = provider_advertiser_id)
);

-- New storage keys include the full binding fingerprint; the old global constraint and keys remain intact.
CREATE UNIQUE INDEX uq_lead_receiver_request
    ON dealership_lead_request (receiver_binding_fingerprint, receiver_request_key);
