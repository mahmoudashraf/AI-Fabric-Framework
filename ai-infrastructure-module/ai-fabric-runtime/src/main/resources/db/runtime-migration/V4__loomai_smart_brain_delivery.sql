CREATE TABLE IF NOT EXISTS loomai_smart_brain_delivery (
  delivery_id VARCHAR(120) PRIMARY KEY,
  operation_id VARCHAR(120) NOT NULL,
  tenant_id VARCHAR(200) NOT NULL,
  deployment_id VARCHAR(200) NOT NULL,
  callback_url VARCHAR(1000) NOT NULL,
  status VARCHAR(40) NOT NULL,
  attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMP NOT NULL,
  last_http_status INTEGER NULL,
  last_error VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  delivered_at TIMESTAMP NULL,
  version BIGINT NOT NULL,
  CONSTRAINT fk_smart_brain_delivery_operation
    FOREIGN KEY (operation_id) REFERENCES loomai_smart_brain_operation(operation_id),
  CONSTRAINT uq_smart_brain_delivery_operation UNIQUE (operation_id)
);

CREATE INDEX IF NOT EXISTS idx_smart_brain_delivery_ready
  ON loomai_smart_brain_delivery (status, next_attempt_at);
