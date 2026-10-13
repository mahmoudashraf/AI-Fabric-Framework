CREATE TABLE IF NOT EXISTS inbox_conversation (
  handover_id VARCHAR(160) PRIMARY KEY,
  binding_id VARCHAR(160) NOT NULL,
  external_conversation_ref VARCHAR(240) NOT NULL UNIQUE,
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_ref VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  expected_control_version BIGINT NOT NULL,
  claimed_by VARCHAR(160) NULL,
  claimed_display_name VARCHAR(160) NULL,
  protected_intake TEXT NULL,
  unread_count INTEGER NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  closed_at TIMESTAMP NULL,
  version BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inbox_conversation_queue
  ON inbox_conversation (binding_id, status, updated_at);

CREATE TABLE IF NOT EXISTS inbox_message (
  message_id VARCHAR(180) PRIMARY KEY,
  handover_id VARCHAR(160) NOT NULL,
  participant_role VARCHAR(24) NOT NULL,
  actor_ref VARCHAR(256) NOT NULL,
  safe_actor_display_name VARCHAR(160) NULL,
  content TEXT NOT NULL,
  attachment_refs_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_inbox_message_conversation
    FOREIGN KEY (handover_id) REFERENCES inbox_conversation(handover_id)
);

CREATE INDEX IF NOT EXISTS idx_inbox_message_order
  ON inbox_message (handover_id, created_at, message_id);

CREATE TABLE IF NOT EXISTS inbox_dispatch_receipt (
  dispatch_id VARCHAR(180) PRIMARY KEY,
  payload_sha256 VARCHAR(64) NOT NULL,
  response_json TEXT NOT NULL,
  received_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS inbox_callback_outbox (
  callback_id VARCHAR(180) PRIMARY KEY,
  handover_id VARCHAR(160) NOT NULL,
  callback_sequence BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  protected_payload TEXT NOT NULL,
  status VARCHAR(24) NOT NULL,
  attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMP NOT NULL,
  lease_owner VARCHAR(160) NULL,
  lease_until TIMESTAMP NULL,
  failure_code VARCHAR(100) NULL,
  created_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_inbox_callback_conversation
    FOREIGN KEY (handover_id) REFERENCES inbox_conversation(handover_id),
  UNIQUE (handover_id, callback_sequence)
);

CREATE INDEX IF NOT EXISTS idx_inbox_callback_recovery
  ON inbox_callback_outbox (status, next_attempt_at, lease_until);
