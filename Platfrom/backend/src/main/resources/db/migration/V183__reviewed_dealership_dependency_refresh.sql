with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-2'
), reviewed_manifest as (
    select jsonb_set(
               manifest,
               '{version}',
               to_jsonb('1.3.0'::text),
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
    'mkv-template-simulated-dealership-experience-v1-3',
    'mkp-template-simulated-dealership-experience-v1',
    '1.3.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Creates a new immutable template boundary for the reviewed managed-dependency image refresh.',
    'seeded-simulated-dealership-experience-v1-3',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;
