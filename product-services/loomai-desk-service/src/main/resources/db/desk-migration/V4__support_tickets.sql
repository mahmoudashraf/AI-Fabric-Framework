CREATE TABLE desk_support_space (
  space_id VARCHAR(160) PRIMARY KEY,
  binding_id VARCHAR(160) NOT NULL UNIQUE REFERENCES desk_binding(binding_id),
  name VARCHAR(240) NOT NULL,
  enabled BOOLEAN NOT NULL,
  human_enabled BOOLEAN NOT NULL,
  provider_id VARCHAR(100) NOT NULL,
  provider_binding_id VARCHAR(160) NOT NULL,
  runtime_base_url VARCHAR(2048) NOT NULL,
  runtime_trusted_key_header VARCHAR(100) NOT NULL,
  protected_identity TEXT NOT NULL,
  protected_connector_key TEXT NOT NULL,
    protected_server_connector TEXT,
  origins_json TEXT NOT NULL,
  topics_json TEXT NOT NULL,
  retention_days INTEGER NOT NULL CHECK(retention_days BETWEEN 1 AND 3650),
  version BIGINT NOT NULL
);
CREATE TABLE desk_support_requester (
  space_id VARCHAR(160) NOT NULL REFERENCES desk_support_space(space_id),
  requester_id VARCHAR(160) NOT NULL,
  PRIMARY KEY(space_id,requester_id)
);
CREATE TABLE desk_support_request (
  space_id VARCHAR(160) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  requester_id VARCHAR(160) NOT NULL,
  provider_id VARCHAR(100) NOT NULL,
  provider_binding_id VARCHAR(160) NOT NULL,
  runtime_principal VARCHAR(256) NOT NULL,
  private_runtime_principal VARCHAR(256),
  PRIMARY KEY(space_id,request_id),
  UNIQUE(space_id,private_runtime_principal),
  FOREIGN KEY(space_id,requester_id) REFERENCES desk_support_requester(space_id,requester_id)
);
CREATE TABLE desk_support_ticket (
  space_id VARCHAR(160) NOT NULL REFERENCES desk_support_space(space_id),
  request_id VARCHAR(160) NOT NULL,
  requester_id VARCHAR(160) NOT NULL,
  provider_binding_id VARCHAR(160) NOT NULL,
  reference VARCHAR(160) NOT NULL,
  protected_intake TEXT NOT NULL,
  status VARCHAR(24) NOT NULL CHECK(status IN ('OPEN','RESOLVED','CANCELLED')),
  version BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  closed_at TIMESTAMP,
  seen_at TIMESTAMP,
  PRIMARY KEY(space_id,request_id),
  FOREIGN KEY(space_id,requester_id) REFERENCES desk_support_requester(space_id,requester_id)
);
CREATE INDEX idx_support_own ON desk_support_ticket(space_id,requester_id,request_id);
CREATE TABLE desk_support_ticket_episode (
  space_id VARCHAR(160) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  episode_id VARCHAR(160) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  episode_number INTEGER NOT NULL,
  runtime_base_url VARCHAR(2048) NOT NULL,
  opening_required BOOLEAN NOT NULL,
  message_key VARCHAR(180) NOT NULL,
  delivery VARCHAR(24) NOT NULL CHECK(delivery IN ('PENDING','IN_FLIGHT','DELIVERED','UNKNOWN','NOT_REQUIRED')),
  attempt_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY(space_id,episode_id),
  UNIQUE(space_id,conversation_id),
  UNIQUE(space_id,request_id,episode_number),
  FOREIGN KEY(space_id,request_id) REFERENCES desk_support_request(space_id,request_id)
);
CREATE TABLE desk_support_ticket_event (
  space_id VARCHAR(160) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  event_id VARCHAR(160) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_id VARCHAR(160) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY(space_id,event_id),
  FOREIGN KEY(space_id,request_id) REFERENCES desk_support_request(space_id,request_id)
);
CREATE TABLE desk_support_ticket_operation (
  space_id VARCHAR(160) NOT NULL REFERENCES desk_support_space(space_id),
  actor_id VARCHAR(160) NOT NULL,
  command_key VARCHAR(180) NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  protected_result TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY(space_id,actor_id,command_key)
);
CREATE TABLE desk_support_ticket_tombstone (
  space_id VARCHAR(160) NOT NULL REFERENCES desk_support_space(space_id),
  request_id VARCHAR(160) NOT NULL,
  purged_at TIMESTAMP NOT NULL,
  PRIMARY KEY(space_id,request_id)
);
