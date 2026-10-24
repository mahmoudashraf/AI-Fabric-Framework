CREATE TABLE deployment_operations_lock (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);
INSERT INTO deployment_operations_lock (id) VALUES (1);

CREATE TABLE deployment_operations_assessment (
    command_id VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    assessment_id VARCHAR(64) NOT NULL UNIQUE,
    customer_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    mode VARCHAR(16) NOT NULL CHECK (mode IN ('OBSERVE', 'ENFORCE')),
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('ALLOW', 'DENY', 'UNAVAILABLE')),
    reason VARCHAR(128) NOT NULL,
    policy_revision VARCHAR(128) NOT NULL,
    request_json TEXT NOT NULL,
    response_json TEXT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_outcome_json TEXT
);
CREATE INDEX deployment_operations_assessment_scope_idx
    ON deployment_operations_assessment (deployment_id, target_profile_id, observed_at);

CREATE TABLE deployment_operations_allocation (
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    command_id VARCHAR(128) NOT NULL REFERENCES deployment_operations_assessment (command_id),
    resource_demand_json TEXT NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('HOLD', 'ACTIVE', 'UNCONFIRMED', 'STOPPED')),
    hold_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (deployment_id, target_profile_id)
);
CREATE INDEX deployment_operations_allocation_target_idx
    ON deployment_operations_allocation (target_profile_id, state);

CREATE TABLE deployment_operations_outcome (
    outcome_id VARCHAR(64) PRIMARY KEY,
    command_id VARCHAR(128) NOT NULL REFERENCES deployment_operations_assessment (command_id),
    payload_sha256 VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'FAILED', 'STOPPED')),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome_json TEXT NOT NULL
);
