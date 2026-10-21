-- Append-only time authority; original approval terms, binding and usage rows stay unchanged.
CREATE TABLE provider_budget_validity_extension (
    extension_id VARCHAR(128) PRIMARY KEY,
    approval_id VARCHAR(128) NOT NULL REFERENCES provider_budget_approval(approval_id),
    store_uuid VARCHAR(36) NOT NULL,
    runtime_fingerprint VARCHAR(64) NOT NULL,
    previous_second BIGINT NOT NULL,
    previous_nano INTEGER NOT NULL CHECK (previous_nano BETWEEN 0 AND 999999999),
    expires_second BIGINT NOT NULL,
    expires_nano INTEGER NOT NULL CHECK (expires_nano BETWEEN 0 AND 999999999),
    reason_sha256 VARCHAR(64) NOT NULL,
    authorized_second BIGINT NOT NULL,
    authorized_nano INTEGER NOT NULL CHECK (authorized_nano BETWEEN 0 AND 999999999),
    UNIQUE (approval_id, previous_second, previous_nano),
    CHECK (expires_second > previous_second OR (expires_second = previous_second AND expires_nano > previous_nano))
);
