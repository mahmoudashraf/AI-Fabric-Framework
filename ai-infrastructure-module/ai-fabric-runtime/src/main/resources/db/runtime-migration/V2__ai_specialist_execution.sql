CREATE TABLE IF NOT EXISTS ai_specialist_execution (
  invocation_id VARCHAR(120) PRIMARY KEY,
  specialist_name VARCHAR(120) NOT NULL,
  specialist_version VARCHAR(80) NOT NULL,
  specialist_content_hash VARCHAR(64) NOT NULL,
  access_fingerprint VARCHAR(64) NOT NULL,
  idempotency_fingerprint VARCHAR(64) NULL UNIQUE,
  request_fingerprint VARCHAR(64) NOT NULL,
  protected_request TEXT NOT NULL,
  protected_result TEXT NULL,
  status VARCHAR(40) NOT NULL,
  failure_reason VARCHAR(160) NULL,
  deadline TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  expires_at TIMESTAMP NOT NULL,
  lease_owner VARCHAR(160) NULL,
  lease_until TIMESTAMP NULL,
  attempt_count INTEGER NOT NULL,
  version BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_execution_recovery
  ON ai_specialist_execution (status, lease_until, updated_at);
CREATE INDEX IF NOT EXISTS idx_ai_execution_expiry
  ON ai_specialist_execution (completed_at);
CREATE INDEX IF NOT EXISTS idx_ai_execution_access
  ON ai_specialist_execution (access_fingerprint);
