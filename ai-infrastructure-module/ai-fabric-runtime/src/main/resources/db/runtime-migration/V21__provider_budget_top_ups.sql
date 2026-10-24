-- Preserve immutable approval terms while existing max_* columns enforce effective ceilings.
ALTER TABLE provider_budget_approval ADD COLUMN original_chat_attempts BIGINT;
ALTER TABLE provider_budget_approval ADD COLUMN original_total_tokens BIGINT;
ALTER TABLE provider_budget_approval ADD COLUMN original_cost_nano_usd BIGINT;
UPDATE provider_budget_approval SET original_chat_attempts=max_chat_attempts,
    original_total_tokens=max_total_tokens, original_cost_nano_usd=max_cost_nano_usd;
ALTER TABLE provider_budget_approval ALTER COLUMN original_chat_attempts SET NOT NULL;
ALTER TABLE provider_budget_approval ALTER COLUMN original_total_tokens SET NOT NULL;
ALTER TABLE provider_budget_approval ALTER COLUMN original_cost_nano_usd SET NOT NULL;
ALTER TABLE provider_budget_approval ADD CONSTRAINT provider_budget_original_limits
    CHECK (original_chat_attempts >= 0 AND original_chat_attempts <= max_chat_attempts
        AND original_total_tokens >= 0 AND original_total_tokens <= max_total_tokens
        AND original_cost_nano_usd >= 0 AND original_cost_nano_usd <= max_cost_nano_usd);

CREATE TABLE provider_budget_top_up (
    top_up_id VARCHAR(128) PRIMARY KEY,
    approval_id VARCHAR(128) NOT NULL REFERENCES provider_budget_approval(approval_id),
    store_uuid VARCHAR(36) NOT NULL,
    runtime_fingerprint VARCHAR(64) NOT NULL,
    previous_chat BIGINT NOT NULL CHECK (previous_chat >= 0),
    previous_tokens BIGINT NOT NULL CHECK (previous_tokens >= 0),
    previous_cost BIGINT NOT NULL CHECK (previous_cost >= 0),
    additional_chat BIGINT NOT NULL CHECK (additional_chat >= 0),
    additional_tokens BIGINT NOT NULL CHECK (additional_tokens >= 0),
    additional_cost BIGINT NOT NULL CHECK (additional_cost >= 0),
    authorized_by VARCHAR(256) NOT NULL,
    approval_evidence_ref VARCHAR(255) NOT NULL UNIQUE,
    payer_ref VARCHAR(255) NOT NULL,
    reason_sha256 VARCHAR(64) NOT NULL,
    authorized_second BIGINT NOT NULL,
    authorized_nano INTEGER NOT NULL CHECK (authorized_nano BETWEEN 0 AND 999999999),
    UNIQUE (approval_id, previous_chat, previous_tokens, previous_cost),
    CHECK (additional_chat > 0 OR additional_tokens > 0 OR additional_cost > 0)
);
