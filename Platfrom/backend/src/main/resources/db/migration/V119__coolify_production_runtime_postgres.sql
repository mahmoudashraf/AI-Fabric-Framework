-- Production runtime deployments need durable chat/session storage. Staging
-- keeps the runtime Docker template default H2 file so preview deploys remain
-- cheap and disposable.
update deployment_target_profiles
set resource_defaults_json = case
        when trim(resource_defaults_json) = '{}' then
            '{"runtimeDatabaseMode":"COOLIFY_POSTGRES","runtimeDatabaseNamePrefix":"ai-fabric-runtime-postgres","runtimeDatabaseName":"runtime_chat","runtimeDatabaseUsername":"runtime_user","runtimeDatabaseImage":"postgres:16-alpine","runtimeDatabasePort":"5432","runtimeDatabasePublic":false}'
        else
            substring(resource_defaults_json, 1, length(resource_defaults_json) - 1)
                || ',"runtimeDatabaseMode":"COOLIFY_POSTGRES","runtimeDatabaseNamePrefix":"ai-fabric-runtime-postgres","runtimeDatabaseName":"runtime_chat","runtimeDatabaseUsername":"runtime_user","runtimeDatabaseImage":"postgres:16-alpine","runtimeDatabasePort":"5432","runtimeDatabasePublic":false}'
    end,
    updated_at = current_timestamp
where id = 'dtp-coolify-production'
  and resource_defaults_json not like '%"runtimeDatabaseMode"%';
