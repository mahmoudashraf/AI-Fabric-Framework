-- Lifetime financial credits are deliberately independent from releasable R7 physical capacity.
-- No deployment/customer/runtime-DB FK or cascading retirement cleanup may erase these records.
-- No balances, campaigns, evidence references or grants are seeded.
CREATE TABLE provider_usage_grant_mutex (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO provider_usage_grant_mutex(id) VALUES (1);

CREATE TABLE provider_usage_grants (
    id VARCHAR(96) PRIMARY KEY,
    campaign_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    environment_name VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    allocated_chat_attempts BIGINT NOT NULL DEFAULT 0 CHECK (allocated_chat_attempts >= 0),
    allocated_total_tokens BIGINT NOT NULL DEFAULT 0 CHECK (allocated_total_tokens >= 0),
    allocated_cost_nano_usd BIGINT NOT NULL DEFAULT 0 CHECK (allocated_cost_nano_usd >= 0),
    approved_by VARCHAR(255) NOT NULL,
    approved_role VARCHAR(64) NOT NULL CHECK (approved_role = 'PLATFORM_ADMIN'),
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (campaign_id, customer_id, target_profile_id, environment_name)
);

CREATE TABLE provider_usage_allocations (
    approval_id VARCHAR(96) PRIMARY KEY,
    grant_id VARCHAR(96) NOT NULL REFERENCES provider_usage_grants(id),
    customer_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    environment_name VARCHAR(64) NOT NULL,
    approval_fingerprint VARCHAR(64) NOT NULL,
    runtime_allocation_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    initialization_state VARCHAR(16) NOT NULL CHECK (initialization_state IN ('UNINITIALIZED', 'INITIALIZED', 'ACTIVE')),
    store_uuid VARCHAR(36),
    initialization_fingerprint VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    initialized_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (grant_id, deployment_id, target_profile_id),
    CHECK ((initialization_state = 'UNINITIALIZED' AND store_uuid IS NULL AND initialization_fingerprint IS NULL
                AND initialized_at IS NULL AND activated_at IS NULL)
        OR (initialization_state = 'INITIALIZED' AND store_uuid IS NOT NULL AND initialization_fingerprint IS NOT NULL
                AND initialization_fingerprint = runtime_allocation_fingerprint AND initialized_at IS NOT NULL AND activated_at IS NULL)
        OR (initialization_state = 'ACTIVE' AND store_uuid IS NOT NULL AND initialization_fingerprint IS NOT NULL
                AND initialization_fingerprint = runtime_allocation_fingerprint AND initialized_at IS NOT NULL AND activated_at IS NOT NULL))
);

CREATE TABLE provider_usage_allowance_events (
    id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(96) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_provider_usage_allowance_customer ON provider_usage_grants(customer_id, target_profile_id);
CREATE INDEX ix_provider_usage_allowance_audit ON provider_usage_allowance_events(subject_id, created_at);
