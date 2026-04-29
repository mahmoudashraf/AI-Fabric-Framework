CREATE TABLE thinker_deployment_controls (
    deployment_id VARCHAR(255) PRIMARY KEY,
    thinker_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    resolver_preview_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    governed_execution_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    disabled_action_families_json TEXT NOT NULL DEFAULT '[]',
    updated_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE thinker_issue_sessions (
    id VARCHAR(255) PRIMARY KEY,
    deployment_id VARCHAR(255),
    tenant_id VARCHAR(255),
    customer_id VARCHAR(255),
    store_id VARCHAR(255),
    shop_domain VARCHAR(255),
    consumer_id VARCHAR(255),
    user_subject VARCHAR(255),
    shopper_session_id VARCHAR(255),
    locale VARCHAR(64),
    channel VARCHAR(128) NOT NULL,
    mode VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    issue_category VARCHAR(128) NOT NULL,
    risk_class VARCHAR(64) NOT NULL,
    expected_evidence_json TEXT NOT NULL DEFAULT '[]',
    suggested_action_allowlist_json TEXT NOT NULL DEFAULT '[]',
    user_question TEXT NOT NULL,
    user_safe_answer TEXT,
    source_citations_json TEXT NOT NULL DEFAULT '[]',
    planner_trace_json TEXT NOT NULL DEFAULT '{}',
    runtime_conversation_id VARCHAR(255),
    runtime_session_id VARCHAR(255),
    terminal_reason TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_thinker_issue_sessions_started_at ON thinker_issue_sessions(started_at DESC);
CREATE INDEX idx_thinker_issue_sessions_deployment ON thinker_issue_sessions(deployment_id);
CREATE INDEX idx_thinker_issue_sessions_shop_domain ON thinker_issue_sessions(shop_domain);
CREATE INDEX idx_thinker_issue_sessions_status ON thinker_issue_sessions(status);

CREATE TABLE thinker_evidence_items (
    id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    kind VARCHAR(64) NOT NULL,
    source_kind VARCHAR(128) NOT NULL,
    source_identifier VARCHAR(512) NOT NULL,
    freshness VARCHAR(64) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL DEFAULT 0,
    redaction_state VARCHAR(64) NOT NULL,
    operator_raw_reference TEXT,
    summary TEXT NOT NULL,
    safe_summary TEXT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_thinker_evidence_session FOREIGN KEY (session_id) REFERENCES thinker_issue_sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_thinker_evidence_session ON thinker_evidence_items(session_id);

CREATE TABLE thinker_resolution_plans (
    id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    issue_summary TEXT NOT NULL,
    diagnosis TEXT NOT NULL,
    options_considered_json TEXT NOT NULL DEFAULT '[]',
    recommended_next_step TEXT NOT NULL,
    recommendation VARCHAR(64) NOT NULL,
    user_explanation TEXT NOT NULL,
    escalation_target VARCHAR(255),
    evidence_item_ids_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_thinker_plan_session FOREIGN KEY (session_id) REFERENCES thinker_issue_sessions(id) ON DELETE CASCADE
);

CREATE TABLE thinker_session_audit_events (
    id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    actor_role VARCHAR(128) NOT NULL,
    details_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_thinker_audit_session FOREIGN KEY (session_id) REFERENCES thinker_issue_sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_thinker_session_audit_session ON thinker_session_audit_events(session_id, created_at DESC);

CREATE TABLE resolver_intent_proposals (
    id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    action_id VARCHAR(255) NOT NULL,
    action_family VARCHAR(128) NOT NULL,
    target_domain VARCHAR(128) NOT NULL,
    product_boundary VARCHAR(128) NOT NULL,
    actor_context VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255),
    store_id VARCHAR(255),
    deployment_id VARCHAR(255),
    redacted_parameters_json TEXT NOT NULL DEFAULT '{}',
    operator_parameters_json TEXT NOT NULL DEFAULT '{}',
    source_evidence_item_ids_json TEXT NOT NULL DEFAULT '[]',
    proposal_text TEXT NOT NULL,
    normalized_intent_json TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_resolver_proposal_session FOREIGN KEY (session_id) REFERENCES thinker_issue_sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_resolver_proposal_session ON resolver_intent_proposals(session_id);
CREATE INDEX idx_resolver_proposal_status ON resolver_intent_proposals(status);

CREATE TABLE resolver_policy_decisions (
    id VARCHAR(255) PRIMARY KEY,
    proposal_id VARCHAR(255) NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    risk_level VARCHAR(64) NOT NULL,
    required_scopes_json TEXT NOT NULL DEFAULT '[]',
    missing_scopes_json TEXT NOT NULL DEFAULT '[]',
    tier_requirement VARCHAR(64) NOT NULL,
    actor_requirement VARCHAR(128) NOT NULL,
    dry_run_required BOOLEAN NOT NULL,
    confirmation_required BOOLEAN NOT NULL,
    approval_required BOOLEAN NOT NULL,
    confirmation_preview TEXT,
    operator_reason TEXT NOT NULL,
    user_reason TEXT NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_resolver_policy_proposal FOREIGN KEY (proposal_id) REFERENCES resolver_intent_proposals(id) ON DELETE CASCADE
);

CREATE INDEX idx_resolver_policy_proposal ON resolver_policy_decisions(proposal_id);

CREATE TABLE resolver_dry_runs (
    id VARCHAR(255) PRIMARY KEY,
    proposal_id VARCHAR(255) NOT NULL,
    target_action VARCHAR(255) NOT NULL,
    validated_parameters_json TEXT NOT NULL DEFAULT '{}',
    expected_state_transition TEXT NOT NULL,
    expected_side_effects_json TEXT NOT NULL DEFAULT '[]',
    warnings_json TEXT NOT NULL DEFAULT '[]',
    unsupported_fields_json TEXT NOT NULL DEFAULT '[]',
    idempotency_posture TEXT NOT NULL,
    rollback_posture TEXT NOT NULL,
    evidence_freshness VARCHAR(64) NOT NULL,
    product_boundary VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_resolver_dry_run_proposal FOREIGN KEY (proposal_id) REFERENCES resolver_intent_proposals(id) ON DELETE CASCADE
);

CREATE INDEX idx_resolver_dry_run_proposal ON resolver_dry_runs(proposal_id);

CREATE TABLE resolver_executions (
    id VARCHAR(255) PRIMARY KEY,
    proposal_id VARCHAR(255) NOT NULL,
    dry_run_id VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    action_family VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    product_boundary VARCHAR(128) NOT NULL,
    execution_result_json TEXT NOT NULL DEFAULT '{}',
    verification_status VARCHAR(64) NOT NULL,
    recovery_guidance TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_resolver_execution_proposal FOREIGN KEY (proposal_id) REFERENCES resolver_intent_proposals(id) ON DELETE CASCADE
);

CREATE INDEX idx_resolver_execution_proposal ON resolver_executions(proposal_id);
CREATE INDEX idx_resolver_execution_status ON resolver_executions(status);
