update deployment_target_profiles staging
set credential_ref_id = production.credential_ref_id,
    source_strategy = production.source_strategy,
    network_policy_json = production.network_policy_json,
    provider_config_json = (
        (
            staging.provider_config_json::jsonb
            || jsonb_build_object(
                'baseUrl', production.provider_config_json::jsonb ->> 'baseUrl',
                'projectUuid', production.provider_config_json::jsonb ->> 'projectUuid',
                'serverUuid', production.provider_config_json::jsonb ->> 'serverUuid',
                'destinationUuid', production.provider_config_json::jsonb ->> 'destinationUuid',
                'apiVersionPinned', production.provider_config_json::jsonb ->> 'apiVersionPinned',
                'deploymentPollIntervalSeconds',
                    coalesce(
                        production.provider_config_json::jsonb -> 'deploymentPollIntervalSeconds',
                        staging.provider_config_json::jsonb -> 'deploymentPollIntervalSeconds',
                        to_jsonb(5)
                    ),
                'deploymentTimeoutSeconds',
                    coalesce(
                        production.provider_config_json::jsonb -> 'deploymentTimeoutSeconds',
                        staging.provider_config_json::jsonb -> 'deploymentTimeoutSeconds',
                        to_jsonb(600)
                    ),
                'environmentName', 'staging',
                'defaultPublicDomainSuffix', 'runtime-staging.loomai.pro'
            )
        ) - 'environmentUuid'
    )::text,
    active = true,
    platform_services_allowed = true,
    updated_at = current_timestamp
from deployment_target_profiles production
where staging.id = 'dtp-coolify-prod-staging'
  and production.id = 'dtp-coolify-production'
  and (
      staging.provider_config_json::jsonb ->> 'baseUrl' is distinct from production.provider_config_json::jsonb ->> 'baseUrl'
      or staging.provider_config_json::jsonb ? 'environmentUuid'
      or staging.credential_ref_id is distinct from production.credential_ref_id
      or staging.source_strategy is distinct from production.source_strategy
      or staging.network_policy_json is distinct from production.network_policy_json
  );
