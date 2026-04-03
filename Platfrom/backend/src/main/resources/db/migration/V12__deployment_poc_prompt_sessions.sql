CREATE TABLE platform_deployment_poc_prompt_sessions (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    actor_display_name VARCHAR(255),
    session_label VARCHAR(255),
    prompt_preview_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_poc_prompt_sessions_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE,
    CONSTRAINT uq_platform_deployment_poc_prompt_sessions_actor
        UNIQUE (deployment_id, actor_id)
);

CREATE INDEX idx_platform_deployment_poc_prompt_sessions_deployment
    ON platform_deployment_poc_prompt_sessions (deployment_id, actor_id);
