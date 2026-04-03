CREATE TABLE platform_deployment_poc_import_runs (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    dataset_label VARCHAR(255) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    vector_space VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    record_count INTEGER NOT NULL,
    imported_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    error_message TEXT,
    created_by_actor_id VARCHAR(255) NOT NULL,
    created_by_display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_poc_import_runs_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE
);

CREATE INDEX idx_platform_deployment_poc_import_runs_deployment
    ON platform_deployment_poc_import_runs (deployment_id, created_at DESC);
