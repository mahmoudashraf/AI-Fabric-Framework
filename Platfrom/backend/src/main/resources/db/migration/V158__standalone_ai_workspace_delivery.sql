ALTER TABLE ai_workspace_installations
    ADD COLUMN delivery_mode VARCHAR(32) NOT NULL DEFAULT 'EMBEDDED',
    ADD COLUMN app_slug VARCHAR(80),
    ADD COLUMN interaction_policy VARCHAR(64) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN auth_profile_code VARCHAR(96) NOT NULL DEFAULT 'none',
    ADD COLUMN auth_profile_version VARCHAR(64) NOT NULL DEFAULT '1.0.0',
    ADD COLUMN auth_configuration_json TEXT NOT NULL DEFAULT '{}';

CREATE UNIQUE INDEX ux_ai_workspace_installations_app_slug
    ON ai_workspace_installations (LOWER(app_slug))
    WHERE app_slug IS NOT NULL;

CREATE TABLE ai_workspace_domains (
    id VARCHAR(96) PRIMARY KEY,
    installation_entity_id VARCHAR(96) NOT NULL,
    hostname VARCHAR(253) NOT NULL,
    domain_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    verification_token VARCHAR(128) NOT NULL,
    last_verification_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_workspace_domain_installation
        FOREIGN KEY (installation_entity_id) REFERENCES ai_workspace_installations(id) ON DELETE CASCADE,
    CONSTRAINT ux_ai_workspace_domain_hostname UNIQUE (hostname)
);

CREATE INDEX ix_ai_workspace_domains_installation
    ON ai_workspace_domains (installation_entity_id, status);
