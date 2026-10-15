with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-integration-basic-loomai-inbox-v1'
), reviewed_manifest as (
    select jsonb_set(
               jsonb_set(
                   jsonb_set(
                       manifest #- '{contributions,conversationChannel,bindingId}',
                       '{version}',
                       to_jsonb('1.1.0'::text),
                       true
                   ),
                   '{contributions,conversationChannel,bindingIdField}',
                   to_jsonb('bindingId'::text),
                   true
               ),
               '{installForm}',
               (manifest -> 'installForm') || jsonb_build_array(jsonb_build_object(
                   'id', 'bindingId',
                   'label', 'Target-scoped channel binding ID',
                   'type', 'text',
                   'required', true,
                   'description', 'Reviewed target-scoped binding identity supplied by the deployment lifecycle.'
               )),
               true
           ) as manifest
      from source_manifest
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-integration-basic-loomai-inbox-v1-1',
    'mkp-integration-basic-loomai-inbox',
    '1.1.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Adds a reviewed target-scoped binding ID install field for managed Basic Inbox instances.',
    'seeded-conversation-handover-integration-v1-1',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;

with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-0'
), transformed as (
    select
        jsonb_set(
            jsonb_set(
                manifest,
                '{version}',
                to_jsonb('1.1.0'::text),
                true
            ),
            '{contributions,template,requiredPluginRefs}',
            '[
              "mkp-data-simulated-dealership-stock-v1@1.0.0",
              "mkp-action-simulated-dealership-discovery-v1@1.0.0",
              "mkp-action-dealership-engagement-v1@1.0.0",
              "mkp-data-dealership-knowledge-v1@1.0.0",
              "mkp-action-conversation-handover@1.0.0",
              "mkp-integration-basic-loomai-inbox@1.1.0"
            ]'::jsonb,
            true
        ) as manifest,
        (
            manifest #> '{contributions,template,requiredPluginInstallDefaults}'
        ) - 'mkp-integration-basic-loomai-inbox@1.0.0' as defaults_without_inbox,
        jsonb_set(
            manifest #> array[
                'contributions',
                'template',
                'requiredPluginInstallDefaults',
                'mkp-integration-basic-loomai-inbox@1.0.0'
            ],
            '{config,bindingId}',
            to_jsonb(chr(36) || '{MANAGED_INBOX_BINDING_ID}'),
            true
        ) as target_scoped_inbox_defaults
      from source_manifest
), reviewed_manifest as (
    select jsonb_set(
               manifest,
               '{contributions,template,requiredPluginInstallDefaults}',
               jsonb_set(
                   defaults_without_inbox,
                   array['mkp-integration-basic-loomai-inbox@1.1.0'],
                   target_scoped_inbox_defaults,
                   true
               ),
               true
           ) as manifest
      from transformed
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-template-simulated-dealership-experience-v1-1',
    'mkp-template-simulated-dealership-experience-v1',
    '1.1.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Binds each managed Basic Inbox instance to its generated target-scoped conversation channel identity.',
    'seeded-simulated-dealership-experience-v1-1',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;
