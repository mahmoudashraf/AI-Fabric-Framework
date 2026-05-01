-- Public-repository Coolify path for Railway-compatible tenant runtime provisioning.
-- IMAGE_SOURCE remains supported for future GHCR/private-registry hardening.
update deployment_target_profiles
set source_strategy = 'GIT_SOURCE',
    resource_defaults_json = '{"serverType":"cpx32","host":"coolify-staging-01","publicIpv4":"46.224.145.148","publicIpv6":"2a01:4f8:c2c:83e2::1","sourceStrategy":"GIT_SOURCE","buildPack":"dockerfile","baseDirectory":"/","dockerfilePath":"ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile","portsExposes":"8080","healthCheckPath":"/actuator/health","healthCheckPort":"8080"}',
    updated_at = current_timestamp
where id = 'dtp-coolify-staging';

update deployment_target_profiles
set source_strategy = 'GIT_SOURCE',
    resource_defaults_json = '{"serverType":"ccx23","host":"coolify-prod-01","publicIpv4":"46.225.162.106","publicIpv6":"2a01:4f8:1c18:c04::1","sourceStrategy":"GIT_SOURCE","buildPack":"dockerfile","baseDirectory":"/","dockerfilePath":"ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile","portsExposes":"8080","healthCheckPath":"/actuator/health","healthCheckPort":"8080"}',
    updated_at = current_timestamp
where id = 'dtp-coolify-production';
