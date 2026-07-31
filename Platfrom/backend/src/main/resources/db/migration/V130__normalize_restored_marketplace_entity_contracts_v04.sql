-- Portable deployment restore can add customer Marketplace versions after the
-- original V0_4 catalog migration has run. Normalize any such rows before the
-- strict Marketplace startup validator reads them.
with candidate_versions as (
    select
        version.id,
        version.manifest_json::jsonb as manifest,
        version.manifest_json::jsonb #> '{contributions,entityConfig,ai-entities}' as entities
    from platform_marketplace_plugin_versions version
    where jsonb_typeof(
        version.manifest_json::jsonb #> '{contributions,entityConfig,ai-entities}'
    ) = 'object'
),
migrated_entities as (
    select
        candidate.id,
        jsonb_object_agg(
            entity.key,
            case
                when entity.value ?| array[
                    'entity-type',
                    'features',
                    'auto-process',
                    'enable-search',
                    'enable-recommendations',
                    'auto-embedding',
                    'indexable',
                    'embeddable-fields',
                    'crud-operations',
                    'description'
                ]
                then jsonb_strip_nulls(
                    jsonb_build_object(
                        'indexing',
                        jsonb_build_object(
                            'enabled',
                            case
                                when jsonb_typeof(entity.value #> '{indexing,enabled}') = 'boolean'
                                    then (entity.value #>> '{indexing,enabled}')::boolean
                                when jsonb_typeof(entity.value -> 'indexable') = 'boolean'
                                    then (entity.value ->> 'indexable')::boolean
                                else true
                            end,
                            'max-characters',
                            case
                                when (entity.value #>> '{indexing,max-characters}') ~ '^[0-9]{1,4}$'
                                    and (entity.value #>> '{indexing,max-characters}')::integer between 1 and 8000
                                    then (entity.value #>> '{indexing,max-characters}')::integer
                                else 8000
                            end
                        ),
                        'analysis',
                        jsonb_build_object(
                            'enabled', false,
                            'after', jsonb_build_array()
                        ),
                        'searchable-fields',
                        jsonb_build_array(
                            jsonb_build_object(
                                'name', 'content',
                                'destinations', jsonb_build_array('SEMANTIC_SEARCH', 'RAG_CONTEXT'),
                                'preprocessing', 'CLEAN',
                                'max-length', 8000,
                                'priority', 100,
                                'required', true
                            )
                        ),
                        'metadata-fields',
                        jsonb_build_array(
                            jsonb_build_object(
                                'name', 'title',
                                'data-type', 'STRING',
                                'description', 'Human-readable dataset record title.',
                                'destinations', jsonb_build_array(
                                    'VECTOR_METADATA',
                                    'LLM_CONTEXT',
                                    'API_RESPONSE'
                                ),
                                'priority', 90,
                                'required', false,
                                'sanitize-pii', false
                            ),
                            jsonb_build_object(
                                'name', 'scope',
                                'data-type', 'STRING',
                                'description', 'Dataset-defined retrieval scope.',
                                'destinations', jsonb_build_array(
                                    'VECTOR_METADATA',
                                    'LLM_CONTEXT'
                                ),
                                'priority', 80,
                                'required', false,
                                'sanitize-pii', false
                            ),
                            jsonb_build_object(
                                'name', 'tenantId',
                                'data-type', 'ID',
                                'description', 'Server-owned tenant isolation key.',
                                'destinations', jsonb_build_array('VECTOR_METADATA'),
                                'priority', 100,
                                'required', true,
                                'sanitize-pii', false
                            )
                        ),
                        'marketplaceManaged', entity.value -> 'marketplaceManaged',
                        'marketplacePluginId', entity.value -> 'marketplacePluginId',
                        'marketplaceInstallId', entity.value -> 'marketplaceInstallId',
                        'marketplacePluginVersion', entity.value -> 'marketplacePluginVersion'
                    )
                )
                else entity.value
            end
            order by entity.key
        ) as entities
    from candidate_versions candidate
    cross join lateral jsonb_each(candidate.entities) entity
    group by candidate.id
    having bool_or(
        entity.value ?| array[
            'entity-type',
            'features',
            'auto-process',
            'enable-search',
            'enable-recommendations',
            'auto-embedding',
            'indexable',
            'embeddable-fields',
            'crud-operations',
            'description'
        ]
    )
),
migrated_versions as (
    select
        candidate.id,
        jsonb_set(
            candidate.manifest,
            '{contributions,entityConfig,ai-entities}',
            migrated.entities,
            false
        ) as manifest
    from candidate_versions candidate
    join migrated_entities migrated on migrated.id = candidate.id
)
update platform_marketplace_plugin_versions version
set manifest_json = migrated.manifest::text
from migrated_versions migrated
where version.id = migrated.id;
