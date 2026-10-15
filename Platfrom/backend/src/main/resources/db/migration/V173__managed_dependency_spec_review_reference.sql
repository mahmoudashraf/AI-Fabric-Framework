ALTER TABLE deployment_managed_dependency_specs
    ADD COLUMN review_reference VARCHAR(255) NOT NULL DEFAULT 'legacy-reviewed-spec';

ALTER TABLE deployment_managed_dependency_specs
    ALTER COLUMN review_reference DROP DEFAULT;
