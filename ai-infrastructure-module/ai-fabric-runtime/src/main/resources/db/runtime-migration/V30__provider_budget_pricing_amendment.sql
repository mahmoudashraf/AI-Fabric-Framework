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
