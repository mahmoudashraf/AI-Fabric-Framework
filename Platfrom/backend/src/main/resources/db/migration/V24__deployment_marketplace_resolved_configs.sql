ALTER TABLE platform_deployment_drafts
    ADD COLUMN knowledge_source_config_json TEXT NOT NULL DEFAULT '{"contractVersion":"KNOWLEDGE_SOURCE_CONFIG_V1","sources":[]}';

ALTER TABLE platform_deployment_drafts
    ADD COLUMN shell_config_json TEXT NOT NULL DEFAULT '{"contractVersion":"SHELL_CONFIG_V1","modules":[],"cards":[]}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN knowledge_source_config_json TEXT NOT NULL DEFAULT '{"contractVersion":"KNOWLEDGE_SOURCE_CONFIG_V1","sources":[]}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN shell_config_json TEXT NOT NULL DEFAULT '{"contractVersion":"SHELL_CONFIG_V1","modules":[],"cards":[]}';
