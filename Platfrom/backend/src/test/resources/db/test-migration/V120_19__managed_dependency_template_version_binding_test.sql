ALTER TABLE deployment_managed_dependency_specs
    ADD COLUMN template_version_id VARCHAR(64);

CREATE UNIQUE INDEX uq_dependency_spec_template_version
    ON deployment_managed_dependency_specs(template_version_id);
