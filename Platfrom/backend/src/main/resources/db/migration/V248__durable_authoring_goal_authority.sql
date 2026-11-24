CREATE TABLE platform_authoring_goals (
    id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL REFERENCES platform_authoring_sessions(id) ON DELETE CASCADE,
    turn_id VARCHAR(255) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL,
    work_id VARCHAR(255),
    reason_code VARCHAR(255),
    request_revision BIGINT NOT NULL,
    cancellation_epoch BIGINT NOT NULL,
    segment INTEGER NOT NULL,
    encrypted_authority TEXT NOT NULL,
    encrypted_projection TEXT NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_poll_at TIMESTAMP WITH TIME ZONE,
    pending_control VARCHAR(255),
    erased_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (session_id, turn_id)
);
CREATE INDEX idx_authoring_goal_poll ON platform_authoring_goals(next_poll_at);
CREATE INDEX idx_authoring_goal_actor ON platform_authoring_goals(actor_id, state);

CREATE TABLE platform_authoring_operations (
    id VARCHAR(255) PRIMARY KEY,
    goal_id VARCHAR(255) NOT NULL REFERENCES platform_authoring_goals(id) ON DELETE CASCADE,
    operation_id VARCHAR(200) NOT NULL,
    parameter_fingerprint VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    encrypted_receipt TEXT NOT NULL,
    undoes VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (goal_id, operation_id)
);
CREATE INDEX idx_authoring_operation_goal ON platform_authoring_operations(goal_id, created_at, id);
CREATE INDEX idx_authoring_operation_segment ON platform_authoring_operations(goal_id, segment);
CREATE INDEX idx_authoring_operation_undo ON platform_authoring_operations(goal_id, undoes);

CREATE TABLE platform_authoring_goal_directives (
    id VARCHAR(255) PRIMARY KEY,
    goal_id VARCHAR(255) NOT NULL REFERENCES platform_authoring_goals(id) ON DELETE CASCADE,
    request_revision BIGINT NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    command_hash VARCHAR(255) NOT NULL,
    encrypted_request TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (goal_id, request_key),
    UNIQUE (goal_id, request_revision)
);
