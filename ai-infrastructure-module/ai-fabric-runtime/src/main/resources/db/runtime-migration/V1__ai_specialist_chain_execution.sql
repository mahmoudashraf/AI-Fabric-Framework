CREATE TABLE IF NOT EXISTS ai_specialist_chain_execution (
  execution_id VARCHAR(120) PRIMARY KEY,
  chain_name VARCHAR(120) NOT NULL,
  chain_version VARCHAR(80) NOT NULL,
  chain_content_hash VARCHAR(64) NOT NULL,
  manager_name VARCHAR(120) NOT NULL,
  manager_version VARCHAR(80) NOT NULL,
  manager_content_hash VARCHAR(64) NOT NULL,
  access_fingerprint VARCHAR(64) NOT NULL,
  idempotency_fingerprint VARCHAR(64) NOT NULL UNIQUE,
  request_fingerprint VARCHAR(64) NOT NULL,
  protected_request TEXT NOT NULL,
  protected_checkpoint TEXT NOT NULL,
  protected_result TEXT NULL,
  status VARCHAR(40) NOT NULL,
  failure_reason VARCHAR(160) NULL,
  next_decision_index INTEGER NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_ai_chain_recovery
  ON ai_specialist_chain_execution (status, lease_until, updated_at);
CREATE INDEX IF NOT EXISTS idx_ai_chain_expiry
  ON ai_specialist_chain_execution (completed_at);
CREATE INDEX IF NOT EXISTS idx_ai_chain_access
  ON ai_specialist_chain_execution (access_fingerprint);
