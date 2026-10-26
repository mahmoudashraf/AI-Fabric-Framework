create table platform_desk_bindings (
    id varchar(64) primary key,
    customer_id varchar(64) not null,
    tenant_id varchar(64) not null,
    deployment_id varchar(64) not null references platform_deployments(id),
    target_profile_id varchar(64) not null references deployment_target_profiles(id),
    source_release_id varchar(64) not null references platform_deployment_releases(id),
    operation_key varchar(128) not null,
    status varchar(32) not null,
    release_id varchar(64) references platform_deployment_releases(id),
    version_id varchar(64) references platform_deployment_versions(id),
    provider_key_ref varchar(255) not null,
    callback_key_ref varchar(255) not null,
    runtime_callback_key_ref varchar(255) not null,
    bridge_key_ref varchar(255) not null,
    error_message varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_platform_desk_target unique (deployment_id, target_profile_id)
);

insert into platform_marketplace_plugins
    (id, slug, display_name, plugin_type, publisher_slug, publisher_display_name,
     short_description, status, created_at, updated_at)
values ('mkp-integration-loomai-desk', 'loomai-desk', 'LoomAI Desk', 'INTEGRATION',
        'loom', 'Loom AI Labs', 'Managed human conversations and registered deployment support agents.',
        'ACTIVE', current_timestamp, current_timestamp);

insert into platform_marketplace_plugin_versions
    (id, plugin_id, version, release_channel, status, manifest_json,
     submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
     reviewed_at, review_notes, bundle_sha256, created_at, published_at)
select 'mkv-integration-loomai-desk-v1', 'mkp-integration-loomai-desk', '1.0.0',
       'EARLY_ACCESS', 'PUBLISHED',
       (manifest_json::jsonb || jsonb_build_object(
           'pluginId', 'mkp-integration-loomai-desk', 'version', '1.0.0',
           'displayName', 'LoomAI Desk',
           'description', 'Shared LoomAI-managed conversation-channel provider. Binding credentials are provisioned server-side.'
       ))::text,
       'mpub-loom', 'system', 'system', current_timestamp,
       'Reuses LOOMAI_CONVERSATION_CHANNEL_V1 and the existing HTTP adapter without runtime changes.',
       'seeded-loomai-desk-channel-v1', current_timestamp, current_timestamp
from platform_marketplace_plugin_versions
where id = 'mkv-integration-basic-loomai-inbox-v1-3';

-- New bootstrap revisions do not provision an Inbox application or database.
-- Desk is enabled against an actual target through its normal configuration lifecycle.
with sources as (
    select distinct on (v.plugin_id) v.*
    from platform_marketplace_plugin_versions v
    join platform_marketplace_plugins p on p.id = v.plugin_id
    where p.plugin_type = 'TEMPLATE' and v.status = 'PUBLISHED'
      and exists (select 1 from jsonb_array_elements_text(
          coalesce(v.manifest_json::jsonb #> '{contributions,template,requiredPluginRefs}', '[]'::jsonb)) r
          where r like 'mkp-integration-basic-loomai-inbox@%')
    order by v.plugin_id, v.published_at desc, v.id desc
), revisions as (
    select *, split_part(version, '.', 1) || '.' || (split_part(version, '.', 2)::int + 1) || '.0' as desk_version
    from sources
), manifests as (
    select *, jsonb_set(jsonb_set(jsonb_set(jsonb_set(manifest_json::jsonb,
        '{version}', to_jsonb(desk_version)),
        '{contributions,template,requiredPluginRefs}',
        coalesce((select jsonb_agg(r) from jsonb_array_elements_text(
            manifest_json::jsonb #> '{contributions,template,requiredPluginRefs}') r
            where r not like 'mkp-integration-basic-loomai-inbox@%'), '[]'::jsonb)),
        '{contributions,template,requiredPluginInstallDefaults}',
        coalesce((select jsonb_object_agg(k, val) from jsonb_each(
            coalesce(manifest_json::jsonb #> '{contributions,template,requiredPluginInstallDefaults}', '{}'::jsonb)) e(k, val)
            where k not like 'mkp-integration-basic-loomai-inbox@%'), '{}'::jsonb)),
        '{contributions,template,deploymentBehavior,defaultExecutionExtensions}',
        coalesce((select jsonb_agg(r) from jsonb_array_elements_text(coalesce(
            manifest_json::jsonb #> '{contributions,template,deploymentBehavior,defaultExecutionExtensions}', '[]'::jsonb)) r
            where r <> 'CONVERSATION_HANDOVER'), '[]'::jsonb)) as desk_manifest
    from revisions
)
insert into platform_marketplace_plugin_versions
    (id, plugin_id, version, release_channel, status, manifest_json,
     submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
     reviewed_at, review_notes, bundle_sha256, created_at, published_at)
select 'mkv-desk-' || substr(md5(id), 1, 24), plugin_id, desk_version, release_channel,
       'PUBLISHED', desk_manifest::text, submitted_by_publisher_id, 'system', 'system',
       current_timestamp, 'Desk-ready bootstrap; no per-deployment Inbox managed dependency.',
       'seeded-desk-template-' || substr(md5(id), 1, 24), current_timestamp, current_timestamp
from manifests
on conflict (plugin_id, version) do nothing;
