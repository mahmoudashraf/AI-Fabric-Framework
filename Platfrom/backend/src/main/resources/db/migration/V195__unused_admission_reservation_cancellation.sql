-- A cancellation records unused authority, not resource reclamation or provider credit refund.
CREATE TABLE deployment_admission_unused_cancellations (
    reservation_id VARCHAR(64) NOT NULL REFERENCES deployment_capacity_reservations(id),
    generation VARCHAR(64) NOT NULL,
    id VARCHAR(64) NOT NULL UNIQUE,
    operation_key VARCHAR(128) NOT NULL,
    command_fingerprint VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (reservation_id, generation)
);
