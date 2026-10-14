CREATE TABLE deployment_managed_dependency_specs (
    id VARCHAR(64) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    spec_json TEXT NOT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE deployment_managed_dependency_graphs (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    version_id VARCHAR(64) NOT NULL,
    spec_ref VARCHAR(64) NOT NULL REFERENCES deployment_managed_dependency_specs(id),
    spec_fingerprint VARCHAR(64) NOT NULL,
    binding_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    step VARCHAR(128) NOT NULL,
    failure_code VARCHAR(64),
    reservation_ref VARCHAR(255),
    worker_token VARCHAR(64),
    revision BIGINT NOT NULL DEFAULT 0,
    attempt INTEGER NOT NULL DEFAULT 0,
    spec_json TEXT NOT NULL,
    state_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_dependency_deployment_target UNIQUE (deployment_id, target_profile_id)
);
CREATE INDEX ix_dependency_status ON deployment_managed_dependency_graphs(status, updated_at);
CREATE INDEX ix_dependency_customer ON deployment_managed_dependency_graphs(customer_id, deployment_id);
