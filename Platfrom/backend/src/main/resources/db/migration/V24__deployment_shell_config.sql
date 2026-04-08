ALTER TABLE platform_deployment_drafts
    ADD COLUMN shell_config_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN shell_config_json TEXT NOT NULL DEFAULT '{}';
