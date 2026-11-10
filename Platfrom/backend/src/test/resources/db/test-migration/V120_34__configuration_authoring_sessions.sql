CREATE TABLE platform_authoring_sessions (
    id VARCHAR(64) PRIMARY KEY,
    actor_id VARCHAR(256) NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    resource_kind VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    task VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_turn_id VARCHAR(64),
    deadline TIMESTAMP WITH TIME ZONE,
    encrypted_content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_authoring_owner_resource ON platform_authoring_sessions(actor_id, resource_kind, resource_id, updated_at);
CREATE INDEX idx_authoring_expiry ON platform_authoring_sessions(expires_at);
CREATE INDEX idx_authoring_active ON platform_authoring_sessions(customer_id, status, deadline);
