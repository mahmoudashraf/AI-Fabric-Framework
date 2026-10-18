ALTER TABLE platform_customer_offer_deployments
    ADD COLUMN bootstrap_target_profile_id VARCHAR(64);

WITH single_customer_staging_target AS (
    SELECT offer.id AS offer_id,
           MIN(target ->> 'targetProfileId') AS target_profile_id
    FROM platform_customer_offers offer
    CROSS JOIN LATERAL jsonb_array_elements(offer.definition_json::jsonb -> 'targets') target
    WHERE target ->> 'intent' = 'CUSTOMER_STAGING'
    GROUP BY offer.id
    HAVING COUNT(*) = 1
)
UPDATE platform_customer_offer_deployments association
SET bootstrap_target_profile_id = target.target_profile_id
FROM single_customer_staging_target target
WHERE target.offer_id = association.offer_id;

ALTER TABLE platform_customer_offer_deployments
    ALTER COLUMN bootstrap_target_profile_id SET NOT NULL,
    ADD CONSTRAINT fk_customer_offer_deployment_bootstrap_target
        FOREIGN KEY (bootstrap_target_profile_id) REFERENCES deployment_target_profiles(id);

CREATE INDEX idx_customer_offer_deployments_bootstrap_target
    ON platform_customer_offer_deployments(bootstrap_target_profile_id);
