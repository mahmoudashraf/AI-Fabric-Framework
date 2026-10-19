-- No peer, ownership, permission, historical release or provider resource is seeded.
CREATE TABLE deployment_vector_transfer_releases (
    id VARCHAR(64) PRIMARY KEY,
    cluster_id VARCHAR(255) NOT NULL UNIQUE,
    binding_id VARCHAR(64) NOT NULL REFERENCES deployment_provider_resource_handles(id),
    operation_key VARCHAR(128) NOT NULL UNIQUE,
    command_fingerprint VARCHAR(64) NOT NULL,
    source_cp_id VARCHAR(64) NOT NULL,
    destination_cp_id VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE deployment_vector_transfer_release_bindings (
    binding_id VARCHAR(64) PRIMARY KEY REFERENCES deployment_provider_resource_handles(id),
    release_id VARCHAR(64) NOT NULL REFERENCES deployment_vector_transfer_releases(id),
    metadata_fingerprint VARCHAR(64) NOT NULL,
    identity_fingerprint VARCHAR(64) NOT NULL
);
CREATE TABLE deployment_vector_transfer_reviews (
    id VARCHAR(64) PRIMARY KEY,
    operation_key VARCHAR(128) NOT NULL UNIQUE,
    source_cp_id VARCHAR(64) NOT NULL,
    source_receipt_id VARCHAR(64) NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    readback_fingerprint VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (source_cp_id, source_receipt_id)
);
CREATE TABLE deployment_vector_transfer_claims (
    id VARCHAR(64) PRIMARY KEY,
    operation_key VARCHAR(128) NOT NULL UNIQUE,
    review_id VARCHAR(64) NOT NULL UNIQUE REFERENCES deployment_vector_transfer_reviews(id),
    cluster_id VARCHAR(255) NOT NULL,
    binding_id VARCHAR(64) NOT NULL UNIQUE REFERENCES deployment_provider_resource_handles(id),
    command_fingerprint VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE FUNCTION reject_vector_transfer_ledger_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'External vector transfer records are immutable';
END;
$$;
CREATE TRIGGER vector_transfer_release_immutable BEFORE UPDATE OR DELETE ON deployment_vector_transfer_releases
    FOR EACH ROW EXECUTE FUNCTION reject_vector_transfer_ledger_mutation();
CREATE TRIGGER vector_transfer_release_bindings_immutable BEFORE UPDATE OR DELETE ON deployment_vector_transfer_release_bindings
    FOR EACH ROW EXECUTE FUNCTION reject_vector_transfer_ledger_mutation();
CREATE TRIGGER vector_transfer_review_immutable BEFORE UPDATE OR DELETE ON deployment_vector_transfer_reviews
    FOR EACH ROW EXECUTE FUNCTION reject_vector_transfer_ledger_mutation();
CREATE TRIGGER vector_transfer_claim_immutable BEFORE UPDATE OR DELETE ON deployment_vector_transfer_claims
    FOR EACH ROW EXECUTE FUNCTION reject_vector_transfer_ledger_mutation();
CREATE FUNCTION reject_released_vector_binding_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.resource_kind = 'EXTERNAL_VECTOR_STORAGE_BINDING' AND OLD.status = 'RELEASED_UNUSED_TRANSFER' THEN
        RAISE EXCEPTION 'Released external vector binding is permanently fenced';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER released_vector_binding_immutable BEFORE UPDATE OR DELETE ON deployment_provider_resource_handles
    FOR EACH ROW EXECUTE FUNCTION reject_released_vector_binding_mutation();
