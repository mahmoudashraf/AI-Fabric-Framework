CREATE TABLE IF NOT EXISTS ai_conversation_control (
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  control_state VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  control_epoch BIGINT NOT NULL,
  active_handover_id VARCHAR(160) NULL,
  channel_binding_id VARCHAR(160) NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, deployment_id, conversation_id)
);

CREATE TABLE IF NOT EXISTS ai_conversation_control_command (
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  command_type VARCHAR(40) NOT NULL,
  command_fingerprint VARCHAR(64) NOT NULL,
  outcome VARCHAR(40) NOT NULL,
  result_state VARCHAR(32) NOT NULL,
  result_version BIGINT NOT NULL,
  result_epoch BIGINT NOT NULL,
  result_handover_id VARCHAR(160) NULL,
  result_channel_binding_id VARCHAR(160) NULL,
  code VARCHAR(100) NULL,
  message VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, deployment_id, conversation_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ai_conversation_message (
  message_id VARCHAR(160) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  message_sequence BIGINT NOT NULL,
  participant_role VARCHAR(24) NOT NULL,
  actor_ref VARCHAR(256) NOT NULL,
  content VARCHAR(20000) NOT NULL,
  client_message_id VARCHAR(200) NULL,
  metadata_json VARCHAR(16000) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (message_id),
  UNIQUE (tenant_id, deployment_id, conversation_id, message_sequence),
  UNIQUE (tenant_id, deployment_id, conversation_id, owner_id, client_message_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_message_scope
  ON ai_conversation_message (tenant_id, deployment_id, conversation_id, message_sequence);

CREATE TABLE IF NOT EXISTS ai_conversation_ai_interaction (
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  user_message_id VARCHAR(160) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  response_http_status INTEGER NULL,
  protected_response TEXT NULL,
  failure_code VARCHAR(100) NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (
    tenant_id,
    deployment_id,
    conversation_id,
    owner_id,
    idempotency_key
  ),
  CONSTRAINT fk_ai_conversation_interaction_message
    FOREIGN KEY (user_message_id) REFERENCES ai_conversation_message(message_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_interaction_status
  ON ai_conversation_ai_interaction (status, updated_at);

CREATE TABLE IF NOT EXISTS ai_conversation_handover (
  handover_id VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  channel_binding_id VARCHAR(160) NOT NULL,
  request_idempotency_key VARCHAR(200) NOT NULL,
  reason_code VARCHAR(100) NULL,
  status VARCHAR(40) NOT NULL,
  external_conversation_ref VARCHAR(240) NULL,
  external_actor_ref VARCHAR(256) NULL,
  safe_actor_display_name VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL,
  activated_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL,
  UNIQUE (tenant_id, deployment_id, conversation_id, request_idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_handover_scope
  ON ai_conversation_handover (tenant_id, deployment_id, conversation_id, status);

CREATE TABLE IF NOT EXISTS ai_conversation_handover_operation (
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  operation_type VARCHAR(40) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  handover_id VARCHAR(160) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (
    tenant_id,
    deployment_id,
    conversation_id,
    owner_id,
    operation_type,
    idempotency_key
  ),
  CONSTRAINT fk_ai_conversation_handover_operation
    FOREIGN KEY (handover_id) REFERENCES ai_conversation_handover(handover_id)
);

CREATE TABLE IF NOT EXISTS ai_conversation_handover_intake (
  handover_id VARCHAR(160) NOT NULL,
  field_key VARCHAR(80) NOT NULL,
  protected_value TEXT NOT NULL,
  purpose VARCHAR(240) NOT NULL,
  consented BOOLEAN NOT NULL,
  verified BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  PRIMARY KEY (handover_id, field_key),
  CONSTRAINT fk_ai_conversation_handover_intake
    FOREIGN KEY (handover_id) REFERENCES ai_conversation_handover(handover_id)
);

CREATE TABLE IF NOT EXISTS ai_conversation_handover_dispatch (
  dispatch_id VARCHAR(160) PRIMARY KEY,
  handover_id VARCHAR(160) NOT NULL,
  dispatch_sequence BIGINT NOT NULL,
  dispatch_type VARCHAR(40) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  protected_payload TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMP NOT NULL,
  lease_owner VARCHAR(160) NULL,
  lease_until TIMESTAMP NULL,
  external_conversation_ref VARCHAR(240) NULL,
  provider_message_ref VARCHAR(240) NULL,
  failure_code VARCHAR(100) NULL,
  failure_message VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL,
  CONSTRAINT fk_ai_conversation_handover_dispatch
    FOREIGN KEY (handover_id) REFERENCES ai_conversation_handover(handover_id),
  UNIQUE (handover_id, dispatch_sequence)
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_dispatch_recovery
  ON ai_conversation_handover_dispatch (status, next_attempt_at, lease_until);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_dispatch_handover_order
  ON ai_conversation_handover_dispatch (handover_id, dispatch_sequence);

CREATE TABLE IF NOT EXISTS ai_conversation_event (
  event_id VARCHAR(160) PRIMARY KEY,
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  protected_payload TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_event_scope
  ON ai_conversation_event (tenant_id, deployment_id, conversation_id, created_at, event_id);

CREATE TABLE IF NOT EXISTS ai_conversation_channel_event_receipt (
  event_id VARCHAR(160) PRIMARY KEY,
  channel_binding_id VARCHAR(160) NOT NULL,
  handover_id VARCHAR(160) NOT NULL,
  payload_fingerprint VARCHAR(64) NOT NULL,
  outcome VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_channel_receipt_handover
  ON ai_conversation_channel_event_receipt (handover_id, created_at);

CREATE TABLE IF NOT EXISTS ai_conversation_channel_callback_nonce (
  nonce VARCHAR(160) PRIMARY KEY,
  event_id VARCHAR(160) NOT NULL,
  channel_binding_id VARCHAR(160) NOT NULL,
  handover_id VARCHAR(160) NOT NULL,
  payload_fingerprint VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_channel_nonce_created
  ON ai_conversation_channel_callback_nonce (created_at);
