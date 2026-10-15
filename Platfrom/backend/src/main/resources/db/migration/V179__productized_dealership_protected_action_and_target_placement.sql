with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-action-dealership-engagement-v1-0'
), reviewed_manifest as (
    select jsonb_set(
               jsonb_set(
                   manifest,
                   '{version}',
                   to_jsonb('1.1.0'::text),
                   true
               ),
               '{contributions,actions}',
               (
                   select jsonb_agg(
                       case
                           when action ->> 'actionId' = 'dealership_request_test_drive'
                               then jsonb_set(action, '{anonymousAllowed}', 'false'::jsonb, true)
                           else action
                       end
                       order by ordinal
                   )
                     from jsonb_array_elements(manifest #> '{contributions,actions}')
                          with ordinality as actions(action, ordinal)
               ),
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
    'mkv-action-dealership-engagement-v1-1',
    'mkp-action-dealership-engagement-v1',
    '1.1.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Keeps callback available anonymously while requiring verified customer identity before test-drive submission.',
    'seeded-dealership-engagement-v1-1',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;

with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-1'
), transformed as (
    select
        jsonb_set(
            jsonb_set(
                manifest,
                '{version}',
                to_jsonb('1.2.0'::text),
                true
            ),
            '{contributions,template,requiredPluginRefs}',
            (
                select jsonb_agg(
                    case
                        when reference #>> '{}' = 'mkp-action-dealership-engagement-v1@1.0.0'
                            then to_jsonb('mkp-action-dealership-engagement-v1@1.1.0'::text)
                        else reference
                    end
                    order by ordinal
                )
                  from jsonb_array_elements(manifest #> '{contributions,template,requiredPluginRefs}')
                       with ordinality as plugin_refs(reference, ordinal)
            ),
            true
        ) as manifest,
        manifest #> '{contributions,template,requiredPluginInstallDefaults}' as install_defaults
      from source_manifest
), reviewed_manifest as (
    select jsonb_set(
               manifest,
               '{contributions,template,requiredPluginInstallDefaults}',
               jsonb_set(
                   install_defaults - 'mkp-action-dealership-engagement-v1@1.0.0',
                   array['mkp-action-dealership-engagement-v1@1.1.0'],
                   install_defaults -> 'mkp-action-dealership-engagement-v1@1.0.0',
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
    'mkv-template-simulated-dealership-experience-v1-2',
    'mkp-template-simulated-dealership-experience-v1',
    '1.2.0',
    'EARLY_ACCESS',
    'PUBLISHED',
    manifest::text,
    'mpub-loom',
    'system',
    'system',
    current_timestamp,
    'Pins the reviewed engagement action version with protected test-drive submission.',
    'seeded-simulated-dealership-experience-v1-2',
    current_timestamp,
    current_timestamp
  from reviewed_manifest
on conflict (id) do nothing;

-- Customer staging is admitted only on the isolated staging Coolify fleet.
-- Keep this profile for historical release/retirement readback, but prevent new selection.
update deployment_target_profiles
   set active = false,
       default_for_runtime = false,
       default_for_restartable_services = false,
       updated_at = current_timestamp
 where id = 'dtp-coolify-prod-staging';
