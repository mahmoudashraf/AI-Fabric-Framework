CREATE TABLE IF NOT EXISTS loomai_smart_brain_operation (
  operation_id VARCHAR(120) PRIMARY KEY,
  tenant_id VARCHAR(200) NOT NULL,
  deployment_id VARCHAR(200) NOT NULL,
  trigger_code VARCHAR(80) NOT NULL,
  cloud_event_id VARCHAR(200) NOT NULL,
  cloud_event_type VARCHAR(200) NOT NULL,
  cloud_event_source VARCHAR(500) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  protected_request TEXT NOT NULL,
  invocation_id VARCHAR(120) NULL,
  status VARCHAR(40) NOT NULL,
  protected_result TEXT NULL,
  failure_code VARCHAR(100) NULL,
  failure_message VARCHAR(500) NULL,
  accepted_runtime_url VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  expires_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL,
  CONSTRAINT uq_smart_brain_idempotency
    UNIQUE (tenant_id, deployment_id, trigger_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_smart_brain_owner_status
  ON loomai_smart_brain_operation (tenant_id, deployment_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_smart_brain_processing
  ON loomai_smart_brain_operation (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_smart_brain_expiry
  ON loomai_smart_brain_operation (expires_at);
