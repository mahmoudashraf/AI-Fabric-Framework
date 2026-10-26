CREATE TABLE desk_operator (
  operator_id VARCHAR(160) PRIMARY KEY,
  issuer VARCHAR(512) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  email VARCHAR(320),
  display_name VARCHAR(160) NOT NULL,
  registered BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  UNIQUE (issuer, subject)
);
CREATE TABLE desk_workspace (
  customer_id VARCHAR(160) PRIMARY KEY,
  name VARCHAR(240) NOT NULL
);
CREATE TABLE desk_owner (
  customer_id VARCHAR(160) NOT NULL REFERENCES desk_workspace(customer_id),
  operator_id VARCHAR(160) NOT NULL REFERENCES desk_operator(operator_id),
  PRIMARY KEY (customer_id, operator_id)
);
CREATE TABLE desk_binding (
  binding_id VARCHAR(160) PRIMARY KEY,
  customer_id VARCHAR(160) NOT NULL REFERENCES desk_workspace(customer_id),
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  target_profile_id VARCHAR(160) NOT NULL,
  deployment_name VARCHAR(240) NOT NULL,
  environment VARCHAR(64) NOT NULL,
  callback_url VARCHAR(2048) NOT NULL,
  provider_key_hash VARCHAR(64) NOT NULL UNIQUE,
  protected_provider_key TEXT NOT NULL,
  protected_signing_secret TEXT NOT NULL,
  review_bridge_url VARCHAR(2048),
  protected_review_bridge_key TEXT,
  enabled BOOLEAN NOT NULL,
  UNIQUE(customer_id, deployment_id, target_profile_id)
);
CREATE TABLE desk_assignment (
  binding_id VARCHAR(160) NOT NULL REFERENCES desk_binding(binding_id),
  operator_id VARCHAR(160) NOT NULL REFERENCES desk_operator(operator_id),
  role VARCHAR(24) NOT NULL CHECK(role = 'SUPPORT' OR role = 'REVIEWER'),
  PRIMARY KEY(binding_id, operator_id, role)
);
CREATE TABLE desk_audit (
  audit_id VARCHAR(160) PRIMARY KEY,
  binding_id VARCHAR(160),
  customer_id VARCHAR(160),
  actor_id VARCHAR(160) NOT NULL,
  operation VARCHAR(64) NOT NULL,
  target_id VARCHAR(256),
  created_at TIMESTAMP NOT NULL
);
CREATE TABLE desk_oauth_client (
  registration_id VARCHAR(100) NOT NULL,
  principal_name VARCHAR(256) NOT NULL,
  protected_tokens TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY(registration_id,principal_name)
);
CREATE TABLE inbox_conversation (
  binding_id VARCHAR(160) NOT NULL REFERENCES desk_binding(binding_id),
  handover_id VARCHAR(160) NOT NULL,
  external_conversation_ref VARCHAR(400) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_ref VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  expected_control_version BIGINT NOT NULL,
  claimed_by VARCHAR(160),
  claimed_display_name VARCHAR(160),
  protected_intake TEXT,
  unread_count INTEGER NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  closed_at TIMESTAMP,
  version BIGINT NOT NULL,
  PRIMARY KEY(binding_id, handover_id),
  UNIQUE(binding_id, external_conversation_ref)
);
CREATE INDEX idx_desk_queue ON inbox_conversation(binding_id,status,updated_at);
CREATE TABLE inbox_message (
  binding_id VARCHAR(160) NOT NULL,
  handover_id VARCHAR(160) NOT NULL,
  message_id VARCHAR(180) NOT NULL,
  source_message_id VARCHAR(180) NOT NULL,
  participant_role VARCHAR(24) NOT NULL,
  actor_ref VARCHAR(256) NOT NULL,
  safe_actor_display_name VARCHAR(160),
  content TEXT NOT NULL,
  attachment_refs_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY(binding_id,handover_id,message_id),
  UNIQUE(binding_id,handover_id,source_message_id),
  FOREIGN KEY(binding_id,handover_id) REFERENCES inbox_conversation(binding_id,handover_id)
);
CREATE TABLE inbox_dispatch_receipt (
  binding_id VARCHAR(160) NOT NULL REFERENCES desk_binding(binding_id),
  dispatch_id VARCHAR(180) NOT NULL,
  payload_sha256 VARCHAR(64) NOT NULL,
  response_json TEXT NOT NULL,
  received_at TIMESTAMP NOT NULL,
  PRIMARY KEY(binding_id, dispatch_id)
);
CREATE TABLE inbox_callback_outbox (
  binding_id VARCHAR(160) NOT NULL,
  callback_id VARCHAR(180) NOT NULL,
  handover_id VARCHAR(160) NOT NULL,
  callback_sequence BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  protected_payload TEXT NOT NULL,
  status VARCHAR(24) NOT NULL,
  attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMP NOT NULL,
  lease_owner VARCHAR(160),
  lease_until TIMESTAMP,
  failure_code VARCHAR(100),
  created_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY(binding_id,callback_id),
  UNIQUE(binding_id,handover_id,callback_sequence),
  FOREIGN KEY(binding_id,handover_id) REFERENCES inbox_conversation(binding_id,handover_id)
);
CREATE INDEX idx_desk_callback_fifo ON inbox_callback_outbox(binding_id,handover_id,callback_sequence,status);
CREATE INDEX idx_desk_callback_poll ON inbox_callback_outbox(binding_id,status,next_attempt_at,lease_until);
CREATE TABLE inbox_operator_operation (
  binding_id VARCHAR(160) NOT NULL REFERENCES desk_binding(binding_id),
  operation_id VARCHAR(180) NOT NULL,
  operator_id VARCHAR(160) NOT NULL,
  operation_type VARCHAR(40) NOT NULL,
  target_id VARCHAR(180) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY(binding_id,operation_id)
);
