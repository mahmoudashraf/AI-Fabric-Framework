-- Deploy only after the live Desk cutover is qualified. This retires bootstrap
-- metadata, not graph nodes or native resources. Immutable payloads stay intact.
update deployment_managed_dependency_specs spec
set active = false
where spec.active
  and exists (
      select 1
      from jsonb_array_elements(case
          when jsonb_typeof(spec.spec_json::jsonb -> 'services') = 'array'
          then spec.spec_json::jsonb -> 'services' else '[]'::jsonb end) service
      where service ->> 'contract' = 'BASIC_INBOX_KEYS_V1'
         or service ->> 'initializationContract' = 'inbox-callback-canary/v1'
         or exists (
             select 1
             from jsonb_each(case
                 when jsonb_typeof(service -> 'environment') = 'object'
                 then service -> 'environment' else '{}'::jsonb end) environment
             where environment.value ->> 'kind' = 'OPERATORS'
         )
  );

update platform_marketplace_plugin_versions version
set status = 'DEPRECATED'
where version.plugin_id = 'mkp-integration-basic-loomai-inbox'
   or (
       exists (select 1 from platform_marketplace_plugins plugin
               where plugin.id = version.plugin_id and plugin.plugin_type = 'TEMPLATE')
       and exists (
           select 1
           from jsonb_array_elements_text(case
               when jsonb_typeof(version.manifest_json::jsonb #> '{contributions,template,requiredPluginRefs}') = 'array'
               then version.manifest_json::jsonb #> '{contributions,template,requiredPluginRefs}'
               else '[]'::jsonb end) required_ref
           where required_ref like 'mkp-integration-basic-loomai-inbox@%'
       )
   );

update platform_marketplace_plugins
set status = 'DEPRECATED'
where id = 'mkp-integration-basic-loomai-inbox';
