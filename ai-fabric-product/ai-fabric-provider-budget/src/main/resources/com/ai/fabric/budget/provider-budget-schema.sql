-- Owning application must incorporate this schema into its reviewed Flyway migration.
-- This library never executes DDL. PostgreSQL and H2 PostgreSQL mode are supported.
CREATE TABLE provider_budget_approval (
    approval_id VARCHAR(128) PRIMARY KEY,
    customer_id VARCHAR(256) NOT NULL,
    deployment_id VARCHAR(256) NOT NULL,
    target_profile_id VARCHAR(256) NOT NULL,
    environment_name VARCHAR(256) NOT NULL,
    credential_identity VARCHAR(256) NOT NULL,
    rate_card_reference VARCHAR(256) NOT NULL,
    max_chat_attempts BIGINT NOT NULL CHECK (max_chat_attempts >= 0),
    max_total_tokens BIGINT NOT NULL CHECK (max_total_tokens >= 0),
    max_cost_nano_usd BIGINT NOT NULL CHECK (max_cost_nano_usd >= 0),
    expires_second BIGINT NOT NULL,
    expires_nano INTEGER NOT NULL CHECK (expires_nano BETWEEN 0 AND 999999999),
    chat_attempts BIGINT NOT NULL DEFAULT 0 CHECK (chat_attempts >= 0),
    provider_attempts BIGINT NOT NULL DEFAULT 0 CHECK (provider_attempts >= 0),
    accounted_input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (accounted_input_tokens >= 0),
    accounted_output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (accounted_output_tokens >= 0),
    accounted_cost_nano_usd BIGINT NOT NULL DEFAULT 0 CHECK (accounted_cost_nano_usd >= 0),
    stopped BOOLEAN NOT NULL DEFAULT FALSE,
    stop_reason VARCHAR(512),
    CHECK ((stopped AND stop_reason IS NOT NULL) OR (NOT stopped AND stop_reason IS NULL)),
    CHECK (chat_attempts <= max_chat_attempts),
    CHECK (accounted_input_tokens <= max_total_tokens - accounted_output_tokens),
    CHECK (accounted_cost_nano_usd <= max_cost_nano_usd)
);

CREATE TABLE provider_budget_chat_ingress (
    approval_id VARCHAR(128) NOT NULL REFERENCES provider_budget_approval(approval_id),
    server_attempt_id VARCHAR(36) NOT NULL,
    ordinal BIGINT NOT NULL CHECK (ordinal > 0),
    PRIMARY KEY (approval_id, server_attempt_id),
    UNIQUE (approval_id, ordinal)
);

CREATE TABLE provider_budget_attempt (
    approval_id VARCHAR(128) NOT NULL REFERENCES provider_budget_approval(approval_id),
    attempt_id VARCHAR(36) NOT NULL,
    bound_input_tokens BIGINT NOT NULL CHECK (bound_input_tokens >= 0),
    bound_output_tokens BIGINT NOT NULL CHECK (bound_output_tokens >= 0),
    bound_cost_nano_usd BIGINT NOT NULL CHECK (bound_cost_nano_usd >= 0),
    attempt_state VARCHAR(16) NOT NULL CHECK (attempt_state IN ('RESERVED', 'AMBIGUOUS', 'SETTLED')),
    actual_input_tokens BIGINT CHECK (actual_input_tokens >= 0),
    actual_output_tokens BIGINT CHECK (actual_output_tokens >= 0),
    actual_cost_nano_usd BIGINT CHECK (actual_cost_nano_usd >= 0),
    evidence_reference VARCHAR(512),
    ambiguity_reason VARCHAR(512),
    PRIMARY KEY (approval_id, attempt_id),
    CHECK ((attempt_state = 'SETTLED' AND actual_input_tokens IS NOT NULL AND actual_output_tokens IS NOT NULL
            AND actual_cost_nano_usd IS NOT NULL AND evidence_reference IS NOT NULL
            AND actual_input_tokens <= bound_input_tokens AND actual_output_tokens <= bound_output_tokens
            AND actual_cost_nano_usd <= bound_cost_nano_usd)
        OR (attempt_state <> 'SETTLED' AND actual_input_tokens IS NULL AND actual_output_tokens IS NULL
            AND actual_cost_nano_usd IS NULL AND evidence_reference IS NULL)),
    CHECK (attempt_state <> 'AMBIGUOUS' OR ambiguity_reason IS NOT NULL)
);
