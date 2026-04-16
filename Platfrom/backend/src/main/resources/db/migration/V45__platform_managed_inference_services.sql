create table platform_managed_inference_services (
    id varchar(64) primary key,
    service_ref varchar(128) not null,
    display_name varchar(255) not null,
    service_kind varchar(64) not null,
    deployment_mode varchar(64) not null,
    provider_type varchar(64) not null,
    protocol_type varchar(64) not null,
    model_id varchar(255),
    environment_scope varchar(100),
    tier_scope varchar(100),
    deployment_id varchar(64),
    railway_project_id varchar(128),
    railway_environment_id varchar(128),
    railway_service_id varchar(128),
    desired_replicas integer,
    actual_replicas integer,
    min_replicas integer,
    max_replicas integer,
    autoscaling_mode varchar(64),
    base_url text,
    private_network_url text,
    health_path varchar(255),
    secret_name varchar(255),
    status varchar(64) not null,
    details_json text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uk_platform_managed_inference_services_service_ref
    on platform_managed_inference_services (service_ref);

alter table platform_managed_inference_endpoints
    add column service_id varchar(64);

alter table platform_managed_inference_endpoints
    add column endpoint_purpose varchar(64);

alter table platform_managed_inference_endpoints
    add column protocol_type varchar(64);

insert into platform_managed_inference_services (
    id,
    service_ref,
    display_name,
    service_kind,
    deployment_mode,
    provider_type,
    protocol_type,
    model_id,
    environment_scope,
    tier_scope,
    desired_replicas,
    actual_replicas,
    min_replicas,
    max_replicas,
    autoscaling_mode,
    base_url,
    health_path,
    secret_name,
    status,
    details_json,
    created_at,
    updated_at
)
select
    'pmis-' || replace(profile_ref, '_', '-') as id,
    profile_ref as service_ref,
    display_name,
    case
        when profile_ref = 'onnx-bundled' then 'BUNDLED_ONNX_SERVICE'
        else 'MANAGED_ENDPOINT_PROFILE'
    end as service_kind,
    case
        when profile_ref = 'onnx-bundled' then 'BUNDLED_RUNTIME'
        else 'SHARED_PLATFORM_SERVICE'
    end as deployment_mode,
    provider_type,
    case
        when provider_type = 'onnx' then 'OPENAI_COMPATIBLE'
        else 'OPENAI_COMPATIBLE'
    end as protocol_type,
    case
        when profile_ref = 'onnx-bundled' then 'bge-small-en-v1.5'
        else null
    end as model_id,
    'global',
    'default',
    1,
    1,
    1,
    4,
    'MANUAL',
    base_url,
    '/actuator/health',
    secret_name,
    status,
    '{}',
    created_at,
    updated_at
from platform_managed_inference_endpoints
where not exists (
    select 1
    from platform_managed_inference_services services
    where lower(services.service_ref) = lower(platform_managed_inference_endpoints.profile_ref)
);

update platform_managed_inference_endpoints endpoints
set
    service_id = (
        select services.id
        from platform_managed_inference_services services
        where lower(services.service_ref) = lower(endpoints.profile_ref)
    ),
    endpoint_purpose = case
        when lower(endpoints.profile_ref) like '%orchestration%' then 'ORCHESTRATION'
        when lower(endpoints.profile_ref) = 'onnx-bundled' then 'EMBEDDING'
        else 'GENERATION'
    end,
    protocol_type = 'OPENAI_COMPATIBLE'
where endpoints.service_id is null
  and exists (
      select 1
      from platform_managed_inference_services services
      where lower(services.service_ref) = lower(endpoints.profile_ref)
  );
