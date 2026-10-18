-- Retained external clusters are exclusive to one reviewed deployment/target.
-- Admission and cleanup never treat these rows as owned provider capacity.
create unique index uq_active_external_vector_binding_cluster
    on deployment_provider_resource_handles(provider_resource_uuid)
    where resource_kind = 'EXTERNAL_VECTOR_STORAGE_BINDING' and status = 'ACTIVE';

with source_manifest as (
    select manifest_json::jsonb as manifest
      from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-6'
), candidate_manifest as (
    select jsonb_set(
               jsonb_set(manifest, '{version}', to_jsonb('1.7.0'::text), true),
               '{contributions,template,providerConfig}',
               ((manifest #> '{contributions,template,providerConfig}')
                   - 'milvusHost' - 'milvusDatabaseName' - 'zillizCloudProjectId'
                   - 'zillizCloudRegionId' - 'zillizCloudClusterPlan'
                   - 'zillizCloudCuType' - 'zillizCloudCuSize')
                   || jsonb_build_object('vectorProvisioningMode', 'EXTERNAL_EXISTING',
                       'vectorStoragePosture', 'SHARED', 'milvusExternalBindingRequired', true),
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
    'mkv-template-simulated-dealership-experience-v1-7',
    'mkp-template-simulated-dealership-experience-v1',
    '1.7.0', 'EARLY_ACCESS', 'PUBLISHED', manifest::text,
    'mpub-loom', 'system', 'system', current_timestamp,
    'Full-feature measurement candidate requiring an explicit retained external vector binding per deployment/target/version. No default endpoint, credentials, provider allocation or production-fit approval.',
    encode(sha256(convert_to(manifest::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp
  from candidate_manifest
on conflict (id) do nothing;
