ALTER TABLE platform_secrets ADD COLUMN target_profile_id VARCHAR(128);

ALTER TABLE platform_secrets ADD CONSTRAINT platform_secret_target_requires_deployment
    CHECK (target_profile_id IS NULL OR
        (deployment_id IS NOT NULL AND scope_type IS NOT NULL AND scope_type = 'DEPLOYMENT_MANAGED'
            AND owner_type IS NOT NULL AND owner_type = 'PLATFORM_MANAGED' AND managed_by_platform = TRUE));
