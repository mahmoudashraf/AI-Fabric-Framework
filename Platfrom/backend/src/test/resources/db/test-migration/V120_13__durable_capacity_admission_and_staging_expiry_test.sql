CREATE TABLE deployment_admission_mutex (
    id INTEGER PRIMARY KEY CHECK (id = 1), active_policy_ref VARCHAR(64)
);
INSERT INTO deployment_admission_mutex (id) VALUES (1);
CREATE TABLE deployment_admission_reviews (
    kind VARCHAR(16) NOT NULL, record_key VARCHAR(128) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL, reviewed_by VARCHAR(255) NOT NULL, reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (kind, record_key)
);
CREATE TABLE deployment_admission_grants (customer_id VARCHAR(64) PRIMARY KEY, record_ref VARCHAR(128) NOT NULL);
CREATE TABLE deployment_capacity_reservations (
    id VARCHAR(64) PRIMARY KEY, customer_id VARCHAR(64) NOT NULL, deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL, operation_key VARCHAR(128) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL,
    generation VARCHAR(64) NOT NULL, revision BIGINT NOT NULL CHECK (revision >= 0),
    state VARCHAR(32) NOT NULL CHECK (state IN ('RESERVED','PROVISIONING','ACTIVE','OUTCOME_UNKNOWN','CLEANUP_FAILED','RELEASED')),
    current_slot VARCHAR(160) UNIQUE, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, next_retry_at TIMESTAMP WITH TIME ZONE,
    expiry_state VARCHAR(32) NOT NULL, payload_json TEXT NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_admission_command UNIQUE (customer_id, deployment_id, target_profile_id, operation_key),
    CONSTRAINT ck_admission_slot CHECK ((state = 'RELEASED' AND current_slot IS NULL) OR (state <> 'RELEASED' AND current_slot IS NOT NULL))
);
CREATE INDEX ix_admission_due ON deployment_capacity_reservations (expiry_state, expires_at, next_retry_at);
CREATE INDEX ix_admission_capacity ON deployment_capacity_reservations (state, customer_id, target_profile_id);
CREATE TABLE deployment_admission_notices (
    delivery_key VARCHAR(160) PRIMARY KEY, reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    notice_cycle VARCHAR(64) NOT NULL, channel VARCHAR(16) NOT NULL CHECK (channel IN ('CONSOLE','OWNER_EMAIL')),
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL, evidence_reference VARCHAR(255) NOT NULL,
    CONSTRAINT uq_admission_notice UNIQUE (reservation_id, notice_cycle, channel)
);
CREATE TABLE deployment_admission_events (
    id VARCHAR(64) PRIMARY KEY, reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    revision BIGINT NOT NULL, event_type VARCHAR(64) NOT NULL, actor_id VARCHAR(255) NOT NULL,
    evidence_reference VARCHAR(255), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_admission_event_revision UNIQUE (reservation_id, revision)
);
