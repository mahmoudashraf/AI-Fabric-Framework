-- Console-specific resource consent only. Tickets and lifecycle belong to TicketProvider.
CREATE TABLE platform_support_integration_context (
    support_space_id VARCHAR(128) NOT NULL,
    ticket_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(64) NOT NULL REFERENCES platform_customers(id),
    requester_user_id VARCHAR(64) NOT NULL REFERENCES platform_users(id),
    requester_issuer VARCHAR(512),
    requester_subject VARCHAR(255),
    runtime_principal VARCHAR(160) NOT NULL,
    resources_json TEXT NOT NULL,
    consent_granted BOOLEAN NOT NULL DEFAULT FALSE,
    consent_version BIGINT NOT NULL DEFAULT 0 CHECK (consent_version >= 0),
    consent_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consent_revoked_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (support_space_id, ticket_id),
    UNIQUE (support_space_id, runtime_principal),
    CHECK ((requester_issuer IS NULL AND requester_subject IS NULL)
        OR (requester_issuer IS NOT NULL AND requester_subject IS NOT NULL))
);
CREATE INDEX support_integration_requester ON platform_support_integration_context
    (support_space_id, customer_id, requester_user_id);

-- Private-host conversation authority before a requester chooses to create a ticket.
-- This is not ticket storage and contains no message or support lifecycle state.
CREATE TABLE platform_support_conversation_scope (
    support_space_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    runtime_principal VARCHAR(160) NOT NULL,
    customer_id VARCHAR(64) NOT NULL REFERENCES platform_customers(id),
    requester_user_id VARCHAR(64) NOT NULL REFERENCES platform_users(id),
    requester_issuer VARCHAR(512),
    requester_subject VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (support_space_id, conversation_id),
    UNIQUE (support_space_id, runtime_principal),
    CHECK ((requester_issuer IS NULL AND requester_subject IS NULL)
        OR (requester_issuer IS NOT NULL AND requester_subject IS NOT NULL))
);
