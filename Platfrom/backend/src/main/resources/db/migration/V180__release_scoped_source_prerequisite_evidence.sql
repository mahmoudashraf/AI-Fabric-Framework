CREATE TABLE deployment_source_prerequisite_evidence (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    release_id VARCHAR(64) NOT NULL REFERENCES deployment_admission_release_bindings(release_id),
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    allocation_generation VARCHAR(64) NOT NULL,
    scope_fingerprint VARCHAR(64) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (expires_at > checked_at)
);

CREATE INDEX deployment_source_prerequisite_scope
    ON deployment_source_prerequisite_evidence
    (customer_id,tenant_id,deployment_id,target_profile_id,release_id,scope_fingerprint,checked_at DESC);
