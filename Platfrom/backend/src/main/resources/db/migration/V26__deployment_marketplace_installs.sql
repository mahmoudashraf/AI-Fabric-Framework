create table platform_deployment_marketplace_plugin_installs (
    id varchar(64) primary key,
    deployment_id varchar(64) not null,
    plugin_id varchar(64) not null,
    plugin_version_id varchar(64) not null,
    status varchar(64) not null,
    config_json text not null,
    secret_refs_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_deployment_marketplace_plugin_installs_deployment
        foreign key (deployment_id) references platform_deployments (id) on delete cascade,
    constraint fk_platform_deployment_marketplace_plugin_installs_plugin
        foreign key (plugin_id) references platform_marketplace_plugins (id) on delete cascade,
    constraint fk_platform_deployment_marketplace_plugin_installs_plugin_version
        foreign key (plugin_version_id) references platform_marketplace_plugin_versions (id) on delete cascade
);

create unique index uk_platform_deployment_marketplace_plugin_installs_unique
    on platform_deployment_marketplace_plugin_installs (deployment_id, plugin_id);

create index idx_platform_deployment_marketplace_plugin_installs_deployment
    on platform_deployment_marketplace_plugin_installs (deployment_id, updated_at desc);
