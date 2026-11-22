ALTER TABLE platform_support_conversation_scope ADD COLUMN last_selected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE platform_support_conversation_scope ADD COLUMN resources_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE platform_support_conversation_scope ADD COLUMN consent_granted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE platform_support_conversation_scope ADD COLUMN consent_version BIGINT NOT NULL DEFAULT 0 CHECK (consent_version >= 0);
ALTER TABLE platform_support_conversation_scope ADD COLUMN consent_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE platform_support_conversation_scope ADD COLUMN consent_revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE platform_support_conversation_scope ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0);

UPDATE platform_support_conversation_scope
SET last_selected_at = created_at, consent_changed_at = created_at;

CREATE INDEX support_conversation_owner_history ON platform_support_conversation_scope
    (support_space_id, customer_id, requester_user_id, created_at);
