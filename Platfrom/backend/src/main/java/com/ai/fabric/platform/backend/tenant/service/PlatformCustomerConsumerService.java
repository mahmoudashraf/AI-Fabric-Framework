package com.ai.fabric.platform.backend.tenant.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerBindingHistoryEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerBindingHistorySummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerBindingHistoryRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformCustomerConsumerService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    private final PlatformConsumerRepository consumerRepository;
    private final PlatformConsumerBindingHistoryRepository bindingHistoryRepository;
    private final PlatformCustomerRepository customerRepository;
    private final DeploymentRepository deploymentRepository;
    private final PlatformCustomerAccessService platformCustomerAccessService;
    private final PlatformAuditService platformAuditService;

    public PlatformCustomerConsumerService(PlatformConsumerRepository consumerRepository,
                                           PlatformConsumerBindingHistoryRepository bindingHistoryRepository,
                                           PlatformCustomerRepository customerRepository,
                                           DeploymentRepository deploymentRepository,
                                           PlatformCustomerAccessService platformCustomerAccessService,
                                           PlatformAuditService platformAuditService) {
        this.consumerRepository = consumerRepository;
        this.bindingHistoryRepository = bindingHistoryRepository;
        this.customerRepository = customerRepository;
        this.deploymentRepository = deploymentRepository;
        this.platformCustomerAccessService = platformCustomerAccessService;
        this.platformAuditService = platformAuditService;
    }

    @Transactional(readOnly = true)
    public List<PlatformConsumerSummary> listConsumers(String customerId) {
        PlatformCustomerEntity customer = requireCustomer(customerId);
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
        return summarizeConsumers(consumerRepository.findByCustomerIdOrderByCreatedAtAsc(customer.getId()));
    }

    @Transactional
    public PlatformConsumerSummary createConsumer(String customerId, CreatePlatformConsumerRequest request) {
        PlatformCustomerEntity customer = requireActiveCustomer(customerId);
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());

        String normalizedConsumerId = normalizeConsumerId(request.consumerId());
        if (consumerRepository.findByConsumerIdIgnoreCase(normalizedConsumerId).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Consumer id already exists: " + normalizedConsumerId);
        }

        Instant now = Instant.now();
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId(generateId("con"));
        consumer.setCustomerId(customer.getId());
        consumer.setConsumerId(normalizedConsumerId);
        consumer.setDisplayName(normalizeRequiredDisplayName(request.displayName()));
        consumer.setDescription(normalizeDescription(request.description()));
        consumer.setStatus(ACTIVE);
        consumer.setCreatedAt(now);
        consumer.setUpdatedAt(now);

        String requestedDeploymentId = normalizeOptionalId(request.deploymentId());
        if (requestedDeploymentId != null) {
            DeploymentEntity deployment = requireBindableDeployment(customer.getId(), requestedDeploymentId);
            consumer.setBoundDeploymentId(deployment.getId());
            consumer.setLastBoundAt(now);
        }
        consumerRepository.saveAndFlush(consumer);

        if (requestedDeploymentId != null) {
            recordBindingHistory(
                consumer,
                null,
                requestedDeploymentId,
                normalizeDescription(request.bindingReason()),
                now
            );
        }

        platformAuditService.record(
            "CUSTOMER_CONSUMER_CREATED",
            "PLATFORM_CONSUMER",
            consumer.getConsumerId(),
            auditDetails(
                "customerId", customer.getId(),
                "consumerId", consumer.getConsumerId(),
                "boundDeploymentId", consumer.getBoundDeploymentId()
            )
        );
        return summarizeConsumer(consumer);
    }

    @Transactional
    public PlatformConsumerSummary updateConsumer(String customerId,
                                                  String consumerId,
                                                  UpdatePlatformConsumerRequest request) {
        PlatformConsumerEntity consumer = requireManagedConsumer(customerId, consumerId);
        consumer.setDisplayName(normalizeRequiredDisplayName(request.displayName()));
        consumer.setDescription(normalizeDescription(request.description()));
        consumer.setStatus(normalizeStatus(request.status()));
        consumer.setUpdatedAt(Instant.now());
        consumerRepository.save(consumer);

        platformAuditService.record(
            "CUSTOMER_CONSUMER_UPDATED",
            "PLATFORM_CONSUMER",
            consumer.getConsumerId(),
            Map.of(
                "customerId", consumer.getCustomerId(),
                "consumerId", consumer.getConsumerId(),
                "status", consumer.getStatus()
            )
        );
        return summarizeConsumer(consumer);
    }

    @Transactional
    public PlatformConsumerSummary updateBinding(String customerId,
                                                 String consumerId,
                                                 UpdatePlatformConsumerBindingRequest request) {
        PlatformConsumerEntity consumer = requireManagedConsumer(customerId, consumerId);
        String nextDeploymentId = normalizeOptionalId(request.deploymentId());
        if (sameDeployment(consumer.getBoundDeploymentId(), nextDeploymentId)) {
            return summarizeConsumer(consumer);
        }

        if (nextDeploymentId != null) {
            requireBindableDeployment(consumer.getCustomerId(), nextDeploymentId);
        }

        String previousDeploymentId = consumer.getBoundDeploymentId();
        Instant now = Instant.now();
        consumer.setBoundDeploymentId(nextDeploymentId);
        if (nextDeploymentId != null) {
            consumer.setLastBoundAt(now);
        }
        consumer.setUpdatedAt(now);
        consumerRepository.saveAndFlush(consumer);
        recordBindingHistory(
            consumer,
            previousDeploymentId,
            nextDeploymentId,
            normalizeDescription(request.reason()),
            now
        );

        platformAuditService.record(
            "CUSTOMER_CONSUMER_BINDING_UPDATED",
            "PLATFORM_CONSUMER",
            consumer.getConsumerId(),
            auditDetails(
                "customerId", consumer.getCustomerId(),
                "consumerId", consumer.getConsumerId(),
                "fromDeploymentId", previousDeploymentId,
                "toDeploymentId", nextDeploymentId,
                "reason", normalizeDescription(request.reason())
            )
        );
        return summarizeConsumer(consumer);
    }

    @Transactional
    public void deleteConsumer(String customerId, String consumerId) {
        PlatformConsumerEntity consumer = requireManagedConsumer(customerId, consumerId);
        if (StringUtils.hasText(consumer.getBoundDeploymentId())) {
            throw new ResponseStatusException(CONFLICT, "Unbind the consumer before deleting it.");
        }
        consumerRepository.delete(consumer);
        platformAuditService.record(
            "CUSTOMER_CONSUMER_DELETED",
            "PLATFORM_CONSUMER",
            consumer.getConsumerId(),
            Map.of(
                "customerId", consumer.getCustomerId(),
                "consumerId", consumer.getConsumerId()
            )
        );
    }

    @Transactional(readOnly = true)
    public List<PlatformConsumerBindingHistorySummary> listBindingHistory(String customerId, String consumerId) {
        PlatformConsumerEntity consumer = requireManagedConsumer(customerId, consumerId);
        List<PlatformConsumerBindingHistoryEntity> history = bindingHistoryRepository
            .findByConsumerEntityIdOrderByCreatedAtDesc(consumer.getId());
        return summarizeBindingHistory(history);
    }

    @Transactional(readOnly = true)
    public Map<String, List<PlatformConsumerSummary>> summarizeConsumersByCustomerIds(Collection<String> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        List<String> normalizedIds = customerIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<String, List<PlatformConsumerSummary>> result = new LinkedHashMap<>();
        normalizedIds.forEach(customerId -> result.put(customerId, List.of()));

        List<PlatformConsumerSummary> summaries = summarizeConsumers(
            consumerRepository.findByCustomerIdInOrderByCreatedAtAsc(normalizedIds)
        );
        Map<String, List<PlatformConsumerSummary>> grouped = summaries.stream()
            .collect(Collectors.groupingBy(PlatformConsumerSummary::customerId, LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((customerId, consumers) -> result.put(
            customerId,
            consumers.stream()
                .sorted(Comparator.comparing(PlatformConsumerSummary::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList()
        ));
        return result;
    }

    @Transactional(readOnly = true)
    public PlatformConsumerSummary summarizeBoundConsumerForDeployment(String deploymentId) {
        if (!StringUtils.hasText(deploymentId)) {
            return null;
        }
        return consumerRepository.findByBoundDeploymentId(deploymentId.trim())
            .map(this::summarizeConsumer)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasBoundConsumer(String deploymentId) {
        return StringUtils.hasText(deploymentId)
            && consumerRepository.findByBoundDeploymentId(deploymentId.trim()).isPresent();
    }

    @Transactional(readOnly = true)
    public ResolvedPublicConsumer resolvePublicConsumer(String consumerId) {
        String normalizedConsumerId = normalizeConsumerId(consumerId);
        PlatformConsumerEntity consumer = consumerRepository.findByConsumerIdIgnoreCase(normalizedConsumerId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Consumer not found: " + normalizedConsumerId));
        if (!ACTIVE.equalsIgnoreCase(consumer.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Consumer is disabled: " + normalizedConsumerId);
        }
        if (!StringUtils.hasText(consumer.getBoundDeploymentId())) {
            throw new ResponseStatusException(CONFLICT, "Consumer is not bound to a deployment: " + normalizedConsumerId);
        }
        DeploymentEntity deployment = deploymentRepository.findById(consumer.getBoundDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Consumer binding points to a missing deployment."));
        return new ResolvedPublicConsumer(consumer, deployment);
    }

    private PlatformConsumerEntity requireManagedConsumer(String customerId, String consumerId) {
        PlatformCustomerEntity customer = requireCustomer(customerId);
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
        String normalizedConsumerId = normalizeConsumerId(consumerId);
        PlatformConsumerEntity consumer = consumerRepository.findByCustomerIdAndConsumerIdIgnoreCase(customer.getId(), normalizedConsumerId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Consumer not found: " + normalizedConsumerId));
        if (!customer.getId().equals(consumer.getCustomerId())) {
            throw new ResponseStatusException(NOT_FOUND, "Consumer not found: " + normalizedConsumerId);
        }
        return consumer;
    }

    private PlatformCustomerEntity requireCustomer(String customerId) {
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer not found: " + customerId));
    }

    private PlatformCustomerEntity requireActiveCustomer(String customerId) {
        PlatformCustomerEntity customer = requireCustomer(customerId);
        if (!ACTIVE.equalsIgnoreCase(customer.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer is not active: " + customerId);
        }
        return customer;
    }

    private DeploymentEntity requireBindableDeployment(String customerId, String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        if (!customerId.equals(deployment.getCustomerId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment does not belong to the selected customer.");
        }
        if (deployment.getArchivedAt() != null || "ARCHIVED".equalsIgnoreCase(deployment.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Archived deployments cannot be bound to consumers.");
        }
        PlatformConsumerEntity existing = consumerRepository.findByBoundDeploymentId(deploymentId).orElse(null);
        if (existing != null) {
            throw new ResponseStatusException(CONFLICT, "Deployment is already bound to consumer: " + existing.getConsumerId());
        }
        return deployment;
    }

    private List<PlatformConsumerSummary> summarizeConsumers(List<PlatformConsumerEntity> consumers) {
        if (consumers == null || consumers.isEmpty()) {
            return List.of();
        }
        List<String> deploymentIds = consumers.stream()
            .map(PlatformConsumerEntity::getBoundDeploymentId)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        Map<String, DeploymentEntity> deploymentsById = deploymentIds.isEmpty()
            ? Collections.emptyMap()
            : deploymentRepository.findAllById(deploymentIds).stream()
                .collect(Collectors.toMap(DeploymentEntity::getId, deployment -> deployment, (left, right) -> left, LinkedHashMap::new));
        return consumers.stream()
            .map(consumer -> {
                DeploymentEntity deployment = deploymentsById.get(consumer.getBoundDeploymentId());
                return new PlatformConsumerSummary(
                    consumer.getConsumerId(),
                    consumer.getCustomerId(),
                    consumer.getDisplayName(),
                    consumer.getDescription(),
                    consumer.getStatus(),
                    consumer.getBoundDeploymentId(),
                    deployment == null ? null : deployment.getName(),
                    deployment == null ? null : deployment.getEnvironmentName(),
                    deployment == null ? null : deployment.getStatus(),
                    consumer.getLastBoundAt(),
                    consumer.getCreatedAt(),
                    consumer.getUpdatedAt()
                );
            })
            .toList();
    }

    private PlatformConsumerSummary summarizeConsumer(PlatformConsumerEntity consumer) {
        return summarizeConsumers(List.of(consumer)).stream().findFirst().orElseThrow();
    }

    private List<PlatformConsumerBindingHistorySummary> summarizeBindingHistory(List<PlatformConsumerBindingHistoryEntity> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deploymentIds = new LinkedHashSet<>();
        entries.forEach(entry -> {
            if (StringUtils.hasText(entry.getFromDeploymentId())) {
                deploymentIds.add(entry.getFromDeploymentId().trim());
            }
            if (StringUtils.hasText(entry.getToDeploymentId())) {
                deploymentIds.add(entry.getToDeploymentId().trim());
            }
        });
        Map<String, DeploymentEntity> deploymentsById = deploymentIds.isEmpty()
            ? Collections.emptyMap()
            : deploymentRepository.findAllById(deploymentIds).stream()
                .collect(Collectors.toMap(DeploymentEntity::getId, deployment -> deployment, (left, right) -> left, LinkedHashMap::new));
        return entries.stream()
            .map(entry -> new PlatformConsumerBindingHistorySummary(
                entry.getConsumerId(),
                entry.getCustomerId(),
                entry.getFromDeploymentId(),
                deploymentName(deploymentsById.get(entry.getFromDeploymentId())),
                entry.getToDeploymentId(),
                deploymentName(deploymentsById.get(entry.getToDeploymentId())),
                entry.getReason(),
                entry.getActorId(),
                entry.getActorRole(),
                entry.getCreatedAt()
            ))
            .toList();
    }

    private void recordBindingHistory(PlatformConsumerEntity consumer,
                                      String fromDeploymentId,
                                      String toDeploymentId,
                                      String reason,
                                      Instant createdAt) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        PlatformConsumerBindingHistoryEntity entry = new PlatformConsumerBindingHistoryEntity();
        entry.setId(generateId("cbh"));
        entry.setConsumerEntityId(consumer.getId());
        entry.setConsumerId(consumer.getConsumerId());
        entry.setCustomerId(consumer.getCustomerId());
        entry.setFromDeploymentId(fromDeploymentId);
        entry.setToDeploymentId(toDeploymentId);
        entry.setReason(reason);
        entry.setActorId(principal == null ? "system" : principal.actorId());
        entry.setActorRole(principal == null ? "SYSTEM" : principal.role().name());
        entry.setCreatedAt(createdAt);
        bindingHistoryRepository.save(entry);
    }

    private String deploymentName(DeploymentEntity deployment) {
        return deployment == null ? null : deployment.getName();
    }

    private boolean sameDeployment(String left, String right) {
        String normalizedLeft = normalizeOptionalId(left);
        String normalizedRight = normalizeOptionalId(right);
        return normalizedLeft == null ? normalizedRight == null : normalizedLeft.equals(normalizedRight);
    }

    private String normalizeConsumerId(String consumerId) {
        if (!StringUtils.hasText(consumerId)) {
            throw new ResponseStatusException(BAD_REQUEST, "consumerId is required.");
        }
        String normalized = consumerId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "consumerId must be 3-128 characters and contain only lowercase letters, numbers, hyphen, or underscore."
            );
        }
        return normalized;
    }

    private String normalizeRequiredDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName) || displayName.trim().length() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Display name must be at least 2 characters.");
        }
        return displayName.trim();
    }

    private String normalizeDescription(String description) {
        return StringUtils.hasText(description) ? description.trim() : null;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return ACTIVE;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !DISABLED.equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported consumer status: " + status.trim());
        }
        return normalized;
    }

    private String normalizeOptionalId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> auditDetails(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Audit details require key/value pairs.");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object key = entries[index];
            if (!(key instanceof String keyString) || !StringUtils.hasText(keyString)) {
                throw new IllegalArgumentException("Audit detail keys must be non-empty strings.");
            }
            details.put(keyString, entries[index + 1]);
        }
        return details;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public record ResolvedPublicConsumer(
        PlatformConsumerEntity consumer,
        DeploymentEntity deployment
    ) {
    }
}
