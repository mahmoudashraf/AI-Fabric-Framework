-- Publish immutable dependency versions that explicitly support the reviewed Milvus base.
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-data-simulated-dealership-stock-v1-1', plugin_id, '1.1.0', release_channel, 'PUBLISHED',
    jsonb_set(
        jsonb_set(manifest_json::jsonb, '{version}', to_jsonb('1.1.0'::text), true),
        '{compatibility,supportedDeploymentTargets}',
        '["custom-start-from-scratch", "dev-openai-milvus"]'::jsonb,
        true
    )::text,
    submitted_by_publisher_id, submitted_by_actor_id, 'system', current_timestamp,
    'Adds the reviewed managed Milvus target without changing the stock connector contract.',
    'seeded-simulated-dealership-stock-v1-1', current_timestamp, current_timestamp
  from platform_marketplace_plugin_versions
 where id = 'mkv-data-simulated-dealership-stock-v1-0'
on conflict (id) do nothing;

insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-action-simulated-dealership-discovery-v1-1', plugin_id, '1.1.0', release_channel, 'PUBLISHED',
    jsonb_set(
        jsonb_set(manifest_json::jsonb, '{version}', to_jsonb('1.1.0'::text), true),
        '{compatibility,supportedDeploymentTargets}',
        '["custom-start-from-scratch", "dev-openai-milvus"]'::jsonb,
        true
    )::text,
    submitted_by_publisher_id, submitted_by_actor_id, 'system', current_timestamp,
    'Adds the reviewed managed Milvus target without changing the discovery action contract.',
    'seeded-simulated-dealership-discovery-v1-1', current_timestamp, current_timestamp
  from platform_marketplace_plugin_versions
 where id = 'mkv-action-simulated-dealership-discovery-v1-0'
on conflict (id) do nothing;

insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-action-dealership-engagement-v1-2', plugin_id, '1.2.0', release_channel, 'PUBLISHED',
    jsonb_set(
        jsonb_set(manifest_json::jsonb, '{version}', to_jsonb('1.2.0'::text), true),
        '{compatibility,supportedDeploymentTargets}',
        '["custom-start-from-scratch", "dev-openai-milvus"]'::jsonb,
        true
    )::text,
    submitted_by_publisher_id, submitted_by_actor_id, 'system', current_timestamp,
    'Adds the reviewed managed Milvus target while preserving protected test-drive submission.',
    'seeded-dealership-engagement-v1-2', current_timestamp, current_timestamp
  from platform_marketplace_plugin_versions
 where id = 'mkv-action-dealership-engagement-v1-1'
on conflict (id) do nothing;

insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-action-conversation-handover-v1-1', plugin_id, '1.1.0', release_channel, 'PUBLISHED',
    jsonb_set(
        jsonb_set(manifest_json::jsonb, '{version}', to_jsonb('1.1.0'::text), true),
        '{compatibility,supportedDeploymentTargets}',
        '["custom-start-from-scratch", "dev-openai-milvus"]'::jsonb,
        true
    )::text,
    submitted_by_publisher_id, submitted_by_actor_id, 'system', current_timestamp,
    'Adds the reviewed managed Milvus target without changing conversation-control authority.',
    'seeded-conversation-handover-action-v1-1', current_timestamp, current_timestamp
  from platform_marketplace_plugin_versions
 where id = 'mkv-action-conversation-handover-v1'
on conflict (id) do nothing;

insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-integration-basic-loomai-inbox-v1-2', plugin_id, '1.2.0', release_channel, 'PUBLISHED',
    jsonb_set(
        jsonb_set(manifest_json::jsonb, '{version}', to_jsonb('1.2.0'::text), true),
        '{compatibility,supportedDeploymentTargets}',
        '["custom-start-from-scratch", "dev-openai-milvus"]'::jsonb,
        true
    )::text,
    submitted_by_publisher_id, submitted_by_actor_id, 'system', current_timestamp,
    'Adds the reviewed managed Milvus target while preserving target-scoped Inbox binding.',
    'seeded-conversation-handover-integration-v1-2', current_timestamp, current_timestamp
  from platform_marketplace_plugin_versions
 where id = 'mkv-integration-basic-loomai-inbox-v1-1'
on conflict (id) do nothing;

with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-3'
), template_shape as (
    select jsonb_set(
               jsonb_set(
                   jsonb_set(
                       jsonb_set(
                           jsonb_set(manifest, '{version}', to_jsonb('1.4.0'::text), true),
                           '{compatibility,supportedDeploymentTargets}',
                           '["dev-openai-milvus"]'::jsonb,
                           true
                       ),
                       '{contributions,template,templateId}',
                       to_jsonb('dev-openai-milvus'::text),
                       true
                   ),
                   '{contributions,template,providerConfig}',
                   '{
                     "vectorStrategy": "milvus",
                     "vectorProvisioningMode": "PLATFORM_MANAGED",
                     "vectorStoragePosture": "DEDICATED",
                     "zillizCloudProjectId": "proj-a58a34b87ccfe2c80d6ec2",
                     "zillizCloudRegionId": "aws-eu-central-1",
                     "zillizCloudClusterPlan": "Serverless",
                     "milvusPort": 443,
                     "milvusSecure": true,
                     "milvusDatabaseName": "default",
                     "milvusFlushOnWrite": false
                   }'::jsonb,
                   true
               ),
               '{contributions,template,requiredPluginRefs}',
               '[
                 "mkp-data-simulated-dealership-stock-v1@1.1.0",
                 "mkp-action-simulated-dealership-discovery-v1@1.1.0",
                 "mkp-action-dealership-engagement-v1@1.2.0",
                 "mkp-data-dealership-knowledge-v1@1.0.0",
                 "mkp-action-conversation-handover@1.1.0",
                 "mkp-integration-basic-loomai-inbox@1.2.0"
               ]'::jsonb,
               true
           ) as manifest,
           manifest #> '{contributions,template,requiredPluginInstallDefaults}' as install_defaults
      from source_manifest
), reviewed_manifest as (
    select jsonb_set(
               manifest,
               '{contributions,template,requiredPluginInstallDefaults}',
               (install_defaults
                   - 'mkp-data-simulated-dealership-stock-v1@1.0.0'
                   - 'mkp-action-dealership-engagement-v1@1.1.0'
                   - 'mkp-integration-basic-loomai-inbox@1.1.0')
               || jsonb_build_object(
                   'mkp-data-simulated-dealership-stock-v1@1.1.0',
                   install_defaults -> 'mkp-data-simulated-dealership-stock-v1@1.0.0',
                   'mkp-action-dealership-engagement-v1@1.2.0',
                   install_defaults -> 'mkp-action-dealership-engagement-v1@1.1.0',
                   'mkp-integration-basic-loomai-inbox@1.2.0',
                   install_defaults -> 'mkp-integration-basic-loomai-inbox@1.1.0'
               ),
               true
           ) as manifest
      from template_shape
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-template-simulated-dealership-experience-v1-4',
    'mkp-template-simulated-dealership-experience-v1',
    '1.4.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Pins compatible immutable dependencies and the managed Zilliz/Milvus baseline required before customer-owned document storage can be enabled.',
    'seeded-simulated-dealership-experience-v1-4',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;
