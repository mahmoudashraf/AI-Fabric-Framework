ALTER TABLE platform_customer_offer_deployments
    ADD COLUMN bootstrap_target_profile_id VARCHAR(64) NOT NULL;

ALTER TABLE platform_customer_offer_deployments
    ADD CONSTRAINT fk_customer_offer_deployment_bootstrap_target
        FOREIGN KEY (bootstrap_target_profile_id) REFERENCES deployment_target_profiles(id);

CREATE INDEX idx_customer_offer_deployments_bootstrap_target
    ON platform_customer_offer_deployments(bootstrap_target_profile_id);
