CREATE TABLE runtime_target_index_retirement (
    operation_id VARCHAR(160) PRIMARY KEY,
    fence_epoch BIGINT NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    topology_fingerprint VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
