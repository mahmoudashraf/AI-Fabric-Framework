-- Publish immutable handover products that require the canonical conversation
-- orchestration-state contract consumed from AI Fabric 0.8.35.
with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-integration-basic-loomai-inbox-v1-2'
), reviewed_manifest as (
    select jsonb_set(
               jsonb_set(
                   jsonb_set(
                       manifest,
                       '{version}',
                       to_jsonb('1.3.0'::text),
                       true
                   ),
                   '{contributions,conversationChannel,requiredRuntimeCapabilityIds}',
                   '[
                     "conversation-control",
                     "canonical-conversation-orchestration-state",
                     "conversation-channel-outbox",
                     "conversation-event-stream"
                   ]'::jsonb,
                   true
               ),
               '{contributions,conversationChannel,requiredMigrationIds}',
               '[
                 "loomai-conversation-handover-v1",
                 "loomai-conversation-orchestration-state-v1"
               ]'::jsonb,
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
    'mkv-integration-basic-loomai-inbox-v1-3',
    'mkp-integration-basic-loomai-inbox',
    '1.3.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Requires deployment-local canonical orchestration state for pending confirmations, action drafts and trusted follow-up targets.',
    'seeded-conversation-handover-integration-v1-3',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;

with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-conversation-handover-v1-0-1'
), reviewed_manifest as (
    select jsonb_set(
               jsonb_set(
                   manifest,
                   '{version}',
                   to_jsonb('1.0.2'::text),
                   true
               ),
               '{contributions,template,requiredPluginRefs}',
               '[
                 "mkp-action-conversation-handover@1.0.0",
                 "mkp-integration-basic-loomai-inbox@1.3.0"
               ]'::jsonb,
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
    'mkv-template-conversation-handover-v1-0-2',
    'mkp-template-conversation-handover',
    '1.0.2',
    'GA',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Pins the canonical conversation orchestration-state capable Inbox integration.',
    'seeded-conversation-handover-template-v1-0-2',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;

with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-4'
), template_shape as (
    select jsonb_set(
               jsonb_set(
                   manifest,
                   '{version}',
                   to_jsonb('1.5.0'::text),
                   true
               ),
               '{contributions,template,requiredPluginRefs}',
               '[
                 "mkp-data-simulated-dealership-stock-v1@1.1.0",
                 "mkp-action-simulated-dealership-discovery-v1@1.1.0",
                 "mkp-action-dealership-engagement-v1@1.2.0",
                 "mkp-data-dealership-knowledge-v1@1.0.0",
                 "mkp-action-conversation-handover@1.1.0",
                 "mkp-integration-basic-loomai-inbox@1.3.0"
               ]'::jsonb,
               true
           ) as manifest,
           manifest #> '{contributions,template,requiredPluginInstallDefaults}' as install_defaults
      from source_manifest
), reviewed_manifest as (
    select jsonb_set(
               manifest,
               '{contributions,template,requiredPluginInstallDefaults}',
               (install_defaults - 'mkp-integration-basic-loomai-inbox@1.2.0')
               || jsonb_build_object(
                   'mkp-integration-basic-loomai-inbox@1.3.0',
                   install_defaults -> 'mkp-integration-basic-loomai-inbox@1.2.0'
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
    'mkv-template-simulated-dealership-experience-v1-5',
    'mkp-template-simulated-dealership-experience-v1',
    '1.5.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Pins the canonical conversation orchestration-state capable Inbox integration for promotable dealership deployments.',
    'seeded-simulated-dealership-experience-v1-5',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;
