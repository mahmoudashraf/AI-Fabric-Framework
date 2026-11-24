package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Software-only adoption of the existing first-party service. Never creates infrastructure or a wallet. */
@Service
public class ManagedAssistantRuntime {
    public record Install(String targetProfileId, String applicationId, String expectedStoreUuid, String expectedRuntimeFingerprint) { }
    private final AuthoringProperties properties;
    private final DeploymentProviderResourceHandleRepository handles;
    private final DeploymentTargetProfileService targets;
    private final DeploymentTargetProfileRepository targetRows;
    private final CoolifyTargetProfileResolver connections;
    private final CoolifyApiClient coolify;
    private final PlatformSecretService secrets;
    private final RuntimePrivateAssertionSigningService signer;
    private final DeploymentRuntimeAuthorityService authority;
    private final RailwayProvisioningPlanService plans;
    private final ReleaseArtifactResolver artifacts;
    private final ObjectProvider<DeploymentService> deployments;
    private final ConfigurationAssistantTemplate template;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository customers;
    private final com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository tenants;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String platformUrl;
    public ManagedAssistantRuntime(AuthoringProperties properties, DeploymentProviderResourceHandleRepository handles,
        DeploymentTargetProfileService targets, DeploymentTargetProfileRepository targetRows, CoolifyTargetProfileResolver connections, CoolifyApiClient coolify,
        PlatformSecretService secrets, RuntimePrivateAssertionSigningService signer, DeploymentRuntimeAuthorityService authority,
        RailwayProvisioningPlanService plans, ReleaseArtifactResolver artifacts, ObjectProvider<DeploymentService> deployments,
        ConfigurationAssistantTemplate template, PlatformAuditService audit, ObjectMapper mapper, PlatformTransactionManager transactions,
        com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository customers,
        com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository tenants,
        @Value("${platform.delivery.public-base-url:}") String platformUrl) {
        this.properties = properties; this.handles = handles; this.targets = targets; this.targetRows = targetRows; this.connections = connections;
        this.coolify = coolify; this.secrets = secrets; this.signer = signer; this.authority = authority; this.plans = plans;
        this.artifacts = artifacts; this.deployments = deployments; this.template = template; this.audit = audit;
        this.mapper = mapper; this.transactions = new TransactionTemplate(transactions); this.platformUrl = platformUrl;
        this.customers = customers; this.tenants = tenants;
    }
    public boolean matches(DeploymentEntity deployment) {
        if (deployment == null || !properties.getDeploymentId().equals(deployment.getId())) return false;
        if (!properties.getCustomerId().equals(deployment.getCustomerId()) || !properties.getTenantId().equals(deployment.getTenantId())) throw changed();
        return true;
    }
    public Map<String, Object> install(Install request) {
        var actor = PlatformSecurityContext.currentPrincipal();
        if (actor == null || actor.role() != PlatformRole.PLATFORM_ADMIN) throw new ResponseStatusException(FORBIDDEN);
        if (request == null || request.applicationId() == null || !request.applicationId().matches("[a-zA-Z0-9-]{8,128}")) throw changed();
        var sourceTarget = targets.requireActiveProfile(request.targetProfileId());
        if (sourceTarget.getProviderType() != DeploymentProviderType.COOLIFY) throw changed();
        var connection = connections.requireConnection(sourceTarget);
        var app = coolify.getApplication(connection, request.applicationId()).orElseThrow(ManagedAssistantRuntime::changed);
        for (var field : Map.of("AI_FABRIC_RUNTIME_DEPLOYMENT_ID", properties.getDeploymentId(),
            "AI_FABRIC_RUNTIME_CUSTOMER_ID", properties.getCustomerId(), "AI_FABRIC_RUNTIME_TENANT_ID", properties.getTenantId(),
            "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY", requiredSecret(properties.getApiKeySecretRef()),
            "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY", requiredSecret(properties.getSigningKeySecretRef())).entrySet())
            if (!field.getValue().equals(coolify.requireRuntimeEnvironmentValue(connection, app.uuid(), field.getKey()))) throw changed();
        String runtimeAddress = installedRuntimeAddress(properties.getRuntimeUrl(), app.fqdn());
        var budget = read("/api/admin/provider-budget", legacyHeaders());
        String originalTarget = coolify.requireRuntimeEnvironmentValue(connection, app.uuid(), "LOOMAI_RUNTIME_TARGET_PROFILE_ID");
        requireBudgetScope(budget, originalTarget);
        if (!sourceTarget.getEnvironmentName().equals(budget.path("environment").asText())) throw changed();
        if (!Objects.equals(request.expectedStoreUuid(), budget.path("storeUuid").asText())
            || !Objects.equals(request.expectedRuntimeFingerprint(), budget.path("runtimeFingerprint").asText())) throw changed();
        var existingServiceDefaults = adoptionDefaults(parse(sourceTarget.getResourceDefaultsJson()),
            coolify.requireRuntimeEnvironmentValue(connection, app.uuid(), "SPRING_DATASOURCE_URL"),
            app.raw().path("ports_exposes").asText());
        var target = targetRows.findById(originalTarget).orElseGet(() -> {
            if (!originalTarget.equals("configuration-authoring-" + sourceTarget.getEnvironmentName())) throw changed();
            var adopted = new DeploymentTargetProfileEntity();
            adopted.setId(originalTarget); adopted.setName("Configuration assistant " + sourceTarget.getEnvironmentName());
            adopted.setProviderType(DeploymentProviderType.COOLIFY); adopted.setEnvironmentName(sourceTarget.getEnvironmentName());
            adopted.setActive(true); adopted.setDefaultForRuntime(false); adopted.setDefaultForRestartableServices(false);
            adopted.setPlatformServicesAllowed(false); adopted.setRegion(sourceTarget.getRegion()); adopted.setSourceStrategy("IMAGE_SOURCE");
            adopted.setCredentialRefId(sourceTarget.getCredentialRefId()); adopted.setProviderConfigJson(sourceTarget.getProviderConfigJson());
            adopted.setNetworkPolicyJson(sourceTarget.getNetworkPolicyJson()); adopted.setResourceDefaultsJson(existingServiceDefaults.toString());
            adopted.setCreatedAt(Instant.now()); adopted.setUpdatedAt(Instant.now()); return targetRows.saveAndFlush(adopted);
        });
        if (!target.getCredentialRefId().equals(sourceTarget.getCredentialRefId())
            || !connections.requireConnection(target).baseUrl().equals(connection.baseUrl())
            || !target.getEnvironmentName().equals(sourceTarget.getEnvironmentName())) throw changed();
        if (!handles.findByProviderResourceUuidOrderByIdAsc(app.uuid()).stream().allMatch(h ->
            properties.getDeploymentId().equals(h.getDeploymentId()) && target.getId().equals(h.getTargetProfileId())
                && PreservedExternalBindingKinds.MANAGED_ASSISTANT.equals(h.getResourceKind()))) throw changed();
        if (!existingServiceDefaults.equals(parse(target.getResourceDefaultsJson()))) {
            if (target.isDefaultForRuntime() || target.isDefaultForRestartableServices() || target.isPlatformServicesAllowed()) throw changed();
            target.setResourceDefaultsJson(existingServiceDefaults.toString()); target.setSourceStrategy("IMAGE_SOURCE");
            target.setUpdatedAt(Instant.now()); targetRows.saveAndFlush(target);
        }
        transactions.executeWithoutResult(tx -> ensureInternalOwner());
        var created = deployments.getObject().createManagedConfigurationAssistant(target.getEnvironmentName(), properties, template);
        return transactions.execute(tx -> {
            var prior = handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(created.id(), target.getId(), PreservedExternalBindingKinds.MANAGED_ASSISTANT);
            if (prior.isPresent()) {
                if (!app.uuid().equals(prior.get().getProviderResourceUuid())) throw changed();
                requireSameBudget(metadata(prior.get()).path("budget"), budget);
                return Map.of("deploymentId", created.id(), "resourceHandleId", prior.get().getId(), "targetProfileId", target.getId(), "reused", true);
            }
            var handle = new DeploymentProviderResourceHandleEntity();
            handle.setId("asst-" + UUID.randomUUID()); handle.setDeploymentId(created.id()); handle.setTargetProfileId(target.getId());
            handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setResourceKind(PreservedExternalBindingKinds.MANAGED_ASSISTANT);
            handle.setProviderResourceUuid(app.uuid()); handle.setFqdn(runtimeAddress); handle.setStatus("BOUND");
            handle.setCreatedAt(Instant.now()); handle.setUpdatedAt(Instant.now());
            var details = mapper.createObjectNode().put("applicationName", app.name()).put("runtimeBaseUrl", runtimeAddress)
                .put("adoptedBy", actor.actorId()).put("adoptedAt", Instant.now().toString()).put("infrastructureOwned", false);
            details.set("budget", budget); handle.setMetadataJson(details.toString()); handles.saveAndFlush(handle);
            audit.record("AUTHORING_EXISTING_SERVICE_BOUND", "DEPLOYMENT", created.id(), Map.of("targetProfileId", target.getId(), "applicationId", app.uuid(), "budgetStoreUuid", budget.path("storeUuid").asText()));
            return Map.of("deploymentId", created.id(), "resourceHandleId", handle.getId(), "targetProfileId", target.getId(), "reused", false);
        });
    }
    public void requireInstalled(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        if (!matches(deployment) || !deployment.getId().equals(release.getDeploymentId())) throw changed();
        binding(release);
    }
    public ProvisioningResult provision(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        requireInstalled(deployment, release);
        var handle = binding(release); var metadata = metadata(handle);
        var target = targets.requireActiveProfile(release.getTargetProfileId());
        var connection = connections.requireConnection(target);
        var app = coolify.getApplication(connection, handle.getProviderResourceUuid()).orElseThrow(ManagedAssistantRuntime::changed);
        if (!metadata.path("applicationName").asText().equals(app.name())
            || !handle.getFqdn().equals(installedRuntimeAddress(properties.getRuntimeUrl(), app.fqdn()))) throw changed();
        var frozen = authority.requireReleaseUnchangedForProvisioning(deployment, version, release);
        // The original provider binding and database settings are preserved, not regenerated from a grant.
        var runtimeEnvironment = new LinkedHashMap<String, String>();
        var plan = plans.buildPlan(deployment, version, null, target.getId(), release.getRuntimeAuthorityFingerprint());
        plan.services().runtime().env().forEach(env -> runtimeEnvironment.put(env.key(), env.value()));
        authority.releaseEnvironmentPlanForProvisioning(deployment, version, release).runtime()
            .forEach(runtimeEnvironment::put);
        runtimeEnvironment.put("SPRING_PROFILES_ACTIVE", "configured-authoring");
        runtimeEnvironment.put("LOOMAI_RUNTIME_RELEASE_ID", release.getId());
        runtimeEnvironment.put("LOOMAI_CONFIGURATION_ASSISTANT_PLATFORM_BASE_URL", requiredHttps(platformUrl));
        runtimeEnvironment.put("LOOMAI_CONFIGURATION_ASSISTANT_TOOL_KEY", requiredSecret(properties.getApiKeySecretRef()));
        runtimeEnvironment.put("LOOMAI_AUTHORING_ENABLED", "false");
        runtimeEnvironment.put("LOOMAI_CONFIGURATION_ASSISTANT_ENABLED", Boolean.toString(
            com.ai.fabric.platform.backend.deployment.specialist.WorkflowAssistanceCompiler.enabled(parse(version.getBehaviorConfigJson()))));
        runtimeEnvironment.put("LOOMAI_PROVIDER_BUDGET_ENABLED", "true");
        runtimeEnvironment.put("LOOMAI_CONVERSATION_CONTROL_ENABLED", "true");
        runtimeEnvironment.put("AI_CHAT_CONTROL_ENABLED", "true");
        for (String key : List.of("LOOMAI_DOCUMENTS_ENABLED", "LOOMAI_SMART_BRAIN_ENABLED", "LOOMAI_CONVERSATION_HANDOVER_ENABLED",
            "AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS", "AI_INDEXING_ENABLED", "AI_DATA_SYNC_ENABLED")) runtimeEnvironment.put(key, "false");
        // Leave the original paid-provider key, budget identity and persistent datasource untouched.
        for (String key : List.of("OPENAI_API_KEY", "LOOMAI_PROVIDER_BUDGET_ENVIRONMENT", "LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY",
            "LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE", "LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE", "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "SPRING_DATASOURCE_DRIVER_CLASS_NAME")) runtimeEnvironment.remove(key);
        preserveProviderCredential(runtimeEnvironment,
            coolify.requireRuntimeEnvironmentValue(connection, app.uuid(), "OPENAI_API_KEY"));
        var artifact = artifacts.resolve(release);
        if (!version.getAiFabricFrameworkVersion().equals(parse(artifact.getCapabilityManifestJson()).path("aiFabricVersion").asText())) throw changed();
        coolify.updateEnvironmentVariables(connection, app.uuid(), runtimeEnvironment.entrySet().stream()
            .map(e -> new CoolifyEnvVar(e.getKey(), resolve(e.getValue()), false, true, e.getValue().contains("\n"), true)).toList());
        coolify.updateApplicationImage(connection, app.uuid(), artifact.getImageRepository(), artifact.getImageDigest());
        var started = coolify.start(connection, app.uuid(), true, true);
        if (started.deploymentUuid() == null || started.deploymentUuid().isBlank()) throw changed();
        Instant deadline = Instant.now().plusSeconds(180);
        RuntimeException last = null;
        do {
            try {
                var observed = coolify.getDeployment(connection, started.deploymentUuid()).orElseThrow(ManagedAssistantRuntime::changed);
                if (!app.uuid().equals(observed.applicationUuid()) || !"finished".equals(observed.status())) throw changed();
                if (!"UP".equals(read("/actuator/health/liveness", Map.of()).path("status").asText())) throw changed();
                last = null; break;
            }
            catch (RuntimeException pending) { last = pending; }
            try { Thread.sleep(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw changed(); }
        } while (Instant.now().isBefore(deadline));
        if (last != null) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_RUNTIME_ACTIVATION_UNCONFIRMED", last);
        var details = mapper.createObjectNode().put("providerResourceHandleId", handle.getId()).put("runtimeBaseUrl", handle.getFqdn())
            .put("applicationId", app.uuid()).put("runtimeAuthorityFingerprint", frozen.fingerprint())
            .put("budgetStoreUuid", metadata.path("budget").path("storeUuid").asText()).put("softwareOnly", true);
        handle.setReleaseId(release.getId()); handle.setStatus("ACTIVE"); handle.setUpdatedAt(Instant.now()); handles.save(handle);
        return new ProvisioningResult("ACTIVE", "coolify", handle.getFqdn(), null, details.toString());
    }
    static void preserveProviderCredential(Map<String, String> environment, String originalKey) {
        if (originalKey == null || originalKey.isBlank()) throw changed();
        // Purpose-specific clients must use the same paid account as the original ledger.
        for (String key : List.of("AI_PROVIDERS_OPENAI_API_KEY", "AI_PROVIDERS_ORCHESTRATION_API_KEY", "AI_PROVIDERS_GENERATION_API_KEY",
            "AI_PROVIDERS_OPENAI_EMBEDDING_API_KEY", "AI_PROVIDERS_EMBEDDING_API_KEY"))
            environment.put(key, originalKey);
    }
    static ObjectNode adoptionDefaults(JsonNode source, String datasource, String ports) {
        if (!source.isObject() || datasource == null || !datasource.startsWith("jdbc:postgresql://")
            || ports == null || !ports.matches("[1-9][0-9]{0,4}") || Integer.parseInt(ports) > 65535) throw changed();
        var defaults = (ObjectNode) source.deepCopy();
        defaults.remove(List.of("buildPack", "baseDirectory", "dockerfilePath", "gitRepository", "gitBranch"));
        defaults.put("sourceStrategy", "IMAGE_SOURCE").put("runtimeDatabaseMode", "COOLIFY_POSTGRES")
            .put("portsExposes", ports).put("healthCheckPort", ports);
        return defaults;
    }
    public void verifyRuntime(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        requireInstalled(deployment, release);
        var headers = authority.issueReleaseVerificationHeaders(deployment, release, false);
        var overview = read("/api/admin/overview", headers);
        var expected = parse(version.getBehaviorConfigJson()).path("workflowAssistance");
        boolean enabled = expected.path("enabled").asBoolean();
        if (!overview.path("workflowAssistance").path("ready").asBoolean()
            || overview.path("workflowAssistance").path("enabled").asBoolean() != enabled
            || enabled && !expected.path("contentHash").asText().equals(overview.path("workflowAssistance").path("contentHash").asText())
            || !version.getId().equals(overview.path("deploymentVersionId").asText())
            || !version.getAiFabricFrameworkVersion().equals(overview.path("aiFabricFrameworkVersion").asText())) throw changed();
        requireSameBudget(metadata(binding(release)).path("budget"), read("/api/admin/provider-budget", headers));
    }
    public DeploymentVerificationRunEntity verify(DeploymentEntity deployment, DeploymentVersionEntity version,
        DeploymentReleaseEntity release, String type) {
        var checks = mapper.createArrayNode(); String status = "PASSED", phase = "installed_service_binding";
        try {
            requireInstalled(deployment, release);
            phase = "frozen_release_authority";
            authority.requireReleaseUnchangedForProvisioning(deployment, version, release);
            phase = "runtime_release_and_budget";
            if (!"PRE_APPLY".equals(type)) verifyRuntime(deployment, version, release);
            checks.addObject().put("key", "configured_authoring_native_release").put("status", "PASSED")
                .put("message", "Exact installed service, frozen authority, published workflow and original budget verified.");
        } catch (RuntimeException failed) {
            status = "FAILED"; checks.addObject().put("key", "configured_authoring_native_release").put("status", "FAILED")
                .put("phase", phase).put("failureType", failed.getClass().getSimpleName())
                .put("message", "The installed assistant does not match its published release or preserved budget.");
        }
        var run = new DeploymentVerificationRunEntity(); run.setId("vrf-" + UUID.randomUUID()); run.setDeploymentId(deployment.getId());
        run.setReleaseId(release.getId()); run.setDeploymentVersionId(version.getId()); run.setVerificationType(type); run.setStatus(status);
        run.setSummaryMessage("Configured assistant " + status.toLowerCase(Locale.ROOT)); run.setChecksJson(checks.toString());
        run.setCreatedAt(Instant.now()); run.setCompletedAt(Instant.now()); return run;
    }
    private DeploymentProviderResourceHandleEntity binding(DeploymentReleaseEntity release) {
        return handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            properties.getDeploymentId(), release.getTargetProfileId(), PreservedExternalBindingKinds.MANAGED_ASSISTANT)
            .filter(h -> !"DELETED".equals(h.getStatus()) && properties.getRuntimeUrl().equals(h.getFqdn())).orElseThrow(ManagedAssistantRuntime::changed);
    }
    private void ensureInternalOwner() {
        var customer = customers.findById(properties.getCustomerId()).orElseGet(() -> {
            var created = new com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity();
            created.setId(properties.getCustomerId()); created.setName("LoomAI Configuration Assistant");
            created.setSlug(properties.getCustomerId()); created.setStatus("ACTIVE"); created.setPlatformManaged(true);
            created.setCreatedAt(Instant.now()); created.setUpdatedAt(Instant.now()); return customers.save(created);
        });
        if (!customer.isPlatformManaged() || !"ACTIVE".equals(customer.getStatus())) throw changed();
        var tenant = tenants.findById(properties.getTenantId()).orElseGet(() -> {
            var created = new com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity();
            created.setId(properties.getTenantId()); created.setCustomerId(customer.getId()); created.setName("LoomAI authoring");
            created.setSlug(properties.getTenantId()); created.setStatus("ACTIVE"); created.setPlatformManaged(true);
            created.setCreatedAt(Instant.now()); created.setUpdatedAt(Instant.now()); return tenants.save(created);
        });
        if (!tenant.isPlatformManaged() || !"ACTIVE".equals(tenant.getStatus()) || !customer.getId().equals(tenant.getCustomerId())) throw changed();
    }
    private Map<String, String> legacyHeaders() {
        var claims = new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims("assistant-installation", "SYSTEM_PROCESS",
            "PRIVATE_RUNTIME_BACKEND_MEDIATED", "TRUSTED_BACKEND", "assistant-installation", properties.getDeploymentId(),
            properties.getCustomerId(), properties.getTenantId(), properties.getIssuer(), Instant.now().plusSeconds(60),
            List.of(properties.getAudience()), List.of("runtime:admin:overview"));
        return Map.of("X-AIFABRIC-RUNTIME-API-KEY", requiredSecret(properties.getApiKeySecretRef()),
            RuntimePrivateAssertionSigningService.AUTHORIZATION_HEADER, "Bearer " + signer.issueAssertionWithSigningKey(claims, requiredSecret(properties.getSigningKeySecretRef())));
    }
    JsonNode read(String path, Map<String, String> headers) {
        try {
            var request = HttpRequest.newBuilder(runtimeBaseUri(properties.getRuntimeUrl()).resolve(path)).timeout(Duration.ofSeconds(10)).GET();
            headers.forEach(request::header);
            var response = AuthoringHttp.send(http, request.build(), Instant.now().plusSeconds(10), 1_048_576);
            {
                var bytes = response.body();
                if (response.statusCode() != 200) throw changed();
                return mapper.readTree(bytes);
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw changed(); }
        catch (Exception unavailable) { throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_RUNTIME_READBACK_UNAVAILABLE", unavailable); }
    }
    private void requireBudgetScope(JsonNode value, String target) {
        if (!"ACTIVE".equals(value.path("state").asText()) || !properties.getCustomerId().equals(value.path("customerId").asText())
            || !properties.getDeploymentId().equals(value.path("deploymentId").asText())
            || !target.equals(value.path("targetProfileId").asText()) || !value.path("limits").isObject()
            || value.path("storeUuid").asText().isBlank() || value.path("stopped").asBoolean(true)) throw changed();
    }
    private void requireSameBudget(JsonNode expected, JsonNode actual) {
        requireBudgetScope(actual, expected.path("targetProfileId").asText());
        for (String field : List.of("storeUuid", "approvalId", "runtimeFingerprint", "customerId", "deploymentId", "targetProfileId",
            "environment", "rateCardReference", "limits", "expiresAt", "effectiveExpiresAt"))
            if (!expected.path(field).equals(actual.path(field))) throw changed();
        var originalLimits = expected.path("effectiveLimits");
        var currentLimits = actual.path("effectiveLimits");
        if (!originalLimits.isObject() || !currentLimits.isObject()) throw changed();
        // Effective limits come from authenticated native ledger readback, not
        // environment overrides. Top-ups preserve the original grant and usage.
        var topUpFields = List.of("chatAttempts", "totalTokens", "costNanoUsd");
        var originalOtherLimits = (ObjectNode) originalLimits.deepCopy(); originalOtherLimits.remove(topUpFields);
        var currentOtherLimits = (ObjectNode) currentLimits.deepCopy(); currentOtherLimits.remove(topUpFields);
        if (!originalOtherLimits.equals(currentOtherLimits)) throw changed();
        for (String field : topUpFields)
            if (!originalLimits.path(field).isIntegralNumber() || !currentLimits.path(field).isIntegralNumber()
                || !originalLimits.path(field).canConvertToLong() || !currentLimits.path(field).canConvertToLong()
                || currentLimits.path(field).asLong() < originalLimits.path(field).asLong()) throw changed();
        for (String field : List.of("chatAttempts", "providerAttempts")) if (actual.path(field).asLong(-1) < expected.path(field).asLong()) throw changed();
        for (String field : List.of("inputTokens", "outputTokens", "costNanoUsd"))
            if (expected.path("accountedUsage").has(field)
                && actual.path("accountedUsage").path(field).asLong(-1) < expected.path("accountedUsage").path(field).asLong()) throw changed();
    }
    private ObjectNode metadata(DeploymentProviderResourceHandleEntity handle) { return (ObjectNode) parse(handle.getMetadataJson()); }
    private JsonNode parse(String json) { try { return mapper.readTree(json); } catch (Exception invalid) { throw changed(); } }
    private String resolve(String value) {
        return value != null && value.startsWith("${secret:") && value.endsWith("}") ? requiredSecret(value.substring(9, value.length() - 1)) : value;
    }
    private String requiredSecret(String reference) { var value = secrets.resolveSecret(reference); if (value == null || value.isBlank()) throw changed(); return value; }
    static String installedRuntimeAddress(String configured, String providerAddress) {
        URI uri = runtimeBaseUri(configured);
        if (providerAddress != null && !providerAddress.isBlank()) {
            if (!configured.equals(providerAddress)) throw changed();
        } else if (!"http".equals(uri.getScheme())) throw changed();
        return uri.toString();
    }
    private static URI runtimeBaseUri(String configured) {
        URI uri = URI.create(configured);
        boolean privateAlias = "http".equals(uri.getScheme()) && uri.getHost() != null
            && uri.getHost().matches("[a-zA-Z][a-zA-Z0-9-]{0,62}") && !"localhost".equalsIgnoreCase(uri.getHost())
            && uri.getPort() > 0 && uri.getPort() <= 65535;
        // This address comes only from operator configuration and is pinned by
        // the installed service's signed identity and original wallet, never AI input.
        if (!("https".equals(uri.getScheme()) || privateAlias) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw changed();
        return uri;
    }
    private static String requiredHttps(String value) {
        var uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw changed();
        return value;
    }
    private static ResponseStatusException changed() { return new ResponseStatusException(CONFLICT, "AUTHORING_EXISTING_SERVICE_BINDING_CHANGED"); }
}
