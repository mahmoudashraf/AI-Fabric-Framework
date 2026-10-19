-- An unused mistaken approval can be superseded, never refunded or erased.
-- No credits, approvals, replacement grants or deployment changes are seeded here.
ALTER TABLE provider_usage_grants ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CHECK (lifecycle_status IN ('ACTIVE', 'VOID_UNUSED'));

DO $migration$
DECLARE old_scope_constraint TEXT;
BEGIN
    SELECT conname INTO STRICT old_scope_constraint FROM pg_constraint
    WHERE conrelid = 'provider_usage_grants'::regclass AND contype = 'u'
      AND conkey = ARRAY(SELECT attnum FROM pg_attribute
          WHERE attrelid = 'provider_usage_grants'::regclass
            AND attname = ANY(ARRAY['campaign_id','customer_id','target_profile_id','environment_name'])
          ORDER BY attnum);
    EXECUTE format('ALTER TABLE provider_usage_grants DROP CONSTRAINT %I', old_scope_constraint);
END
$migration$;
CREATE UNIQUE INDEX ux_provider_usage_active_grant_scope
    ON provider_usage_grants(campaign_id, customer_id, target_profile_id, environment_name)
    WHERE lifecycle_status = 'ACTIVE';

-- Preserve the source of financial history even if a deployment no longer exists.
ALTER TABLE provider_usage_allowance_events ADD COLUMN grant_id VARCHAR(96) REFERENCES provider_usage_grants(id);
UPDATE provider_usage_allowance_events e SET grant_id = g.id
    FROM provider_usage_grants g WHERE e.event_type = 'GRANT_APPROVED' AND e.subject_id = g.id;
UPDATE provider_usage_allowance_events e SET grant_id = a.grant_id
    FROM provider_usage_allocations a WHERE e.event_type IN ('ALLOWANCE_ALLOCATED','ALLOWANCE_INITIALIZED','ALLOWANCE_ACTIVE')
        AND e.subject_id = a.approval_id;
CREATE INDEX ix_provider_usage_grant_history ON provider_usage_allowance_events(grant_id, created_at);

CREATE TABLE provider_usage_grant_void_receipts (
    grant_id VARCHAR(96) PRIMARY KEY REFERENCES provider_usage_grants(id),
    receipt_id VARCHAR(96) NOT NULL UNIQUE,
    operation_key VARCHAR(128) NOT NULL UNIQUE,
    grant_fingerprint VARCHAR(64) NOT NULL,
    command_fingerprint VARCHAR(64) NOT NULL,
    receipt_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    actor_role VARCHAR(64) NOT NULL CHECK (actor_role = 'PLATFORM_ADMIN'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE FUNCTION provider_usage_reject_ledger_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $function$
BEGIN
    RAISE EXCEPTION 'Provider usage ledger records are append-only' USING ERRCODE = '23514';
END
$function$;
CREATE TRIGGER provider_usage_void_receipt_immutable BEFORE UPDATE OR DELETE ON provider_usage_grant_void_receipts
    FOR EACH ROW EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_allowance_event_immutable BEFORE UPDATE OR DELETE ON provider_usage_allowance_events
    FOR EACH ROW EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_void_receipt_no_truncate BEFORE TRUNCATE ON provider_usage_grant_void_receipts
    FOR EACH STATEMENT EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
CREATE TRIGGER provider_usage_allowance_event_no_truncate BEFORE TRUNCATE ON provider_usage_allowance_events
    FOR EACH STATEMENT EXECUTE FUNCTION provider_usage_reject_ledger_mutation();
