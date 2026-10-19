-- Website inventory and provider stock are independently operated sources.
-- Legacy receipts retain their original reference and encrypted envelope unchanged.
ALTER TABLE dealership_lead_request DROP CONSTRAINT fk_lead_vehicle;

ALTER TABLE dealership_lead_request ADD COLUMN provider_stock_id VARCHAR(80);
ALTER TABLE dealership_lead_request ADD COLUMN provider_advertiser_id VARCHAR(160);

ALTER TABLE dealership_lead_request ADD CONSTRAINT ck_lead_provider_identity CHECK (
    (provider_stock_id IS NULL AND provider_advertiser_id IS NULL)
    OR (
        provider_stock_id IS NOT NULL AND provider_advertiser_id IS NOT NULL
        AND CHAR_LENGTH(TRIM(provider_stock_id)) > 0
        AND CHAR_LENGTH(TRIM(provider_advertiser_id)) > 0
        AND vehicle_id = provider_stock_id
    )
);

CREATE INDEX idx_dealership_lead_provider_stock
    ON dealership_lead_request (provider_advertiser_id, provider_stock_id);
