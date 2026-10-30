-- Safe context receipts and actual staff identity, never JWTs or a second transcript.
CREATE TABLE platform_support_desk_operations (
    id VARCHAR(64) PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL REFERENCES platform_support_cases(id),
    binding_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(96) NOT NULL,
    handover_id VARCHAR(128) NOT NULL,
    external_conversation_ref VARCHAR(160) NOT NULL,
    actor_issuer VARCHAR(512) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    actor_agent_id VARCHAR(128) NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('LOOKUP', 'RESOLVE')),
    receipt_key VARCHAR(64) UNIQUE,
    request_hash VARCHAR(64),
    resource_refs_json TEXT NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((operation = 'LOOKUP' AND receipt_key IS NULL AND response_json IS NULL)
        OR (operation = 'RESOLVE' AND receipt_key IS NOT NULL AND request_hash IS NOT NULL AND response_json IS NOT NULL))
);
CREATE INDEX support_desk_operations_case ON platform_support_desk_operations(case_id, created_at);
