-- New immutable, full-feature measurement candidate. These CPU caps are not
-- measured operating capacity or production admission, and seed no grants.
with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-5'
), candidate_manifest as (
    select jsonb_set(
               jsonb_set(manifest, '{version}', to_jsonb('1.6.0'::text), true),
               '{contributions,template,providerConfig}',
               (manifest #> '{contributions,template,providerConfig}')
                   || jsonb_build_object('openaiMaxTokens', 2048),
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
    'mkv-template-simulated-dealership-experience-v1-6',
    'mkp-template-simulated-dealership-experience-v1',
    '1.6.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Full-feature staging measurement candidate with an explicit 2048-token output cap; not measured or approved for production.',
    encode(sha256(convert_to(manifest::text, 'UTF8')), 'hex'),
    current_timestamp,
    current_timestamp
  from candidate_manifest
on conflict (id) do nothing;

-- Target metadata only: leave existing workloads, RAM, JVM settings, CPU sets,
-- runtime/database limits and every other property unchanged.
update deployment_target_profiles
set resource_defaults_json = jsonb_set(
        jsonb_set(resource_defaults_json::jsonb,
            '{connectorLimitsCpus}', to_jsonb('0.25'::text), true),
        '{vectorizationRunnerLimitsCpus}', to_jsonb('0.25'::text), true
    )::text,
    updated_at = current_timestamp
where id in ('dtp-coolify-staging-behavior', 'dtp-coolify-production-behavior');
