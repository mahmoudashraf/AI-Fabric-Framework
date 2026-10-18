-- Correct the immutable candidate's advertised extension contract. Ordinary
-- confirmed chat actions remain unchanged; they are not Governed Resolver APIs.
with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-8'
), candidate_manifest as (
    select jsonb_set(
               jsonb_set(
                   jsonb_set(manifest, '{version}', to_jsonb('1.9.0'::text), true),
                   '{contributions,template,deploymentBehavior,allowedExecutionExtensions}',
                   '["CONVERSATION_HANDOVER"]'::jsonb, true
               ),
               '{contributions,template,deploymentBehavior,defaultExecutionExtensions}',
               '["CONVERSATION_HANDOVER"]'::jsonb, true
           ) as manifest
      from source_manifest
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select
    'mkv-template-simulated-dealership-experience-v1-9',
    'mkp-template-simulated-dealership-experience-v1',
    '1.9.0', 'EARLY_ACCESS', 'PUBLISHED', manifest::text,
    'mpub-loom', 'system', 'system', current_timestamp,
    'Accurate runtime extension contract: ordinary confirmed actions and conversation handover remain; the distinct unimplemented Governed Resolver API lifecycle is not advertised. No provider allocation or production readiness is seeded.',
    encode(sha256(convert_to(manifest::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp
  from candidate_manifest
on conflict (id) do nothing;
