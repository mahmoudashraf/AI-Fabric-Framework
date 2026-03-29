ALTER TABLE platform_deployments
    ADD COLUMN source_repository_override TEXT;

ALTER TABLE platform_deployments
    ADD COLUMN source_branch_override TEXT;
