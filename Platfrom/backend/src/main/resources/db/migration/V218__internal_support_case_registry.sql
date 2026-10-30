-- Intake and correlation only; canonical conversation/history remains in runtime AI state.
CREATE TABLE platform_support_cases (
    id VARCHAR(64) PRIMARY KEY,
    reference VARCHAR(64) NOT NULL UNIQUE,
    customer_id VARCHAR(64) NOT NULL REFERENCES platform_customers(id),
    requester_user_id VARCHAR(64) NOT NULL REFERENCES platform_users(id),
    requester_issuer VARCHAR(512),
    requester_subject VARCHAR(255),
    runtime_principal VARCHAR(96) NOT NULL UNIQUE,
    submission_key VARCHAR(128) NOT NULL,
    submission_hash VARCHAR(64) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    details TEXT,
    topic VARCHAR(80),
    resources_json TEXT NOT NULL,
    initial_message TEXT NOT NULL,
    initial_message_key VARCHAR(96) NOT NULL UNIQUE,
    delivery_status VARCHAR(32) NOT NULL CHECK (delivery_status IN
        ('PENDING', 'UNKNOWN', 'USER_MESSAGE_PERSISTED', 'COMPLETED', 'FAILED')),
    delivery_observed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED')),
    consent_granted BOOLEAN NOT NULL,
    consent_version BIGINT NOT NULL CHECK (consent_version >= 0),
    consent_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consent_revoked_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT support_requester_identity_pair CHECK
        ((requester_issuer IS NULL AND requester_subject IS NULL)
            OR (requester_issuer IS NOT NULL AND requester_subject IS NOT NULL)),
    CONSTRAINT support_closed_consent CHECK (status = 'OPEN' OR NOT consent_granted),
    CONSTRAINT support_intake_operation UNIQUE (customer_id, requester_user_id, submission_key)
);

CREATE INDEX support_cases_requester ON platform_support_cases(customer_id, requester_user_id, created_at DESC);

CREATE TABLE platform_support_case_episodes (
    id VARCHAR(64) PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL REFERENCES platform_support_cases(id),
    episode_number INTEGER NOT NULL CHECK (episode_number > 0),
    binding_json TEXT NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(96) NOT NULL,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (case_id, episode_number),
    UNIQUE (deployment_id, target_profile_id, conversation_id)
);

CREATE TABLE platform_support_case_events (
    id VARCHAR(64) PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL REFERENCES platform_support_cases(id),
    event_type VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64),
    consent_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX support_case_events_case ON platform_support_case_events(case_id, created_at);
