CREATE TABLE dealership_vehicle (
    id VARCHAR(80) PRIMARY KEY,
    stock_id VARCHAR(80) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    make_name VARCHAR(80) NOT NULL,
    model_name VARCHAR(80) NOT NULL,
    derivative VARCHAR(160) NOT NULL,
    registration_year INTEGER NOT NULL,
    price_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    mileage INTEGER NOT NULL,
    fuel_type VARCHAR(40) NOT NULL,
    transmission VARCHAR(40) NOT NULL,
    body_type VARCHAR(40) NOT NULL,
    exterior_colour VARCHAR(60) NOT NULL,
    doors INTEGER NOT NULL,
    seats INTEGER NOT NULL,
    electric_range_miles INTEGER,
    location_name VARCHAR(120) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL,
    summary TEXT NOT NULL,
    features_json TEXT NOT NULL,
    image_path VARCHAR(240) NOT NULL,
    source_label VARCHAR(120) NOT NULL,
    source_updated_at TIMESTAMP NOT NULL,
    source_version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_dealership_vehicle_filters
    ON dealership_vehicle (lifecycle_state, make_name, fuel_type, body_type, price_minor);

CREATE TABLE dealership_sync_run (
    id VARCHAR(80) PRIMARY KEY,
    request_id VARCHAR(180) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    total_count INTEGER NOT NULL,
    succeeded_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    provider_request_id VARCHAR(180),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE dealership_sync_item (
    id VARCHAR(80) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    vehicle_id VARCHAR(80) NOT NULL,
    indexing_work_id VARCHAR(100),
    status VARCHAR(40) NOT NULL,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sync_item_run FOREIGN KEY (run_id) REFERENCES dealership_sync_run(id),
    CONSTRAINT fk_sync_item_vehicle FOREIGN KEY (vehicle_id) REFERENCES dealership_vehicle(id)
);

CREATE INDEX idx_dealership_sync_item_work ON dealership_sync_item (indexing_work_id, status);

CREATE TABLE dealership_lead_request (
    id VARCHAR(80) PRIMARY KEY,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    action_type VARCHAR(50) NOT NULL,
    vehicle_id VARCHAR(80) NOT NULL,
    encrypted_contact TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    consent_recorded BOOLEAN NOT NULL,
    source_session_id VARCHAR(160),
    receipt_code VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_lead_vehicle FOREIGN KEY (vehicle_id) REFERENCES dealership_vehicle(id)
);

CREATE INDEX idx_dealership_lead_status_created ON dealership_lead_request (status, created_at);
