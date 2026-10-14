ALTER TABLE platform_deployment_releases
    ADD COLUMN runtime_authority_snapshot_id VARCHAR(64);

ALTER TABLE platform_deployment_releases
    ADD COLUMN runtime_authority_fingerprint VARCHAR(64);

ALTER TABLE platform_deployment_releases
    ADD CONSTRAINT fk_release_runtime_authority_snapshot
        FOREIGN KEY (runtime_authority_snapshot_id)
        REFERENCES platform_runtime_authority_snapshots(id);
