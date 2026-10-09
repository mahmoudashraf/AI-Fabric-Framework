package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
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
    private final AIWorkspaceConfigurationValidator configurationValidator;
    private final AIWorkspaceReadinessService readinessService;
    private final AIWorkspaceAssetCatalogService assetCatalogService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    public AIWorkspaceInstallationService(AIWorkspaceInstallationRepository repository,
                                          PlatformCustomerRepository customerRepository,
                                          PlatformConsumerRepository consumerRepository,
                                          PlatformCustomerAccessService customerAccessService,
                                          AIWorkspaceExperiencePackRegistry experiencePackRegistry,
                                          AIWorkspaceConnectionProfileRegistry connectionProfileRegistry,
                                          AIWorkspaceConfigurationValidator configurationValidator,
                                          AIWorkspaceReadinessService readinessService,
                                          AIWorkspaceAssetCatalogService assetCatalogService,
                                          PlatformAuditService auditService,
                                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.consumerRepository = consumerRepository;
        this.customerAccessService = customerAccessService;
        this.experiencePackRegistry = experiencePackRegistry;
        this.connectionProfileRegistry = connectionProfileRegistry;
        this.configurationValidator = configurationValidator;
        this.readinessService = readinessService;
        this.assetCatalogService = assetCatalogService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AIWorkspaceCatalogSummary catalog() {
        var health = assetCatalogService.health();
        return new AIWorkspaceCatalogSummary(
            health.ready(), health.status(), experiencePackRegistry.summaries(), connectionProfileRegistry.summaries());
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
        requireCustomerAccess(customerId);
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "AI Workspace installation request is required.");
        PlatformConsumerEntity consumer = requireConsumer(customerId, request.consumerId());
        ValidatedInput input = validateInput(
            request.displayName(), request.experiencePackCode(), request.experiencePackVersion(),
            request.connectionMode(), request.connectionProfileCode(), request.connectionProfileVersion(),
            request.connectionConfiguration(), request.allowedOrigins(), request.configuration());
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
        audit("AI_WORKSPACE_INSTALLATION_CREATED", entity, Map.of("consumerId", consumer.getConsumerId()));
        return summary(entity);
    }

    @Transactional
    public AIWorkspaceInstallationSummary update(String customerId,
                                                  String installationId,
                                                  UpdateAIWorkspaceInstallationRequest request) {
        requireCustomerAccess(customerId);
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "AI Workspace installation request is required.");
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        if (entity.getStatus() == AIWorkspaceInstallationStatus.ACTIVE) {
            throw new ResponseStatusException(CONFLICT, "Disable the AI Workspace installation before editing it.");
        }
        if (request.rowVersion() != null && request.rowVersion() != entity.getRowVersion()) {
            throw new ResponseStatusException(CONFLICT, "AI Workspace installation was changed by another operator.");
        }
        PlatformConsumerEntity consumer = requireConsumer(customerId, request.consumerId());
        ValidatedInput input = validateInput(
            request.displayName(), request.experiencePackCode(), request.experiencePackVersion(),
            request.connectionMode(), request.connectionProfileCode(), request.connectionProfileVersion(),
            request.connectionConfiguration(), request.allowedOrigins(), request.configuration());
        entity.setConsumerEntityId(consumer.getId());
        entity.setDisplayName(input.displayName());
        apply(entity, input);
        entity.setUpdatedAt(Instant.now());
        repository.saveAndFlush(entity);
        audit("AI_WORKSPACE_INSTALLATION_UPDATED", entity, Map.of("consumerId", consumer.getConsumerId()));
        return summary(entity);
    }

    @Transactional
    public AIWorkspaceInstallationSummary activate(String customerId, String installationId) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
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
        audit("AI_WORKSPACE_INSTALLATION_ACTIVATED", entity, Map.of("assignmentRevision", readiness.assignmentRevision()));
        return summary(entity);
    }

    @Transactional
    public AIWorkspaceInstallationSummary disable(String customerId, String installationId) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        Instant now = Instant.now();
        entity.setStatus(AIWorkspaceInstallationStatus.DISABLED);
        entity.setDisabledAt(now);
        entity.setUpdatedAt(now);
        repository.saveAndFlush(entity);
        audit("AI_WORKSPACE_INSTALLATION_DISABLED", entity, Map.of());
        return summary(entity);
    }

    @Transactional
    public void deleteDraft(String customerId, String installationId) {
        requireCustomerAccess(customerId);
        AIWorkspaceInstallationEntity entity = requireInstallation(customerId, installationId);
        if (entity.getStatus() != AIWorkspaceInstallationStatus.DRAFT) {
            throw new ResponseStatusException(CONFLICT, "Only unused draft AI Workspace installations can be deleted.");
        }
        repository.delete(entity);
        audit("AI_WORKSPACE_INSTALLATION_DELETED", entity, Map.of());
    }

    @Transactional(readOnly = true)
    public AIWorkspaceReadinessSummary readiness(String customerId, String installationId) {
        requireCustomerAccess(customerId);
        return readinessService.evaluate(requireInstallation(customerId, installationId));
    }

    private void apply(AIWorkspaceInstallationEntity entity, ValidatedInput input) {
        entity.setExperiencePackCode(input.packCode());
        entity.setExperiencePackVersion(input.packVersion());
        entity.setConnectionMode(input.mode());
        entity.setConnectionProfileCode(input.profileCode());
        entity.setConnectionProfileVersion(input.profileVersion());
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
                                         JsonNode configuration) {
        String normalizedName = required(displayName, "displayName", 255);
        String normalizedPackCode = required(packCode, "experiencePackCode", 96);
        String normalizedPackVersion = required(packVersion, "experiencePackVersion", 64);
        String normalizedProfileCode = required(profileCode, "connectionProfileCode", 96);
        String normalizedProfileVersion = required(profileVersion, "connectionProfileVersion", 64);
        AIWorkspaceConnectionMode mode;
        try {
            mode = AIWorkspaceConnectionMode.parse(connectionMode);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace connection mode.");
        }
        ObjectNode safeConnection = connectionConfiguration == null || connectionConfiguration.isNull()
            ? objectMapper.createObjectNode()
            : requireObject(connectionConfiguration, "connectionConfiguration");
        ObjectNode safeConfiguration = requireObject(configuration, "configuration");
        List<String> origins = configurationValidator.normalizeOrigins(allowedOrigins);
        experiencePackRegistry.validateConfiguration(normalizedPackCode, normalizedPackVersion, safeConfiguration);
        configurationValidator.validatePublicConfiguration(safeConnection);
        connectionProfileRegistry.resolve(
            normalizedProfileCode, normalizedProfileVersion, mode, safeConnection);
        return new ValidatedInput(normalizedName, normalizedPackCode, normalizedPackVersion, mode,
            normalizedProfileCode, normalizedProfileVersion, safeConnection, origins, safeConfiguration);
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
            readJson(entity.getConfigurationJson()), readiness.deploymentId(), readiness.releaseId(),
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
        JsonNode configuration
    ) {
    }
}
