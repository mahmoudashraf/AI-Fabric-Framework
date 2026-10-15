CREATE TABLE loomai_managed_dependency_state (
    singleton_id INTEGER PRIMARY KEY,
    lifecycle_state VARCHAR(24) NOT NULL,
    binding_id VARCHAR(160) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE loomai_managed_dependency_operation (
    operation_key VARCHAR(240) PRIMARY KEY,
    request_sha256 VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
