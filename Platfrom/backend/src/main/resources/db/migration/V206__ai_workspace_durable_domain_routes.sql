-- Domain rows are desired state, not evidence of a deployed or verified route.
-- Existing aiWorkspaceManagedDomains metadata is imported by the worker only as
-- cleanup ownership. No historic success, deployment UUID or verification is inferred.
CREATE TABLE ai_workspace_route_reconciliations (
    route_key VARCHAR(64) PRIMARY KEY,
    service_id VARCHAR(96) NOT NULL UNIQUE,
    target_profile_id VARCHAR(96) NOT NULL,
    target_profile_fingerprint VARCHAR(64) NOT NULL,
    application_uuid VARCHAR(128) NOT NULL,
    generation BIGINT NOT NULL DEFAULT 0,
    desired_fingerprint VARCHAR(64) NOT NULL DEFAULT '',
    desired_domains TEXT NOT NULL DEFAULT '[]',
    managed_domains TEXT NOT NULL DEFAULT '[]',
    inflight_domains TEXT NOT NULL DEFAULT '[]',
    inflight_generation BIGINT NOT NULL DEFAULT 0,
    inflight_fingerprint VARCHAR(64) NOT NULL DEFAULT '',
    expected_provider_domains TEXT NOT NULL DEFAULT '[]',
    deployment_uuid VARCHAR(128),
    phase VARCHAR(24) NOT NULL DEFAULT 'IDLE',
    lease_token VARCHAR(64),
    lease_until TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(64),
    applied_generation BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_ai_workspace_route_application UNIQUE (target_profile_id, application_uuid),
    CONSTRAINT ck_ai_workspace_route_phase CHECK
        (phase IN ('IDLE', 'PATCHING', 'PATCHED', 'STARTING', 'POLLING', 'UNKNOWN', 'FAILED')),
    CONSTRAINT ck_ai_workspace_route_generations CHECK
        (generation >= 0 AND applied_generation >= 0 AND inflight_generation >= 0)
);
