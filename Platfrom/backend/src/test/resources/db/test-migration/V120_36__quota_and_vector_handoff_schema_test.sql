-- H2 mirrors of production V202 and V233, including their ownership constraints.
ALTER TABLE platform_customers
    ADD COLUMN infrastructure_environment_limit integer NOT NULL DEFAULT 2
    CHECK (infrastructure_environment_limit >= 0);

CREATE TABLE deployment_unused_vector_handoffs (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    operation_key VARCHAR(128) NOT NULL,
    command_fingerprint VARCHAR(64) NOT NULL,
    binding_id VARCHAR(64) NOT NULL REFERENCES deployment_provider_resource_handles(id),
    actor_id VARCHAR(255) NOT NULL,
    approval_reference VARCHAR(255) NOT NULL,
    handed_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    CONSTRAINT uq_unused_vector_handoff_operation UNIQUE (deployment_id, target_profile_id, operation_key),
    CONSTRAINT uq_unused_vector_handoff_binding UNIQUE (binding_id)
);
