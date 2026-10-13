CREATE INDEX IF NOT EXISTS idx_inbox_callback_handover_order
  ON inbox_callback_outbox (handover_id, callback_sequence);

CREATE TABLE IF NOT EXISTS inbox_operator_operation (
  operation_id VARCHAR(180) PRIMARY KEY,
  binding_id VARCHAR(160) NOT NULL,
  operator_id VARCHAR(160) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(180) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inbox_operator_operation_binding
  ON inbox_operator_operation (binding_id, created_at);
