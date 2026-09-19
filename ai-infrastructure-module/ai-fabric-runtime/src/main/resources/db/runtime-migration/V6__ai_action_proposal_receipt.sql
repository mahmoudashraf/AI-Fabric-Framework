CREATE TABLE IF NOT EXISTS ai_action_proposal_receipt (
  receipt_id VARCHAR(120) PRIMARY KEY,
  invocation_id VARCHAR(120) NOT NULL,
  specialist_name VARCHAR(120) NOT NULL,
  specialist_version VARCHAR(80) NOT NULL,
  specialist_content_hash VARCHAR(128) NOT NULL,
  effective_profile_hash VARCHAR(128) NOT NULL,
  principal_fingerprint VARCHAR(128) NOT NULL,
  subject_type VARCHAR(80) NOT NULL,
  subject_fingerprint VARCHAR(128) NOT NULL,
  tenant_fingerprint VARCHAR(128) NOT NULL,
  deployment_fingerprint VARCHAR(128) NOT NULL,
  action_name VARCHAR(160) NOT NULL,
  protected_parameters TEXT NOT NULL,
  parameter_hash VARCHAR(128) NOT NULL,
  parameter_schema_hash VARCHAR(128) NOT NULL,
  confirmation_message VARCHAR(1000) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  evidence_hashes TEXT NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  confirmed_at TIMESTAMP NULL,
  execution_started_at TIMESTAMP NULL,
  executed_at TIMESTAMP NULL,
  terminal_at TIMESTAMP NULL,
  protected_outcome TEXT NULL,
  failure_reason VARCHAR(160) NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_action_receipt_status
  ON ai_action_proposal_receipt (status);
CREATE INDEX IF NOT EXISTS idx_ai_action_receipt_expiry
  ON ai_action_proposal_receipt (expires_at);
