CREATE TABLE ai_workspace_create_receipts (
    customer_id VARCHAR(64) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    installation_id VARCHAR(96),
    result_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (customer_id, operation_key),
    CONSTRAINT ai_workspace_create_receipt_completion CHECK (
        (installation_id IS NULL AND result_json IS NULL AND completed_at IS NULL)
        OR (installation_id IS NOT NULL AND result_json IS NOT NULL AND completed_at IS NOT NULL)
    )
);
