alter table platform_deployment_drafts
    add column knowledge_source_config_json text not null default '{}';

alter table platform_deployment_versions
    add column knowledge_source_config_json text not null default '{}';
