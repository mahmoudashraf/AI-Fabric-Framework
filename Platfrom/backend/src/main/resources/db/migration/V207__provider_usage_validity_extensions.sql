-- Allocation-only operating validity. Original financial approval payloads and counters are not modified.
CREATE TABLE provider_usage_validity_reviews (
    extension_id VARCHAR(128) PRIMARY KEY,
    approval_id VARCHAR(96) NOT NULL REFERENCES provider_usage_allocations(approval_id),
    review_fingerprint VARCHAR(64) NOT NULL,
    record_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL
);
CREATE INDEX ix_provider_usage_validity_allocation ON provider_usage_validity_reviews(approval_id);

CREATE TABLE provider_usage_validity_events (
    extension_id VARCHAR(128) NOT NULL REFERENCES provider_usage_validity_reviews(extension_id),
    event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('INTENT','UNKNOWN','ACK')),
    record_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    PRIMARY KEY (extension_id,event_type)
);

CREATE TRIGGER provider_usage_validity_review_immutable BEFORE UPDATE OR DELETE ON provider_usage_validity_reviews
    FOR EACH ROW EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_validity_review_no_truncate BEFORE TRUNCATE ON provider_usage_validity_reviews
    FOR EACH STATEMENT EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_validity_event_immutable BEFORE UPDATE OR DELETE ON provider_usage_validity_events
    FOR EACH ROW EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_validity_event_no_truncate BEFORE TRUNCATE ON provider_usage_validity_events
    FOR EACH STATEMENT EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
