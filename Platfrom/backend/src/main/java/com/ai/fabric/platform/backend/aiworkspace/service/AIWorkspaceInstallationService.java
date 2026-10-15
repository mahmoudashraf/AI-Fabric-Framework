package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Setting;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AIWorkspaceInstallationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AIWorkspaceInstallationRepository repository;
    private final PlatformCustomerRepository customerRepository;
    private final PlatformConsumerRepository consumerRepository;
    private final PlatformCustomerAccessService customerAccessService;
    private final AIWorkspaceExperiencePackRegistry experiencePackRegistry;
    private final AIWorkspaceConnectionProfileRegistry connectionProfileRegistry;
    private final AIWorkspaceAuthenticationProfileRegistry authenticationProfileRegistry;
    private final AIWorkspaceConfigurationValidator configurationValidator;
    private final AIWorkspaceDomainService domainService;
    private final AIWorkspaceReadinessService readinessService;
    private final AIWorkspaceAssetCatalogService assetCatalogService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final CustomerSelfServiceOperationBoundary offerBoundary;
    private final AIWorkspaceDeploymentSecurityProjectionService deploymentSecurityProjection;

    public AIWorkspaceInstallationService(AIWorkspaceInstallationRepository repository,
                                          PlatformCustomerRepository customerRepository,
                                          PlatformConsumerRepository consumerRepository,
                                          PlatformCustomerAccessService customerAccessService,
                                          AIWorkspaceExperiencePackRegistry experiencePackRegistry,
                                          AIWorkspaceConnectionProfileRegistry connectionProfileRegistry,
                                          AIWorkspaceAuthenticationProfileRegistry authenticationProfileRegistry,
                                          AIWorkspaceConfigurationValidator configurationValidator,
                                          AIWorkspaceDomainService domainService,
                                          AIWorkspaceReadinessService readinessService,
                                          AIWorkspaceAssetCatalogService assetCatalogService,
                                          PlatformAuditService auditService,
                                          ObjectMapper objectMapper,
                                          CustomerSelfServiceOperationBoundary offerBoundary,
                                          AIWorkspaceDeploymentSecurityProjectionService deploymentSecurityProjection) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.consumerRepository = consumerRepository;
        this.customerAccessService = customerAccessService;
        this.experiencePackRegistry = experiencePackRegistry;
        this.connectionProfileRegistry = connectionProfileRegistry;
        this.authenticationProfileRegistry = authenticationProfileRegistry;
        this.configurationValidator = configurationValidator;
        this.domainService = domainService;
        this.readinessService = readinessService;
        this.assetCatalogService = assetCatalogService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.offerBoundary = offerBoundary;
        this.deploymentSecurityProjection = deploymentSecurityProjection;
    }

    @Transactional(readOnly = true)
    public AIWorkspaceCatalogSummary catalog() {
        var health = assetCatalogService.health();
        return new AIWorkspaceCatalogSummary(
            health.ready(), health.status(), experiencePackRegistry.summaries(), connectionProfileRegistry.summaries(),
            authenticationProfileRegistry.summaries());
    }

    @Transactional(readOnly = true)
    public List<AIWorkspaceInstallationSummary> list(String customerId) {
        requireCustomerAccess(customerId);
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public AIWorkspaceInstallationSummary get(String customerId, String installationId) {
        requireCustomerAccess(customerId);
        return summary(requireInstallation(customerId, installationId));
    }

    @Transactional
    public AIWorkspaceInstallationSummary create(String customerId,
                                                  CreateAIWorkspaceInstallationRequest request) {
        return create(customerId, request, null);
    }

    @Transactional
    public AIWorkspaceInstallationSummary create(String customerId, CreateAIWorkspaceInstallationRequest request, String operationKey) {
        requireCustomerAccess(customerId);
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "AI Workspace installation request is required.");
        PlatformConsumerEntity consumer = requireConsumer(customerId, request.consumerId());
        ValidatedInput input = validateInput(
            request.displayName(), request.experiencePackCode(), request.experiencePackVersion(),
            request.connectionMode(), request.connectionProfileCode(), request.connectionProfileVersion(),
            request.connectionConfiguration(), request.allowedOrigins(), request.configuration(),
            request.deliveryMode(), request.appSlug(), request.customDomains(), request.interactionPolicy(),
            request.authProfileCode(), request.authProfileVersion(), request.authConfiguration(), null);
        deploymentSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
        authorizeConfiguration(consumer, input, operationKey);
        Instant now = Instant.now();
        AIWorkspaceInstallationEntity entity = new AIWorkspaceInstallationEntity();
        entity.setId("awi-" + UUID.randomUUID());
        entity.setInstallationId(publicInstallationId());
        entity.setCustomerId(customerId);
        entity.setConsumerEntityId(consumer.getId());
        entity.setDisplayName(input.displayName());
        entity.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        apply(entity, input);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.saveAndFlush(entity);
        synchronizeDomains(entity, input);
        deploymentSecurityProjection.synchronizeForConsumer(consumer);
        audit("AI_WORKSPACE_INSTALLATION_CREATED", entity, Map.of("consumerId", consumer.getConsumerId()));
        return summary(entity);
    }

    @Transactional
    public AIWorkspaceInstallationSummary update(String customerId,
                                                  String installationId,
                                                  UpdateAIWorkspaceInstallationRequest request) {
        return update(customerId, installationId, request, null);
    }

    @Transactional
    public AIWorkspaceInstallationSummary update(String customerId, String installationId,
                                                 UpdateAIWorkspaceInstallationRequest request, String operationKey) {
        requireCustomerAccess(customerId);
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "AI Workspace installation request is required.");
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        if (entity.getStatus() == AIWorkspaceInstallationStatus.ACTIVE) {
            throw new ResponseStatusException(CONFLICT, "Disable the AI Workspace installation before editing it.");
        }
        if (request.rowVersion() != null && request.rowVersion() != entity.getRowVersion()) {
            throw new ResponseStatusException(CONFLICT, "AI Workspace installation was changed by another operator.");
        }
        PlatformConsumerEntity previousConsumer = consumerRepository.findById(entity.getConsumerEntityId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Installation consumer is missing."));
        PlatformConsumerEntity consumer = requireConsumer(customerId, request.consumerId());
        ValidatedInput input = validateInput(
            request.displayName(), request.experiencePackCode(), request.experiencePackVersion(),
            request.connectionMode(), request.connectionProfileCode(), request.connectionProfileVersion(),
            request.connectionConfiguration(), request.allowedOrigins(), request.configuration(),
            request.deliveryMode(), request.appSlug(), request.customDomains(), request.interactionPolicy(),
            request.authProfileCode(), request.authProfileVersion(), request.authConfiguration(), entity.getId());
        deploymentSecurityProjection.lockDeploymentsForConsumers(List.of(previousConsumer, consumer));
        authorizeMutation(entity, previousConsumer, operationKey);
        authorizeConfiguration(consumer, input, operationKey);
        authorizeRemovedDomains(entity, input.origins(), operationKey);
        entity.setConsumerEntityId(consumer.getId());
        entity.setDisplayName(input.displayName());
        if (entity.getStatus() == AIWorkspaceInstallationStatus.DISABLED) {
            entity.setStatus(AIWorkspaceInstallationStatus.DRAFT);
            entity.setDisabledAt(null);
        }
        apply(entity, input);
        entity.setUpdatedAt(Instant.now());
        repository.saveAndFlush(entity);
        synchronizeDomains(entity, input);
        deploymentSecurityProjection.synchronizeForConsumer(consumer);
        if (!previousConsumer.getId().equals(consumer.getId())
            && StringUtils.hasText(previousConsumer.getBoundDeploymentId())
            && !java.util.Objects.equals(previousConsumer.getBoundDeploymentId(), consumer.getBoundDeploymentId())) {
            deploymentSecurityProjection.synchronizeDeployment(previousConsumer.getBoundDeploymentId());
        }
        audit("AI_WORKSPACE_INSTALLATION_UPDATED", entity, Map.of("consumerId", consumer.getConsumerId()));
        return summary(entity);
    }

    @Transactional
    public AIWorkspaceInstallationSummary activate(String customerId, String installationId) {
        return activate(customerId, installationId, null);
    }

    @Transactional
    public AIWorkspaceInstallationSummary activate(String customerId, String installationId, String operationKey) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        PlatformConsumerEntity consumer = consumerRepository.findById(entity.getConsumerEntityId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Installation consumer is missing."));
        deploymentSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
        authorizeConfiguration(consumer, storedInput(entity), operationKey);
        AIWorkspaceReadinessSummary readiness = readinessService.evaluate(entity);
        if (!readiness.ready()) {
            String blockers = readiness.checks().stream()
                .filter(check -> "BLOCKED".equals(check.status()))
                .map(check -> check.code() + ": " + check.message())
                .limit(5)
                .collect(java.util.stream.Collectors.joining("; "));
            throw new ResponseStatusException(CONFLICT, "AI Workspace installation is not ready. " + blockers);
        }
        Instant now = Instant.now();
        entity.setStatus(AIWorkspaceInstallationStatus.ACTIVE);
        entity.setActivatedAt(now);
        entity.setDisabledAt(null);
        entity.setUpdatedAt(now);
        repository.saveAndFlush(entity);
        deploymentSecurityProjection.synchronizeForConsumer(consumer);
        audit("AI_WORKSPACE_INSTALLATION_ACTIVATED", entity, Map.of("assignmentRevision", readiness.assignmentRevision()));
        return summary(entity);
    }

    @Transactional
    public AIWorkspaceInstallationSummary disable(String customerId, String installationId) {
        return disable(customerId, installationId, null);
    }

    @Transactional
    public AIWorkspaceInstallationSummary disable(String customerId, String installationId, String operationKey) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        PlatformConsumerEntity consumer = consumerRepository.findById(entity.getConsumerEntityId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Installation consumer is missing."));
        deploymentSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
        authorizeMutation(entity, consumer, operationKey);
        Instant now = Instant.now();
        entity.setStatus(AIWorkspaceInstallationStatus.DISABLED);
        entity.setDisabledAt(now);
        entity.setUpdatedAt(now);
        repository.saveAndFlush(entity);
        deploymentSecurityProjection.synchronizeForConsumer(consumer);
        audit("AI_WORKSPACE_INSTALLATION_DISABLED", entity, Map.of());
        return summary(entity);
    }

    @Transactional
    public void deleteDraft(String customerId, String installationId) {
        deleteDraft(customerId, installationId, null);
    }

    @Transactional
    public void deleteDraft(String customerId, String installationId, String operationKey) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        PlatformConsumerEntity consumer = consumerRepository.findById(entity.getConsumerEntityId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Installation consumer is missing."));
        deploymentSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
        authorizeMutation(entity, consumer, operationKey);
        if (entity.getStatus() != AIWorkspaceInstallationStatus.DRAFT) {
            throw new ResponseStatusException(CONFLICT, "Only unused draft AI Workspace installations can be deleted.");
        }
        repository.delete(entity);
        repository.flush();
        deploymentSecurityProjection.synchronizeForConsumer(consumer);
        audit("AI_WORKSPACE_INSTALLATION_DELETED", entity, Map.of());
    }

    @Transactional(readOnly = true)
    public AIWorkspaceReadinessSummary readiness(String customerId, String installationId) {
        requireCustomerAccess(customerId);
        return readinessService.evaluate(requireInstallation(customerId, installationId));
    }

    @Transactional
    public com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainSummary verifyDomain(
        String customerId,
        String installationId,
        String hostname
    ) {
        return verifyDomain(customerId, installationId, hostname, null);
    }

    @Transactional
    public com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainSummary verifyDomain(
        String customerId, String installationId, String hostname, String operationKey
    ) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        var consumer = consumerRepository.findById(entity.getConsumerEntityId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Installation consumer is missing."));
        offerBoundary.requireConsumer(consumer, Operation.DOMAIN_CONFIGURE, operationKey,
            Map.of(Setting.APP_DOMAIN, objectMapper.getNodeFactory().textNode("https://" + domainService.normalizeHostname(hostname))));
        var result = domainService.verify(entity.getId(), hostname);
        audit("AI_WORKSPACE_DOMAIN_VERIFIED", entity, Map.of("hostname", result.hostname(), "status", result.status()));
        return result;
    }

    private void authorizeMutation(AIWorkspaceInstallationEntity entity,
                                   PlatformConsumerEntity consumer,
                                   String operationKey) {
        if (!entity.getConsumerEntityId().equals(consumer.getId())) {
            throw new ResponseStatusException(CONFLICT, "Installation consumer requires reconciliation.");
        }
        offerBoundary.requireConsumer(consumer, Operation.APP_CONFIGURE, operationKey, Map.of());
    }

    private void authorizeRemovedDomains(AIWorkspaceInstallationEntity entity, List<String> origins, String operationKey) {
        List<String> removed = readOrigins(entity.getAllowedOriginsJson()).stream()
            .filter(origin -> !origins.contains(origin)).toList();
        if (removed.isEmpty()) return;
        var consumer = consumerRepository.findById(entity.getConsumerEntityId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Installation consumer is missing."));
        for (String origin : removed) {
            offerBoundary.requireConsumer(consumer, Operation.DOMAIN_CONFIGURE, operationKey,
                Map.of(Setting.APP_DOMAIN, objectMapper.getNodeFactory().textNode(origin)));
        }
    }

    private void authorizeConfiguration(PlatformConsumerEntity consumer, ValidatedInput input, String operationKey) {
        var interaction = objectMapper.createObjectNode();
        interaction.put("packCode", input.packCode()).put("packVersion", input.packVersion())
            .put("connectionMode", input.mode().name()).put("connectionProfileCode", input.profileCode())
            .put("connectionProfileVersion", input.profileVersion()).put("deliveryMode", input.deliveryMode().name());
        interaction.set("connectionConfiguration", input.connectionConfiguration());
        ObjectNode configuration = input.configuration().deepCopy();
        JsonNode theme = configuration.remove("theme");
        interaction.set("configuration", configuration);
        var settings = new java.util.EnumMap<Setting, JsonNode>(Setting.class);
        settings.put(Setting.APP_TITLE, objectMapper.getNodeFactory().textNode(input.displayName()));
        settings.put(Setting.INTERACTION_PROFILE_ID, objectMapper.getNodeFactory().textNode(offerBoundary.profileId("workspace", interaction)));
        settings.put(Setting.THEME_PROFILE_ID, objectMapper.getNodeFactory().textNode(offerBoundary.profileId("theme", theme)));
        var approved = offerBoundary.requireConsumer(consumer, Operation.APP_CONFIGURE, operationKey, settings);
        if (approved == null) return;
        var authentication = objectMapper.createObjectNode();
        authentication.put("profileCode", input.authProfileCode()).put("profileVersion", input.authProfileVersion())
            .put("interactionPolicy", input.interactionPolicy().name());
        authentication.set("configuration", input.authConfiguration());
        offerBoundary.requireConsumer(consumer, Operation.AUTH_CONFIGURE, operationKey,
            Map.of(Setting.AUTH_PROFILE_ID, objectMapper.getNodeFactory().textNode(offerBoundary.profileId("auth", authentication))));
        for (String origin : input.origins()) {
            offerBoundary.requireConsumer(consumer, Operation.DOMAIN_CONFIGURE, operationKey,
                Map.of(Setting.APP_DOMAIN, objectMapper.getNodeFactory().textNode(origin)));
        }
    }

    private ValidatedInput storedInput(AIWorkspaceInstallationEntity entity) {
        return new ValidatedInput(entity.getDisplayName(), entity.getExperiencePackCode(), entity.getExperiencePackVersion(),
            entity.getConnectionMode(), entity.getConnectionProfileCode(), entity.getConnectionProfileVersion(),
            requireObject(readJson(entity.getConnectionConfigurationJson()), "connectionConfiguration"),
            readOrigins(entity.getAllowedOriginsJson()), requireObject(readJson(entity.getConfigurationJson()), "configuration"),
            entity.getDeliveryMode(), entity.getAppSlug(), List.of(), entity.getInteractionPolicy(),
            entity.getAuthProfileCode(), entity.getAuthProfileVersion(), requireObject(readJson(entity.getAuthConfigurationJson()), "authConfiguration"));
    }

    private void apply(AIWorkspaceInstallationEntity entity, ValidatedInput input) {
        entity.setExperiencePackCode(input.packCode());
        entity.setExperiencePackVersion(input.packVersion());
        entity.setConnectionMode(input.mode());
        entity.setConnectionProfileCode(input.profileCode());
        entity.setConnectionProfileVersion(input.profileVersion());
        entity.setDeliveryMode(input.deliveryMode());
        entity.setAppSlug(input.appSlug());
        entity.setInteractionPolicy(input.interactionPolicy());
        entity.setAuthProfileCode(input.authProfileCode());
        entity.setAuthProfileVersion(input.authProfileVersion());
        entity.setAuthConfigurationJson(writeJson(input.authConfiguration()));
        entity.setConnectionConfigurationJson(writeJson(input.connectionConfiguration()));
        entity.setAllowedOriginsJson(writeJson(input.origins()));
        entity.setConfigurationJson(writeJson(input.configuration()));
    }

    private ValidatedInput validateInput(String displayName,
                                         String packCode,
                                         String packVersion,
                                         String connectionMode,
                                         String profileCode,
                                         String profileVersion,
                                         JsonNode connectionConfiguration,
                                         List<String> allowedOrigins,
                                         JsonNode configuration,
                                         String deliveryMode,
                                         String appSlug,
                                         List<String> customDomains,
                                         String interactionPolicy,
                                         String authProfileCode,
                                         String authProfileVersion,
                                         JsonNode authConfiguration,
                                         String currentEntityId) {
        String normalizedName = required(displayName, "displayName", 255);
        String normalizedPackCode = required(packCode, "experiencePackCode", 96);
        String normalizedPackVersion = required(packVersion, "experiencePackVersion", 64);
        String normalizedProfileCode = required(profileCode, "connectionProfileCode", 96);
        String normalizedProfileVersion = required(profileVersion, "connectionProfileVersion", 64);
        AIWorkspaceConnectionMode mode;
        AIWorkspaceDeliveryMode delivery;
        AIWorkspaceInteractionPolicy policy;
        try {
            mode = AIWorkspaceConnectionMode.parse(connectionMode);
            delivery = AIWorkspaceDeliveryMode.parse(deliveryMode);
            policy = AIWorkspaceInteractionPolicy.parse(interactionPolicy);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace connection, delivery, or interaction mode.");
        }
        String normalizedSlug = delivery == AIWorkspaceDeliveryMode.STANDALONE
            ? domainService.normalizeSlug(appSlug)
            : null;
        if (delivery == AIWorkspaceDeliveryMode.EMBEDDED && appSlug != null && !appSlug.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "appSlug is supported only for standalone delivery.");
        }
        if (normalizedSlug != null) {
            repository.findByAppSlugIgnoreCase(normalizedSlug).ifPresent(existing -> {
                if (!existing.getId().equals(currentEntityId)) {
                    throw new ResponseStatusException(CONFLICT, "AI app slug is already reserved.");
                }
            });
        }
        if (policy == AIWorkspaceInteractionPolicy.LOGIN_REQUIRED
            && mode != AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED) {
            throw new ResponseStatusException(BAD_REQUEST, "LOGIN_REQUIRED uses the authenticated runtime connection mode.");
        }
        if (policy == AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES
            && mode != AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS) {
            throw new ResponseStatusException(BAD_REQUEST, "Mixed public/protected interaction uses the anonymous runtime connection mode with an authenticated broker.");
        }
        ObjectNode safeConnection = connectionConfiguration == null || connectionConfiguration.isNull()
            ? objectMapper.createObjectNode()
            : requireObject(connectionConfiguration, "connectionConfiguration");
        ObjectNode safeConfiguration = requireObject(configuration, "configuration");
        List<String> domainNames = delivery == AIWorkspaceDeliveryMode.STANDALONE
            ? normalizeCustomDomains(customDomains)
            : List.of();
        List<String> requestedOrigins = delivery == AIWorkspaceDeliveryMode.STANDALONE
            ? new java.util.ArrayList<>()
            : allowedOrigins == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(allowedOrigins);
        if (delivery == AIWorkspaceDeliveryMode.STANDALONE) {
            requestedOrigins.add("https://" + normalizedSlug + "." + domainServiceBaseDomain());
            domainNames.forEach(hostname -> requestedOrigins.add("https://" + hostname));
        }
        List<String> origins = configurationValidator.normalizeOrigins(requestedOrigins);
        experiencePackRegistry.validateConfiguration(normalizedPackCode, normalizedPackVersion, safeConfiguration);
        configurationValidator.validatePublicConfiguration(safeConnection);
        connectionProfileRegistry.resolve(
            normalizedProfileCode, normalizedProfileVersion, mode, safeConnection);
        String normalizedAuthProfileCode = authProfileCode == null || authProfileCode.isBlank()
            ? AIWorkspaceAuthenticationProfileRegistry.NONE
            : required(authProfileCode, "authProfileCode", 96);
        String normalizedAuthProfileVersion = authProfileVersion == null || authProfileVersion.isBlank()
            ? AIWorkspaceAuthenticationProfileRegistry.VERSION
            : required(authProfileVersion, "authProfileVersion", 64);
        ObjectNode safeAuthConfiguration = authConfiguration == null || authConfiguration.isNull()
            ? objectMapper.createObjectNode()
            : requireObject(authConfiguration, "authConfiguration");
        authenticationProfileRegistry.resolve(
            normalizedAuthProfileCode, normalizedAuthProfileVersion, policy, safeAuthConfiguration);
        return new ValidatedInput(normalizedName, normalizedPackCode, normalizedPackVersion, mode,
            normalizedProfileCode, normalizedProfileVersion, safeConnection, origins, safeConfiguration,
            delivery, normalizedSlug, domainNames, policy, normalizedAuthProfileCode,
            normalizedAuthProfileVersion, safeAuthConfiguration);
    }

    private void synchronizeDomains(AIWorkspaceInstallationEntity entity, ValidatedInput input) {
        List<String> origins = domainService.synchronize(
            entity, input.deliveryMode(), input.appSlug(), input.customDomains(), input.origins());
        entity.setAllowedOriginsJson(writeJson(origins));
        repository.saveAndFlush(entity);
    }

    private List<String> normalizeCustomDomains(List<String> customDomains) {
        if (customDomains == null) return List.of();
        return customDomains.stream().filter(value -> value != null && !value.isBlank())
            .map(domainService::normalizeHostname).distinct().toList();
    }

    private String domainServiceBaseDomain() {
        return domainService.defaultBaseDomain();
    }

    private PlatformConsumerEntity requireConsumer(String customerId, String consumerId) {
        String normalized = required(consumerId, "consumerId", 128);
        PlatformConsumerEntity consumer = consumerRepository
            .findByCustomerIdAndConsumerIdIgnoreCase(customerId, normalized)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Consumer not found: " + normalized));
        if (!"ACTIVE".equalsIgnoreCase(consumer.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Consumer must be active.");
        }
        return consumer;
    }

    private AIWorkspaceInstallationEntity requireInstallation(String customerId, String installationId) {
        return repository.findByCustomerIdAndInstallationId(customerId, installationId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI Workspace installation not found."));
    }

    private void requireCustomerAccess(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(NOT_FOUND, "Customer not found: " + customerId);
        }
        customerAccessService.requireCustomerManagementAccess(customerId);
    }

    private AIWorkspaceInstallationSummary summary(AIWorkspaceInstallationEntity entity) {
        PlatformConsumerEntity consumer = consumerRepository.findById(entity.getConsumerEntityId()).orElse(null);
        AIWorkspaceReadinessSummary readiness = readinessService.evaluate(entity);
        return new AIWorkspaceInstallationSummary(
            entity.getId(), entity.getInstallationId(), entity.getCustomerId(),
            consumer == null ? null : consumer.getConsumerId(), entity.getDisplayName(), entity.getStatus().name(),
            entity.getExperiencePackCode(), entity.getExperiencePackVersion(), entity.getConnectionMode().manifestValue(),
            entity.getConnectionProfileCode(), entity.getConnectionProfileVersion(),
            readJson(entity.getConnectionConfigurationJson()), readOrigins(entity.getAllowedOriginsJson()),
            readJson(entity.getConfigurationJson()),
            (entity.getDeliveryMode() == null ? AIWorkspaceDeliveryMode.EMBEDDED : entity.getDeliveryMode()).manifestValue(), entity.getAppSlug(),
            domainService.appUrl(entity),
            (entity.getInteractionPolicy() == null ? AIWorkspaceInteractionPolicy.PUBLIC : entity.getInteractionPolicy()).manifestValue(), entity.getAuthProfileCode(),
            entity.getAuthProfileVersion(), readJson(entity.getAuthConfigurationJson()), domainService.summaries(entity.getId()),
            readiness.deploymentId(), readiness.releaseId(),
            readiness.assignmentRevision(), readiness.ready(), readiness.checks(), entity.getRowVersion(),
            entity.getCreatedAt(), entity.getUpdatedAt(), entity.getActivatedAt(), entity.getDisabledAt()
        );
    }

    private ObjectNode requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, field + " must be an object.");
        }
        return (ObjectNode) node.deepCopy();
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(BAD_REQUEST, field + " is required.");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new ResponseStatusException(BAD_REQUEST, field + " is too long.");
        return normalized;
    }

    private String publicInstallationId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return "awi_pub_" + HexFormat.of().formatHex(bytes);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize AI Workspace configuration.", ex);
        }
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw);
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

    private void audit(String action, AIWorkspaceInstallationEntity entity, Map<String, ?> details) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", entity.getCustomerId());
        payload.put("installationId", entity.getInstallationId());
        payload.put("status", entity.getStatus().name());
        payload.putAll(details);
        auditService.record(action, "AI_WORKSPACE_INSTALLATION", entity.getInstallationId(), payload);
    }

    private record ValidatedInput(
        String displayName,
        String packCode,
        String packVersion,
        AIWorkspaceConnectionMode mode,
        String profileCode,
        String profileVersion,
        JsonNode connectionConfiguration,
        List<String> origins,
        JsonNode configuration,
        AIWorkspaceDeliveryMode deliveryMode,
        String appSlug,
        List<String> customDomains,
        AIWorkspaceInteractionPolicy interactionPolicy,
        String authProfileCode,
        String authProfileVersion,
        JsonNode authConfiguration
    ) {
    }
}
