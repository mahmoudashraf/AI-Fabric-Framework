-- Separately funded additive allowances. Original grants, allocation terms and validity remain immutable.
-- Each funding approval evidence reference is consumed by one reviewed command, not another draw on the original grant.
CREATE TABLE provider_usage_top_up_reviews (
    top_up_id VARCHAR(128) PRIMARY KEY,
    approval_id VARCHAR(96) NOT NULL REFERENCES provider_usage_allocations(approval_id),
    approval_evidence_ref VARCHAR(255) NOT NULL UNIQUE,
    review_fingerprint VARCHAR(64) NOT NULL,
    record_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL
);
CREATE INDEX ix_provider_usage_top_up_allocation ON provider_usage_top_up_reviews(approval_id);

CREATE TABLE provider_usage_top_up_events (
    top_up_id VARCHAR(128) NOT NULL REFERENCES provider_usage_top_up_reviews(top_up_id),
    event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('INTENT','UNKNOWN','ACK')),
    record_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    PRIMARY KEY (top_up_id,event_type)
);

CREATE TRIGGER provider_usage_top_up_review_immutable BEFORE UPDATE OR DELETE ON provider_usage_top_up_reviews
    FOR EACH ROW EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_top_up_review_no_truncate BEFORE TRUNCATE ON provider_usage_top_up_reviews
    FOR EACH STATEMENT EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_top_up_event_immutable BEFORE UPDATE OR DELETE ON provider_usage_top_up_events
    FOR EACH ROW EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_top_up_event_no_truncate BEFORE TRUNCATE ON provider_usage_top_up_events
    FOR EACH STATEMENT EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
