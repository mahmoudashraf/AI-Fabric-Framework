ALTER TABLE platform_deployments ADD COLUMN template_defaults_json TEXT;

-- Existing deployments retain their first recorded draft as their initial settings.
-- New deployments capture defaults at bootstrap, before customer editing.
UPDATE platform_deployments p SET template_defaults_json = jsonb_build_object(
    'actionsConfig', d.actions_config_json::jsonb,
    'entityConfig', d.entity_config_json::jsonb,
    'routingConfig', d.routing_config_json::jsonb,
    'providerConfig', d.provider_config_json::jsonb,
    'securityConfig', d.security_config_json::jsonb,
    'promptConfig', d.prompt_config_json::jsonb,
    'knowledgeSourceConfig', d.knowledge_source_config_json::jsonb,
    'shellConfig', d.shell_config_json::jsonb,
    'marketplaceDatasetConfig', d.marketplace_dataset_config_json::jsonb,
    'behaviorConfig', d.behavior_config_json::jsonb
)::text
FROM platform_deployment_drafts d
WHERE d.id = (SELECT original.id FROM platform_deployment_drafts original
    WHERE original.deployment_id = p.id ORDER BY original.revision_number, original.id LIMIT 1);

UPDATE platform_deployments SET approval_required_for_apply = false;
