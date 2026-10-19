CREATE TABLE platform_managed_service_ownership_mutex (
    id INTEGER PRIMARY KEY
);
INSERT INTO platform_managed_service_ownership_mutex(id) VALUES (1);

-- Deliberately empty on upgrade: historical registry bindings are not creation evidence.
CREATE TABLE platform_managed_service_ownership (
    id VARCHAR(64) PRIMARY KEY,
    registry_id VARCHAR(64) NOT NULL,
    service_ref VARCHAR(128) NOT NULL,
    service_kind VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    generation BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL CHECK (provider = 'coolify'),
    endpoint VARCHAR(2048) NOT NULL,
    parent_identity VARCHAR(1024) NOT NULL,
    resource_name VARCHAR(255) NOT NULL,
    resource_uuid VARCHAR(128),
    credential_secret_name VARCHAR(255) UNIQUE,
    state VARCHAR(32) NOT NULL CHECK (state IN ('CREATE_PENDING','OWNED','MUTATION_PENDING','DELETE_PENDING','DELETED')),
    active_registry_id VARCHAR(64) UNIQUE,
    active_location_key VARCHAR(64) UNIQUE,
    identity_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_service_ownership_generation UNIQUE(registry_id,generation),
    CONSTRAINT ck_service_ownership_creation CHECK (
        (state = 'CREATE_PENDING' AND resource_uuid IS NULL AND identity_key IS NULL)
        OR (state <> 'CREATE_PENDING' AND resource_uuid IS NOT NULL AND identity_key IS NOT NULL)
    ),
    CONSTRAINT ck_service_ownership_active CHECK (
        (state = 'DELETED' AND active_registry_id IS NULL AND active_location_key IS NULL)
        OR (state <> 'DELETED' AND active_registry_id = registry_id
            AND active_registry_id IS NOT NULL AND active_location_key IS NOT NULL)
    )
);
CREATE INDEX ix_service_ownership_registry ON platform_managed_service_ownership(registry_id,generation);
CREATE INDEX ix_service_ownership_resource ON platform_managed_service_ownership(provider,resource_uuid);
