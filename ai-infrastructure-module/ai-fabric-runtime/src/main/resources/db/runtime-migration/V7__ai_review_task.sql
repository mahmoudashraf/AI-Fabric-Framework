CREATE TABLE IF NOT EXISTS ai_review_task (
  task_id VARCHAR(120) PRIMARY KEY,
  policy_name VARCHAR(120) NOT NULL,
  policy_version VARCHAR(120) NOT NULL,
  policy_content_hash VARCHAR(64) NOT NULL,
  review_type VARCHAR(40) NOT NULL,
  source_type VARCHAR(40) NOT NULL,
  source_fingerprint VARCHAR(64) NOT NULL,
  initiator_fingerprint VARCHAR(64) NOT NULL,
  subject_fingerprint VARCHAR(64) NOT NULL,
  tenant_fingerprint VARCHAR(64) NOT NULL,
  deployment_fingerprint VARCHAR(64) NOT NULL,
  idempotency_fingerprint VARCHAR(64) NOT NULL UNIQUE,
  request_fingerprint VARCHAR(64) NOT NULL,
  protected_source TEXT NOT NULL,
  protected_presentation TEXT NOT NULL,
  allowed_decisions TEXT NOT NULL,
  status VARCHAR(40) NOT NULL,
  decision_type VARCHAR(40) NULL,
  decision_fingerprint VARCHAR(64) NULL,
  reviewer_fingerprint VARCHAR(64) NULL,
  protected_decision TEXT NULL,
  protected_result TEXT NULL,
  failure_reason VARCHAR(160) NULL,
  successor_task_id VARCHAR(120) NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  terminal_at TIMESTAMP NULL,
  lease_owner VARCHAR(160) NULL,
  lease_until TIMESTAMP NULL,
  attempt_count INTEGER NOT NULL,
  version BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_review_inbox
  ON ai_review_task (tenant_fingerprint, status, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_review_recovery
  ON ai_review_task (status, lease_until, updated_at);
CREATE INDEX IF NOT EXISTS idx_ai_review_expiry
  ON ai_review_task (status, expires_at);
