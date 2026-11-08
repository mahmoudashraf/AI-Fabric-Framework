ALTER TABLE platform_deployments
    ADD COLUMN environment_policy VARCHAR(32) NOT NULL DEFAULT 'UNRESTRICTED';

ALTER TABLE platform_deployments
    ADD CONSTRAINT ck_deployment_environment_policy
    CHECK (environment_policy IN ('UNRESTRICTED', 'STAGING_ONLY'));
