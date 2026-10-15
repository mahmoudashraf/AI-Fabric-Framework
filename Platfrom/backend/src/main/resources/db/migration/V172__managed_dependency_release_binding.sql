ALTER TABLE deployment_managed_dependency_graphs
    ADD COLUMN release_id VARCHAR(64);

CREATE INDEX ix_dependency_release
    ON deployment_managed_dependency_graphs(release_id);
