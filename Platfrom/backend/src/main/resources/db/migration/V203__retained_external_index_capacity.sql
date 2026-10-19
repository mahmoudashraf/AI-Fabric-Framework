-- No retention permission, measured byte count, envelope or paid entitlement is seeded.
ALTER TABLE deployment_capacity_reservations DROP CONSTRAINT deployment_capacity_reservations_state_check;
ALTER TABLE deployment_capacity_reservations ADD CONSTRAINT ck_admission_capacity_state
    CHECK (state IN ('RESERVED','PROVISIONING','ACTIVE','OUTCOME_UNKNOWN','CLEANUP_FAILED','RELEASED','RETAINED_EXTERNAL'));
ALTER TABLE deployment_capacity_reservations DROP CONSTRAINT ck_admission_slot;
ALTER TABLE deployment_capacity_reservations ADD CONSTRAINT ck_admission_slot
    CHECK ((state IN ('RELEASED','RETAINED_EXTERNAL') AND current_slot IS NULL)
        OR (state NOT IN ('RELEASED','RETAINED_EXTERNAL') AND current_slot IS NOT NULL));

ALTER TABLE platform_target_retirement_operations DROP CONSTRAINT ck_target_retirement_state;
ALTER TABLE platform_target_retirement_operations ADD CONSTRAINT ck_target_retirement_state
    CHECK (state IN ('PLANNED','QUEUED','RUNNING','WAITING','RETAINED','BLOCKED','RETRYABLE_FAILURE',
        'CANCEL_REQUESTED','CANCELLED','COMPLETE','RETIRED_WITH_RETAINED_DATA'));

CREATE TABLE deployment_external_index_retention_receipts (
    id VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL UNIQUE REFERENCES platform_target_retirement_operations(id),
    request_fingerprint VARCHAR(64) NOT NULL,
    fence_epoch BIGINT NOT NULL CHECK (fence_epoch > 0),
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    binding_id VARCHAR(64) NOT NULL REFERENCES deployment_provider_resource_handles(id),
    generation VARCHAR(64) NOT NULL,
    policy_fingerprint VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_external_retention_generation UNIQUE (reservation_id, generation)
);

CREATE FUNCTION reject_external_retention_receipt_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'External index retention receipts are immutable';
END;
$$;
CREATE TRIGGER external_retention_receipt_append_only
    BEFORE UPDATE OR DELETE ON deployment_external_index_retention_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_external_retention_receipt_mutation();
