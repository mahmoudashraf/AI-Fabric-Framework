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

-- Declarative current schema for test-owned/bootstrap tooling; production uses additive V30.
CREATE TABLE runtime_provider_budget_pricing_amendment (
    amendment_id VARCHAR(64) PRIMARY KEY,
    approval_id VARCHAR(128) NOT NULL UNIQUE REFERENCES provider_budget_approval(approval_id),
    store_uuid VARCHAR(36) NOT NULL,
    runtime_fingerprint VARCHAR(64) NOT NULL,
    original_rate_card_reference VARCHAR(256) NOT NULL,
    additional_rate_card_reference VARCHAR(256) NOT NULL,
    authorized_by VARCHAR(256) NOT NULL,
    approval_evidence_ref VARCHAR(255) NOT NULL UNIQUE,
    reason_sha256 VARCHAR(64) NOT NULL,
    authorized_second BIGINT NOT NULL,
    authorized_nano INTEGER NOT NULL CHECK (authorized_nano BETWEEN 0 AND 999999999),
    CHECK (original_rate_card_reference = 'openai-gpt4omini-gpt54mini-embedding3small-20261024-v2'),
    CHECK (additional_rate_card_reference = 'openai-gpt62astra-text-short-20261125-v3')
);
