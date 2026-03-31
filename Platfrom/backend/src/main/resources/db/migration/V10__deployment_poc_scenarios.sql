CREATE TABLE platform_deployment_poc_scenarios (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(128) NOT NULL,
    prompt_text TEXT NOT NULL,
    expected_outcome TEXT,
    created_by_actor_id VARCHAR(255) NOT NULL,
    created_by_display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_poc_scenarios_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE
);

CREATE INDEX idx_platform_deployment_poc_scenarios_deployment
    ON platform_deployment_poc_scenarios (deployment_id, created_at DESC);
