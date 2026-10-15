ALTER TABLE deployment_admission_mutex
    ADD COLUMN lifecycle_cursor VARCHAR(64);

CREATE TABLE deployment_admission_notice_deliveries (
    delivery_key VARCHAR(160) PRIMARY KEY,
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    notice_cycle VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('CONSOLE','OWNER_EMAIL')),
    transport_reference VARCHAR(255) NOT NULL,
    recipient_reference VARCHAR(255) NOT NULL,
    recipient_hash VARCHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PENDING','PUBLISHED','SENT','FAILED','OUTCOME_UNKNOWN')),
    attempt_count INTEGER NOT NULL CHECK (attempt_count > 0),
    safe_payload_json TEXT NOT NULL,
    provider_reference VARCHAR(255),
    failure_code VARCHAR(64),
    claim_token VARCHAR(64),
    claim_expires_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    resolution_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_admission_notice_delivery UNIQUE (reservation_id, notice_cycle, channel)
);

CREATE INDEX ix_admission_notice_delivery_state
    ON deployment_admission_notice_deliveries (state, updated_at);
