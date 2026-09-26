alter table platform_marketplace_plugin_datasets
    add column if not exists source_connector_config_json text not null default '{}';

alter table platform_marketplace_plugin_datasets
    add column if not exists document_policy_json text not null default '{}';

alter table platform_marketplace_dataset_handles
    add column if not exists scope_key varchar(128) not null default '__TENANT_SHARED__';

update platform_marketplace_dataset_handles
set scope_key = case
    when upper(sharing_scope) = 'DEPLOYMENT_ONLY' then deployment_id
    else '__TENANT_SHARED__'
end;

alter table platform_marketplace_dataset_handles
    drop constraint if exists uq_marketplace_dataset_handle;

alter table platform_marketplace_dataset_handles
    add constraint uq_marketplace_dataset_handle_scope
        unique (plugin_id, tenant_id, dataset_id, scope_key);
