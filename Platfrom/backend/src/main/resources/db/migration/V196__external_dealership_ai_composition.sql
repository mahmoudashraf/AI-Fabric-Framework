-- New immutable integration contracts. Business systems and provider fixtures are
-- external authorities; this migration creates no dependency spec or customer offer.
-- Historical manifests, dependency graphs, reservations and entitlements stay intact.
insert into platform_marketplace_plugins (
    id, slug, display_name, plugin_type, publisher_slug, publisher_display_name,
    short_description, status, created_at, updated_at
) values
    ('mkp-data-dealership-external-stock-v1', 'dealership-external-stock-v1',
     'External Dealership Stock', 'DATA', 'loom', 'Loom AI',
     'Explicit external stock connection. Deployment-local projections are derived evidence, not an inventory system of record.',
     'ACTIVE', current_timestamp, current_timestamp),
    ('mkp-action-dealership-external-discovery-v1', 'dealership-external-discovery-v1',
     'External Dealership Discovery', 'ACTION', 'loom', 'Loom AI',
     'Search, detail and comparison over a freshness-bounded external stock projection.',
     'ACTIVE', current_timestamp, current_timestamp),
    ('mkp-action-dealership-external-engagement-v1', 'dealership-external-engagement-v1',
     'External Dealership Engagement', 'ACTION', 'loom', 'Loom AI',
     'Confirmed callback and protected test-drive requests to an explicitly bound external business service. No CRM is provisioned.',
     'ACTIVE', current_timestamp, current_timestamp),
    ('mkp-template-dealership-external-systems-v1', 'dealership-external-systems-v1',
     'Dealership AI - External Systems', 'TEMPLATE', 'loom', 'Loom AI',
     'AI integration with externally operated stock, business actions and Inbox. Explicit connection and AI persistence review required.',
     'ACTIVE', current_timestamp, current_timestamp),
    ('mkp-template-dealership-external-systems-managed-inbox-v1', 'dealership-external-systems-managed-inbox-v1',
     'Dealership AI - Optional Managed Inbox', 'TEMPLATE', 'loom', 'Loom AI',
     'Explicitly selected AI conversation Inbox hosting; stock and business actions remain external. No inventory, lead or business database provisioning.',
     'ACTIVE', current_timestamp, current_timestamp)
on conflict (id) do nothing;

with source as (
    select manifest_json::jsonb as m from platform_marketplace_plugin_versions
     where id = 'mkv-data-simulated-dealership-stock-v1-1'
), dataset as (
    select m, m #> '{contributions,datasets,0}' as d from source
), external_dataset as (
    select m,
        jsonb_set(jsonb_set(jsonb_set(jsonb_set(jsonb_set(jsonb_set(
            d || '{"datasetId":"dealership-external-stock"}'::jsonb,
            '{syncConnector,connectionProfile,profileId}', '"external-stock-provider"'),
            '{syncConnector,connectionProfile,environment}', '"external"'),
            '{syncConnector,protectedResource,bindingId}', '"external-dealership-account"'),
            '{syncConnector,protectedResource,environment}', '"external"'),
            '{syncConnector,httpSource,sourceId}', '"dealership-external-stock-source"'),
            '{syncConnector,webhook,sourceId}', '"external-dealership-stock-events"') as d
      from dataset
), candidate as (
    select jsonb_set(jsonb_set(
        m || jsonb_build_object(
            'pluginId', 'mkp-data-dealership-external-stock-v1', 'version', '1.0.0',
            'displayName', 'External Dealership Stock',
            'description', 'Read-only synchronization from an explicitly configured external stock authority. Supports the reviewed token-exchange/stock/notification protocol, not arbitrary APIs or a claim of Auto Trader approval. A simulator may be bound only as a separately operated external fixture.',
            'installForm', '[
                {"id":"providerBaseUrl","label":"External stock provider URL","type":"url","required":true},
                {"id":"providerHost","label":"Approved external stock host","type":"text","required":true},
                {"id":"providerKey","label":"External stock API key reference","type":"secretRef","required":true},
                {"id":"providerSecret","label":"External stock API secret reference","type":"secretRef","required":true},
                {"id":"notificationSecret","label":"External notification verification secret reference","type":"secretRef","required":true},
                {"id":"advertiserId","label":"Authorized external advertiser ID","type":"text","required":true}
            ]'::jsonb),
        '{contributions,datasets}', jsonb_build_array(d)),
        '{contributions,knowledgeSources}', '[{
            "sourceType":"deployment-private-vector","sourceKey":"dealership-external-stock",
            "adapterType":"deployment-private-vector","datasetRef":"dealership-external-stock",
            "entityType":"dealer-vehicle","attributionLabel":"External stock - synchronized derived evidence"
        }]'::jsonb) as m from external_dataset
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select 'mkv-data-dealership-external-stock-v1-0', 'mkp-data-dealership-external-stock-v1',
    '1.0.0', 'EARLY_ACCESS', 'PUBLISHED', m::text, 'mpub-loom', 'system', 'system', current_timestamp,
    'Unbound external integration. No generated endpoints, account IDs, credentials, source mutation rights or provider access are granted.',
    encode(sha256(convert_to(m::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp from candidate
on conflict (id) do nothing;

with source as (
    select manifest_json::jsonb as m from platform_marketplace_plugin_versions
     where id = 'mkv-action-simulated-dealership-discovery-v1-1'
), candidate as (
    select jsonb_set(m || jsonb_build_object(
        'pluginId', 'mkp-action-dealership-external-discovery-v1', 'version', '1.0.0',
        'displayName', 'External Dealership Discovery',
        'description', 'Read-only search, detail and comparison over the external stock projection. Successful sync and bounded freshness remain mandatory; this cache is not an inventory authority.'),
        '{contributions,actions}', (
            select jsonb_agg(jsonb_set(jsonb_set(jsonb_set(a,
                '{route,sourceProjection,sourceRef}', '"dealership-external-stock-source"'),
                '{description}', to_jsonb(replace(a ->> 'description', 'fictional', 'external'))),
                '{route,response,message}', to_jsonb(replace(a #>> '{route,response,message}', 'fictional', 'external'))) order by n)
              from jsonb_array_elements(m #> '{contributions,actions}') with ordinality as actions(a, n)
        )) as m from source
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select 'mkv-action-dealership-external-discovery-v1-0', 'mkp-action-dealership-external-discovery-v1',
    '1.0.0', 'EARLY_ACCESS', 'PUBLISHED', m::text, 'mpub-loom', 'system', 'system', current_timestamp,
    'Preserves all three typed discovery actions, trusted canonical stock IDs and freshness checks. No business backend or second stock corpus.',
    encode(sha256(convert_to(m::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp from candidate
on conflict (id) do nothing;

with candidate as (
    select manifest_json::jsonb || jsonb_build_object(
        'pluginId', 'mkp-action-dealership-external-engagement-v1', 'version', '1.0.0',
        'displayName', 'External Dealership Engagement',
        'description', 'Confirmed callback and authenticated test-drive requests to an explicitly configured external business action endpoint. The external authority owns inventory validation, consent, idempotency and business receipts. No local leads, CRM or booking system is provisioned.',
        'installForm', '[
            {"id":"receiverActionUrl","label":"External business action URL","type":"url","required":true},
            {"id":"receiverApiKey","label":"External business action key reference","type":"secretRef","required":true}
        ]'::jsonb) as m from platform_marketplace_plugin_versions
     where id = 'mkv-action-dealership-engagement-v1-2'
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select 'mkv-action-dealership-external-engagement-v1-0', 'mkp-action-dealership-external-engagement-v1',
    '1.0.0', 'EARLY_ACCESS', 'PUBLISHED', m::text, 'mpub-loom', 'system', 'system', current_timestamp,
    'Preserves confirmation, evidence-bound stock IDs, protected test-drive auth and idempotency. External endpoint protocol must be reviewed; this does not claim generic CRM compatibility.',
    encode(sha256(convert_to(m::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp from candidate
on conflict (id) do nothing;

with variants(plugin_id, version_id, display_name, managed_inbox) as (values
    ('mkp-template-dealership-external-systems-v1', 'mkv-template-dealership-external-systems-v1-0',
     'Dealership AI - External Systems', false),
    ('mkp-template-dealership-external-systems-managed-inbox-v1', 'mkv-template-dealership-external-systems-managed-inbox-v1-0',
     'Dealership AI - Optional Managed Inbox', true)
), source as (
    select manifest_json::jsonb as m from platform_marketplace_plugin_versions
     where id = 'mkv-template-simulated-dealership-experience-v1-9'
), candidate as (
    select v.*,
        jsonb_set(jsonb_set(jsonb_set(jsonb_set(
            m || jsonb_build_object('pluginId', v.plugin_id, 'version', '1.0.0', 'displayName', v.display_name,
                'description', 'AI-only integration. Explicitly bind external stock and business actions, customer-owned documents and a reviewed conversation Inbox. No inventory/lead database, simulator, seeding or business system lifecycle. Durable AI operational persistence requires a separately approved hosting/binding profile.'),
            '{contributions,template,requiredPluginRefs}', '[
                "mkp-data-dealership-external-stock-v1@1.0.0",
                "mkp-action-dealership-external-discovery-v1@1.0.0",
                "mkp-action-dealership-external-engagement-v1@1.0.0",
                "mkp-data-dealership-knowledge-v1@1.0.0",
                "mkp-action-conversation-handover@1.1.0",
                "mkp-integration-basic-loomai-inbox@1.3.0"
            ]'::jsonb),
            '{contributions,template,requiredPluginInstallDefaults}',
            jsonb_build_object('mkp-data-dealership-knowledge-v1@1.0.0',
                '{"config":{"publicContentApprovalConfirmed":false}}'::jsonb)
            || case when v.managed_inbox then jsonb_build_object('mkp-integration-basic-loomai-inbox@1.3.0',
                m #> '{contributions,template,requiredPluginInstallDefaults,mkp-integration-basic-loomai-inbox@1.3.0}')
                else '{}'::jsonb end),
            '{contributions,template,shell,greeting,message}',
            '"Search synchronized external stock, compare vehicles, ask about approved documents, request confirmed follow-up or transfer to a person."'),
            '{contributions,template,shell,starterPrompts}', '[
                {"id":"stock-search","label":"Find a vehicle","query":"Help me find a vehicle from current stock"},
                {"id":"delivery-policy","label":"Delivery policy","query":"Explain the approved delivery policy"}
            ]'::jsonb) as m
      from source cross join variants v
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select version_id, plugin_id, '1.0.0', 'EARLY_ACCESS', 'PUBLISHED', m::text,
    'mpub-loom', 'system', 'system', current_timestamp,
    '010.44 unbound AI integration. Native TOKEN external vector contract and supported conversation extensions preserved. Only the explicit managed-Inbox variant may receive a separately reviewed AI-only dependency spec. No offer, policy, SQL hosting approval or external business binding is seeded.',
    encode(sha256(convert_to(m::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp from candidate
on conflict (id) do nothing;
