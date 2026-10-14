CREATE TABLE platform_customer_offers (
    id VARCHAR(64) PRIMARY KEY,
    definition_json TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    review_reference VARCHAR(255) NOT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE platform_customer_offer_entitlements (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES platform_customers(id),
    offer_id VARCHAR(64) NOT NULL REFERENCES platform_customer_offers(id),
    owner_user_id VARCHAR(64) NOT NULL REFERENCES platform_users(id),
    invitation_reference VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'REVOKED')),
    revision BIGINT NOT NULL CHECK (revision > 0),
    row_version BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_customer_offer_entitlement UNIQUE (customer_id, offer_id)
);
CREATE TABLE platform_customer_entitlement_commands (
    id VARCHAR(64) PRIMARY KEY,
    entitlement_id VARCHAR(64) NOT NULL REFERENCES platform_customer_offer_entitlements(id),
    operation_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    command_type VARCHAR(16) NOT NULL CHECK (command_type IN ('INVITE', 'ACCEPT', 'REVOKE')),
    actor_id VARCHAR(255) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_customer_entitlement_command UNIQUE (entitlement_id, operation_key)
);
CREATE INDEX idx_customer_offer_entitlement_owner ON platform_customer_offer_entitlements(owner_user_id);

CREATE TABLE platform_customer_placement_evidence (
    id VARCHAR(64) PRIMARY KEY,
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    intent VARCHAR(32) NOT NULL CHECK (intent IN ('CUSTOMER_STAGING', 'CUSTOMER_PRODUCTION')),
    profile_fingerprint VARCHAR(64) NOT NULL,
    server_uuid VARCHAR(255) NOT NULL,
    server_address VARCHAR(255) NOT NULL,
    destination_uuid VARCHAR(255) NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (expires_at > checked_at)
);
CREATE INDEX idx_customer_placement_evidence_lookup
    ON platform_customer_placement_evidence(target_profile_id, intent, profile_fingerprint, checked_at DESC);

CREATE TABLE platform_customer_offer_deployments (
    deployment_id VARCHAR(64) PRIMARY KEY REFERENCES platform_deployments(id),
    customer_id VARCHAR(64) NOT NULL REFERENCES platform_customers(id),
    tenant_id VARCHAR(64) NOT NULL REFERENCES platform_customer_tenants(id),
    offer_id VARCHAR(64) NOT NULL REFERENCES platform_customer_offers(id),
    offer_fingerprint VARCHAR(64) NOT NULL,
    authorization_entitlement_revision BIGINT NOT NULL CHECK (authorization_entitlement_revision > 0),
    operation_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
