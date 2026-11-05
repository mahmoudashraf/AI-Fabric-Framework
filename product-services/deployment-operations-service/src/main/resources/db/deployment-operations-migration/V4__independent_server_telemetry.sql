CREATE TABLE operations_telemetry_lock (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);
INSERT INTO operations_telemetry_lock (id) VALUES (1);

CREATE TABLE operations_host_samples (
    server_id VARCHAR(128) NOT NULL,
    sample_minute BIGINT NOT NULL,
    boot_id VARCHAR(36) NOT NULL,
    observed_key VARCHAR(40) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    PRIMARY KEY (server_id, sample_minute),
    UNIQUE (server_id, boot_id, observed_key)
);
CREATE INDEX operations_host_samples_time ON operations_host_samples (server_id, observed_at);
CREATE INDEX operations_host_samples_retention ON operations_host_samples (observed_at);

CREATE TABLE operations_telemetry_latest (
    server_id VARCHAR(128) PRIMARY KEY,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL
);

CREATE TABLE operations_telemetry_boots (
    server_id VARCHAR(128) NOT NULL,
    boot_id VARCHAR(36) NOT NULL,
    first_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (server_id, boot_id)
);
CREATE INDEX operations_telemetry_boot_retention ON operations_telemetry_boots (last_observed_at);

CREATE TABLE operations_telemetry_aggregates (
    server_id VARCHAR(128) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    stats_json TEXT NOT NULL,
    PRIMARY KEY (server_id, resource_id, bucket_start)
);
CREATE INDEX operations_telemetry_aggregate_retention ON operations_telemetry_aggregates (bucket_start);
CREATE INDEX operations_telemetry_aggregate_bucket ON operations_telemetry_aggregates (server_id, bucket_start);
