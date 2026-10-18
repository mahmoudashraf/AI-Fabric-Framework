CREATE TABLE IF NOT EXISTS ai_conversation_orchestration_state (
  tenant_id VARCHAR(128) NOT NULL,
  deployment_id VARCHAR(128) NOT NULL,
  conversation_id VARCHAR(160) NOT NULL,
  owner_id VARCHAR(256) NOT NULL,
  state_revision BIGINT NOT NULL,
  state_json VARCHAR(262144) NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, deployment_id, conversation_id),
  CONSTRAINT fk_ai_conversation_orchestration_state
    FOREIGN KEY (tenant_id, deployment_id, conversation_id)
    REFERENCES ai_conversation_control (tenant_id, deployment_id, conversation_id)
    ON DELETE CASCADE
);

ALTER TABLE IF EXISTS chat_sessions
  ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
