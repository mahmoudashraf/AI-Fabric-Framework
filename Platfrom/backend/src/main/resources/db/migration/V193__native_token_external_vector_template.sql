-- New immutable template only. Existing published versions, bindings and
-- provider resources are untouched; no credentials or endpoint are seeded.
with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-7'
), candidate_manifest as (
    select jsonb_set(
               jsonb_set(manifest, '{version}', to_jsonb('1.8.0'::text), true),
               '{contributions,template,providerConfig}',
               (manifest #> '{contributions,template,providerConfig}')
                   || jsonb_build_object('milvusAuthenticationType', 'TOKEN'),
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
    'mkv-template-simulated-dealership-experience-v1-8',
    'mkp-template-simulated-dealership-experience-v1',
    '1.8.0', 'EARLY_ACCESS', 'PUBLISHED', manifest::text,
    'mpub-loom', 'system', 'system', current_timestamp,
    'Requires native Milvus token authentication and a reviewed retained external binding. No endpoint, credential, paid provider allocation or production approval is seeded.',
    encode(sha256(convert_to(manifest::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp
  from candidate_manifest
on conflict (id) do nothing;
