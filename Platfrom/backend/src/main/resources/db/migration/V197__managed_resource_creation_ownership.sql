CREATE TABLE platform_managed_resource_ownership_mutex (
    id INTEGER PRIMARY KEY
);
INSERT INTO platform_managed_resource_ownership_mutex(id) VALUES (1);

CREATE TABLE platform_managed_resource_ownership (
    id VARCHAR(64) PRIMARY KEY,
    location_key VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL,
    resource_kind VARCHAR(32) NOT NULL,
    parent_identity VARCHAR(1024) NOT NULL,
    resource_name VARCHAR(255) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN ('CREATE_PENDING','OWNED','DELETE_PENDING','DELETED')),
    resource_identity VARCHAR(2048),
    endpoint VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_managed_ownership_evidence CHECK (
        (state = 'CREATE_PENDING' AND resource_identity IS NULL AND endpoint IS NULL)
        OR (state IN ('OWNED','DELETE_PENDING','DELETED') AND resource_identity IS NOT NULL)
    )
);
CREATE INDEX ix_managed_ownership_scope ON platform_managed_resource_ownership(deployment_id,target_profile_id);
CREATE INDEX ix_managed_ownership_identity ON platform_managed_resource_ownership(provider,resource_kind,resource_identity);
CREATE INDEX ix_managed_ownership_parent ON platform_managed_resource_ownership(provider,parent_identity,state);
