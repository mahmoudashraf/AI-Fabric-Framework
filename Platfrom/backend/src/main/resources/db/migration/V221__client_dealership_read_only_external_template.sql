-- New bootstrap revision only: existing deployments, published history, offers,
-- bindings and provider resources are untouched. This grants no provider access.
with source as (
    select manifest_json::jsonb as m
      from platform_marketplace_plugin_versions
     where plugin_id = 'mkp-template-dealership-external-systems-v1'
       and version = '1.1.0' and status = 'PUBLISHED'
), template as (
    select m, (m #> '{contributions,template}') || jsonb_build_object(
        'requiredPluginRefs', '[
            "mkp-data-dealership-external-stock-v1@1.0.0",
            "mkp-action-dealership-external-discovery-v1@1.1.0",
            "mkp-data-dealership-knowledge-v1@1.0.0"
        ]'::jsonb,
        'requiredPluginInstallDefaults', '{
            "mkp-data-dealership-knowledge-v1@1.0.0": {
                "config": {"publicContentApprovalConfirmed": false}
            }
        }'::jsonb,
        'recommendedPluginIds', '["mkp-integration-loomai-desk"]'::jsonb,
        'providerConfig', (m #> '{contributions,template,providerConfig}') || '{
            "openaiModel": "gpt-5.4-mini",
            "vectorStrategy": "milvus",
            "vectorProvisioningMode": "EXTERNAL_EXISTING",
            "vectorStoragePosture": "SHARED",
            "milvusExternalBindingRequired": true,
            "milvusAuthenticationType": "TOKEN",
            "orchestrationModeOverrides": {
                "executor": {
                    "knowledgeBaseOverviewEnabled": true,
                    "rag": {"fanoutEnabled": true, "maxSpaces": 2},
                    "readActionResolution": {
                        "planningMode": "ITERATIVE",
                        "maxIterations": 2,
                        "maxPlannerContextChars": 12000,
                        "maxActionEvidenceCharsPerAction": 8000,
                        "maxActionEvidenceItems": 10,
                        "ragCooperationMode": "RAG_IF_ACTIONS_INSUFFICIENT"
                    },
                    "semanticRecovery": {"missingVectorSpace": "BOUNDED_ALLOWED_FAN_OUT"}
                }
            }
        }'::jsonb,
        'promptConfig', coalesce(m #> '{contributions,template,promptConfig}', '{}'::jsonb) || '{
            "ragMaxDocumentsUsedForContext": 8,
            "ragMaxContextChars": 8000
        }'::jsonb
    ) as t from source
), candidate as (
    select jsonb_set(
        m || jsonb_build_object('version', '1.2.0',
            'displayName', 'Dealership AI - External Stock And Policies',
            'description', 'Read-only dealership discovery and approved customer documents. Customer setup must explicitly select CUSTOMER_POSTGRES and bind retained external Milvus. Stock remains unbound until reviewed; business enquiry writes are not included. Shared LoomAI Desk may be activated separately on a verified target. No business backend or Inbox is provisioned.'),
        '{contributions,template}',
        jsonb_set(jsonb_set(t,
            '{deploymentBehavior,defaultExecutionExtensions}', '[]'::jsonb),
            '{shell,greeting,message}',
            '"Explore synchronized stock, compare vehicles and ask about approved dealership policies. Use the dealership contact route for enquiries."'::jsonb)
    ) as m from template
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select 'mkv-template-dealership-external-systems-v1-2',
    'mkp-template-dealership-external-systems-v1', '1.2.0', 'EARLY_ACCESS', 'PUBLISHED', m::text,
    'mpub-loom', 'system', 'system', current_timestamp,
    '010.55 local client setup composition: select CUSTOMER_POSTGRES through the existing typed customer bootstrap setting, not an unsupported template field. External vectors/documents, no enquiry ACTION or required receiver, optional shared Desk. Reuses the dealership model and supported READ/context budgets, not Support quality candidates. Editable bootstrap defaults, not upgrade restrictions. Hosted provider, customer setup, persistence, quality and promotion acceptance remain required.',
    encode(sha256(convert_to(m::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp
  from candidate
on conflict (plugin_id, version) do nothing;
