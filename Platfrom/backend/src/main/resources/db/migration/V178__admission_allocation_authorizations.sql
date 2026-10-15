CREATE TABLE deployment_admission_authorizations (
    customer_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    generation VARCHAR(64) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    version_id VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    context_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (customer_id,deployment_id,target_profile_id,operation_key)
);

CREATE TABLE deployment_admission_release_bindings (
    release_id VARCHAR(64) PRIMARY KEY,
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    generation VARCHAR(64) NOT NULL,
    authorization_key VARCHAR(128) NOT NULL,
    version_id VARCHAR(64) NOT NULL,
    version_config_hash VARCHAR(255) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (reservation_id,generation,authorization_key)
);

CREATE TABLE deployment_admission_activity_watermarks (
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    release_id VARCHAR(64) NOT NULL,
    activity VARCHAR(40) NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (reservation_id,release_id,activity)
);

CREATE TABLE deployment_admission_extension_commands (
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    operation_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    PRIMARY KEY (reservation_id,operation_key)
);
