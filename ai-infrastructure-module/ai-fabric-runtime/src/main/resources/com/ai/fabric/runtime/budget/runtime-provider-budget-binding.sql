-- Parent must append this schema to its reviewed Flyway migration after the ledger schema.
-- Never execute this resource at runtime startup. Empty state grants no spending authority.
CREATE TABLE runtime_provider_budget_binding (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    binding_state VARCHAR(16) NOT NULL CHECK (binding_state IN ('UNINITIALIZED', 'INITIALIZING', 'INITIALIZED', 'ACTIVE')),
    store_uuid VARCHAR(36),
    approval_id VARCHAR(128),
    allocation_fingerprint VARCHAR(64),
    CHECK ((binding_state = 'UNINITIALIZED' AND store_uuid IS NULL AND approval_id IS NULL AND allocation_fingerprint IS NULL)
        OR (binding_state IN ('INITIALIZING', 'INITIALIZED', 'ACTIVE') AND store_uuid IS NOT NULL
            AND approval_id IS NOT NULL AND allocation_fingerprint IS NOT NULL))
);
INSERT INTO runtime_provider_budget_binding (singleton_id, binding_state) VALUES (1, 'UNINITIALIZED');
