package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessCheck;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AIWorkspaceReadinessService {

    private static final String PUBLIC_SIGNING_KEY = "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY";
    private static final String PRIVATE_SIGNING_KEY = "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY";

    private final PlatformConsumerRepository consumerRepository;
    private final AIWorkspaceAssignmentService assignmentService;
    private final AIWorkspaceAssetCatalogService assetCatalogService;
    private final AIWorkspaceExperiencePackRegistry experiencePackRegistry;
    private final AIWorkspaceConnectionProfileRegistry connectionProfileRegistry;
    private final AIWorkspaceAuthenticationProfileRegistry authenticationProfileRegistry;
    private final AIWorkspaceDomainService domainService;
    private final PlatformSecretService secretService;
    private final PlatformAIWorkspaceProperties properties;
    private final ObjectMapper objectMapper;

    public AIWorkspaceReadinessService(PlatformConsumerRepository consumerRepository,
                                       AIWorkspaceAssignmentService assignmentService,
                                       AIWorkspaceAssetCatalogService assetCatalogService,
                                       AIWorkspaceExperiencePackRegistry experiencePackRegistry,
                                       AIWorkspaceConnectionProfileRegistry connectionProfileRegistry,
                                       AIWorkspaceAuthenticationProfileRegistry authenticationProfileRegistry,
                                       AIWorkspaceDomainService domainService,
                                       PlatformSecretService secretService,
                                       PlatformAIWorkspaceProperties properties,
                                       ObjectMapper objectMapper) {
        this.consumerRepository = consumerRepository;
        this.assignmentService = assignmentService;
        this.assetCatalogService = assetCatalogService;
        this.experiencePackRegistry = experiencePackRegistry;
        this.connectionProfileRegistry = connectionProfileRegistry;
        this.authenticationProfileRegistry = authenticationProfileRegistry;
        this.domainService = domainService;
        this.secretService = secretService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AIWorkspaceReadinessSummary evaluate(AIWorkspaceInstallationEntity installation) {
        return evaluate(installation, true);
    }

    public AIWorkspaceReadinessSummary evaluateLocal(AIWorkspaceInstallationEntity installation) {
        return evaluate(installation, false);
    }

    private AIWorkspaceReadinessSummary evaluate(AIWorkspaceInstallationEntity installation, boolean probeDelivery) {
        PlatformConsumerEntity consumer = consumerRepository.findById(installation.getConsumerEntityId()).orElse(null);
        return evaluateAgainst(installation, consumer,
            consumer == null ? null : consumer.getBoundDeploymentId(),
            consumer == null ? null : consumer.getBoundReleaseId(), probeDelivery);
    }

    public AIWorkspaceReadinessSummary evaluateAgainst(AIWorkspaceInstallationEntity installation,
                                                        PlatformConsumerEntity consumer,
                                                        String deploymentId,
                                                        String releaseId) {
        return evaluateAgainst(installation, consumer, deploymentId, releaseId, true);
    }

    private AIWorkspaceReadinessSummary evaluateAgainst(AIWorkspaceInstallationEntity installation,
                                                        PlatformConsumerEntity consumer,
                                                        String deploymentId, String releaseId,
                                                        boolean probeDelivery) {
        List<AIWorkspaceReadinessCheck> checks = new ArrayList<>();
        AIWorkspaceAssignmentService.Assignment assignment = null;
        if (consumer == null) {
            blocked(checks, "CONSUMER", "Installation consumer is missing.");
        } else {
            try {
                assignment = assignmentService.resolveTarget(consumer, deploymentId, releaseId);
                passed(checks, "ASSIGNMENT", "Consumer resolves to a verified conversational release.");
            } catch (RuntimeException ex) {
                blocked(checks, "ASSIGNMENT", ex.getMessage());
            }
        }

        AIWorkspaceAssetCatalogService.CatalogHealth assetHealth = assetCatalogService.health();
        if (assetHealth.ready()) passed(checks, "ASSETS", "Platform workspace assets and digests are verified.");
        else blocked(checks, "ASSETS", assetHealth.status());

        try {
            var pack = experiencePackRegistry.resolve(
                installation.getExperiencePackCode(), installation.getExperiencePackVersion());
            if (pack.asset() == null) blocked(checks, "EXPERIENCE_PACK", "Experience-pack asset is not packaged.");
            else passed(checks, "EXPERIENCE_PACK", "Experience pack is reviewed and packaged.");
        } catch (RuntimeException ex) {
            blocked(checks, "EXPERIENCE_PACK", ex.getMessage());
        }

        JsonNode connectionConfiguration = readJson(installation.getConnectionConfigurationJson());
        try {
            connectionProfileRegistry.resolve(
                installation.getConnectionProfileCode(),
                installation.getConnectionProfileVersion(),
                installation.getConnectionMode(),
                connectionConfiguration
            );
            passed(checks, "CONNECTION_PROFILE", "Connection profile is reviewed, compatible, and enabled.");
        } catch (RuntimeException ex) {
            blocked(checks, "CONNECTION_PROFILE", ex.getMessage());
        }

        List<String> origins = readOrigins(installation.getAllowedOriginsJson());
        if (origins.isEmpty()) blocked(checks, "ORIGINS", "At least one exact origin is required.");
        else passed(checks, "ORIGINS", origins.size() + " exact website origin(s) configured.");

        com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy interactionPolicy =
            installation.getInteractionPolicy() == null
                ? com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy.PUBLIC
                : installation.getInteractionPolicy();
        AIWorkspaceAuthenticationProfileRegistry.AuthenticationProfile authenticationProfile = null;
        try {
            authenticationProfile = authenticationProfileRegistry.resolve(
                installation.getAuthProfileCode(), installation.getAuthProfileVersion(), interactionPolicy,
                readJson(installation.getAuthConfigurationJson()));
            passed(checks, "AUTHENTICATION_PROFILE", "Authentication policy and reviewed provider profile are compatible.");
        } catch (RuntimeException ex) {
            blocked(checks, "AUTHENTICATION_PROFILE", ex.getMessage());
        }

        var deliveryMode = installation.getDeliveryMode() == null
            ? com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode.EMBEDDED
            : installation.getDeliveryMode();
        if (deliveryMode == com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode.STANDALONE) {
            var domains = domainService.summaries(installation.getId());
            check(domains.stream().anyMatch(domain -> "VERIFIED".equals(domain.status())), checks,
                "APP_DOMAIN", "At least one exact standalone app domain is verified.",
                "No standalone app domain is verified.");
            check(probeDelivery && domainService.deliveryAvailable(installation.getId()), checks,
                "APP_DELIVERY", "A verified standalone frontend route is currently available.",
                probeDelivery ? "The standalone frontend route is currently unavailable."
                    : "Standalone delivery is not probed by configuration commands; explicit readiness is required.");
        }

        if (assignment != null) {
            validateEndpoint(assignment.runtimeBaseUrl(), checks);
            validateMode(installation.getConnectionMode(), assignment.securityConfig(), origins, checks);
            if (interactionPolicy.requiresAuthenticationProfile()) {
                check(ManagedDeploymentProfileCatalog.externalIdentityBrokerEnabled(assignment.securityConfig()), checks,
                    "EXTERNAL_IDENTITY_BROKER", "Assigned runtime enables the deployment-local external identity broker.",
                    "Assigned runtime does not enable the deployment-local external identity broker.");
                if (authenticationProfile != null) {
                    validateExternalIdentityBinding(authenticationProfile, assignment.securityConfig(), checks);
                }
                validateExternalIdentityScopes(assignment, checks);
            }
        }

        boolean ready = checks.stream().noneMatch(check -> "BLOCKED".equals(check.status()));
        return new AIWorkspaceReadinessSummary(
            ready,
            installation.getInstallationId(),
            installation.getConnectionMode().manifestValue(),
            consumer == null ? null : consumer.getConsumerId(),
            assignment == null ? deploymentId : assignment.deployment().getId(),
            assignment == null ? releaseId : assignment.release().getId(),
            assignment == null ? null : assignment.runtimeBaseUrl(),
            assignment == null ? null : assignment.assignmentRevision(),
            List.copyOf(checks)
        );
    }

    private void validateExternalIdentityScopes(AIWorkspaceAssignmentService.Assignment assignment,
                                                List<AIWorkspaceReadinessCheck> checks) {
        java.util.Set<String> required = new java.util.LinkedHashSet<>(List.of(
            "chat:query", "chat:suggestions", "chat:conversations"));
        JsonNode behavior = readJson(assignment.version().getBehaviorConfigJson());
        boolean selected = false;
        for (JsonNode extension : behavior.path("executionExtensions")) {
            if ("CONVERSATION_HANDOVER".equals(extension.asText())) selected = true;
        }
        if (selected && behavior.path("conversationHandover").path("enabled").asBoolean(false)) {
            required.addAll(List.of("chat:messages", "chat:handover:request", "chat:handover:control", "chat:handover:read"));
        }
        java.util.Set<String> granted = java.util.Arrays.stream(
                ManagedDeploymentProfileCatalog.externalIdentityGrantedScopes(assignment.securityConfig()).split(","))
            .map(String::trim).collect(java.util.stream.Collectors.toSet());
        check(granted.containsAll(required), checks, "EXTERNAL_IDENTITY_SCOPES",
            "External identity scopes cover the enabled workspace conversation routes.",
            "External identity scopes must include: " + String.join(",", required));
    }

    private void validateExternalIdentityBinding(
        AIWorkspaceAuthenticationProfileRegistry.AuthenticationProfile authenticationProfile,
        JsonNode securityConfig,
        List<AIWorkspaceReadinessCheck> checks
    ) {
        if (!AIWorkspaceAuthenticationProfileRegistry.SUPABASE.equals(authenticationProfile.code())) {
            blocked(checks, "EXTERNAL_IDENTITY_BINDING", "The assigned runtime does not support this authentication provider.");
            return;
        }
        String projectUrl = authenticationProfile.publicConfiguration().path("projectUrl").asText("");
        String expectedIssuer = projectUrl + "/auth/v1";
        String expectedJwkSetUri = expectedIssuer + "/.well-known/jwks.json";
        boolean matches = "SUPABASE".equalsIgnoreCase(
            ManagedDeploymentProfileCatalog.externalIdentityProvider(securityConfig))
            && expectedIssuer.equals(ManagedDeploymentProfileCatalog.externalIdentityIssuer(securityConfig))
            && expectedJwkSetUri.equals(ManagedDeploymentProfileCatalog.externalIdentityJwkSetUri(securityConfig))
            && "authenticated".equals(ManagedDeploymentProfileCatalog.externalIdentityAudience(securityConfig));
        check(matches, checks,
            "EXTERNAL_IDENTITY_BINDING",
            "Assigned runtime identity validation matches the installation authentication profile.",
            "Assigned runtime identity issuer, JWK set, audience, or provider does not match the installation authentication profile.");
    }

    private void validateMode(AIWorkspaceConnectionMode mode,
                              JsonNode securityConfig,
                              List<String> origins,
                              List<AIWorkspaceReadinessCheck> checks) {
        if (mode == AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS) {
            check(ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig), checks,
                "ANONYMOUS_BOOTSTRAP", "Anonymous runtime bootstrap is enabled.",
                "Assigned release does not enable anonymous runtime bootstrap.");
            validatePublicToken(securityConfig, checks);
            validateRuntimeOrigins(securityConfig, origins, checks);
        } else if (mode == AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED) {
            validatePublicToken(securityConfig, checks);
            validateRuntimeOrigins(securityConfig, origins, checks);
        } else {
            boolean issuerReady = !ManagedDeploymentProfileCatalog.privateRuntimeAcceptedIssuers(securityConfig).isBlank();
            boolean audienceReady = !ManagedDeploymentProfileCatalog.privateRuntimeAcceptedAudiences(securityConfig).isBlank();
            check(secretService.isSecretPresent(PRIVATE_SIGNING_KEY), checks,
                "PRIVATE_ASSERTION_KEY", "Private assertion signing material is configured.",
                "Private assertion signing material is unavailable.");
            check(issuerReady && audienceReady, checks,
                "PRIVATE_ASSERTION_POLICY", "Private runtime issuer and audience policies are explicit.",
                "Private runtime issuer and audience policies are incomplete.");
        }
    }

    private void validatePublicToken(JsonNode securityConfig, List<AIWorkspaceReadinessCheck> checks) {
        boolean policy = !ManagedDeploymentProfileCatalog.publicRuntimeAcceptedIssuers(securityConfig).isBlank()
            && !ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig).isBlank()
            && !ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig).isBlank();
        check(secretService.isSecretPresent(PUBLIC_SIGNING_KEY), checks,
            "PUBLIC_TOKEN_KEY", "Signed public runtime token material is configured.",
            "Signed public runtime token material is unavailable.");
        check(policy, checks,
            "PUBLIC_TOKEN_POLICY", "Public runtime issuer and audience policies are explicit.",
            "Public runtime issuer or audience policy is incomplete.");
    }

    private void validateRuntimeOrigins(JsonNode securityConfig,
                                        List<String> origins,
                                        List<AIWorkspaceReadinessCheck> checks) {
        List<String> runtimeOrigins = csv(securityConfig.path("corsAllowedOrigins").asText(""));
        List<String> missing = origins.stream().filter(origin -> !runtimeOrigins.contains(origin)).toList();
        check(missing.isEmpty(), checks,
            "RUNTIME_CORS", "Every installation origin is present in the runtime CORS allowlist.",
            "Runtime CORS is missing installation origin(s): " + String.join(", ", missing));
    }

    private void validateEndpoint(String value, List<AIWorkspaceReadinessCheck> checks) {
        try {
            URI uri = URI.create(value);
            boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            boolean valid = "https".equalsIgnoreCase(uri.getScheme())
                || (properties.allowLoopbackHttp() && loopback && "http".equalsIgnoreCase(uri.getScheme()));
            check(valid, checks, "RUNTIME_URL", "Assigned runtime uses an approved HTTPS endpoint.",
                "Assigned runtime endpoint must use HTTPS.");
        } catch (RuntimeException ex) {
            blocked(checks, "RUNTIME_URL", "Assigned runtime endpoint is invalid.");
        }
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> readOrigins(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> csv(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private void check(boolean condition,
                       List<AIWorkspaceReadinessCheck> checks,
                       String code,
                       String pass,
                       String fail) {
        if (condition) passed(checks, code, pass); else blocked(checks, code, fail);
    }

    private void passed(List<AIWorkspaceReadinessCheck> checks, String code, String message) {
        checks.add(new AIWorkspaceReadinessCheck(code, "PASSED", message));
    }

    private void blocked(List<AIWorkspaceReadinessCheck> checks, String code, String message) {
        checks.add(new AIWorkspaceReadinessCheck(code, "BLOCKED", message == null ? "Readiness check failed." : message));
    }
}
