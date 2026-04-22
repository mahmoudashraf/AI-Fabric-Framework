alter table platform_deployment_drafts
    add column automation_config_json text not null
        default '{"contractVersion":"AUTOMATION_CONFIG_V1","triggers":[],"actions":[],"workflows":[],"schedules":[]}';

alter table platform_deployment_versions
    add column automation_config_json text not null
        default '{"contractVersion":"AUTOMATION_CONFIG_V1","triggers":[],"actions":[],"workflows":[],"schedules":[]}';
