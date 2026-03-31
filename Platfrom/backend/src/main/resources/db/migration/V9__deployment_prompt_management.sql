ALTER TABLE platform_deployment_drafts
    ADD COLUMN prompt_config_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN prompt_config_json TEXT NOT NULL DEFAULT '{}';

CREATE TABLE platform_deployment_prompt_revisions (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    source_draft_id VARCHAR(64) NOT NULL,
    revision_label VARCHAR(255) NOT NULL,
    revision_summary TEXT,
    prompt_config_json TEXT NOT NULL,
    created_by_actor_id VARCHAR(255) NOT NULL,
    created_by_display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_platform_prompt_revisions_deployment
    ON platform_deployment_prompt_revisions (deployment_id, created_at DESC);
