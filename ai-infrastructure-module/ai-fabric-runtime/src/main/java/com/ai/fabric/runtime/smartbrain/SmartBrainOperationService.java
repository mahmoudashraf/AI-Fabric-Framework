package com.ai.fabric.runtime.smartbrain;

import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.gateway.ExecutionHandle;
import ai.fabric.execution.gateway.ExecutionHandleStatus;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.client.SpecialistClient;
import ai.fabric.execution.specialist.client.SpecialistClientFactory;
import ai.fabric.execution.specialist.client.SpecialistExecutionSnapshot;
import ai.fabric.execution.specialist.client.SpecialistInvocation;
import ai.fabric.execution.specialist.manifest.CanonicalJsonSupport;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainOperationService {

    public static final SpecialistId SPECIALIST_ID = SpecialistId.of("smart-brain-event-analyst", "1");
    private static final Set<String> INTERNAL_SCOPES = Set.of("specialist:smart-brain-event-analyst@1");
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACCEPTED", "QUEUED", "RUNNING");
    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED");

    private final SmartBrainOperationRepository operationRepository;
    private final SmartBrainConfigurationService configurationService;
    private final SmartBrainSecurity security;
    private final SpecialistClient<JsonNode, JsonNode> specialistClient;
    private final ObjectMapper objectMapper;
    private final CanonicalJsonSupport canonicalJson;
    private final SmartBrainDeliveryService deliveryService;
    private final Duration retention;
    private final String acceptedRuntimeUrl;
    private final String runtimeTenantId;
    private final String runtimeDeploymentId;

    public SmartBrainOperationService(
        SmartBrainOperationRepository operationRepository,
        SmartBrainConfigurationService configurationService,
        SmartBrainSecurity security,
        SpecialistClientFactory specialistClientFactory,
        SmartBrainDeliveryService deliveryService,
        ObjectMapper objectMapper,
        @Value("${loomai.smart-brain.operation-retention:P30D}") Duration retention,
        @Value("${loomai.smart-brain.accepted-runtime-url:}") String acceptedRuntimeUrl,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.tenant-id:}") String runtimeTenantId,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.deployment-id:}") String runtimeDeploymentId
    ) {
        this.operationRepository = operationRepository;
        this.configurationService = configurationService;
        this.security = security;
        this.specialistClient = specialistClientFactory.bind(SPECIALIST_ID, JsonNode.class, JsonNode.class);
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
        this.canonicalJson = new CanonicalJsonSupport(objectMapper);
        this.retention = retention;
        this.acceptedRuntimeUrl = stripTrailingSlash(acceptedRuntimeUrl);
        this.runtimeTenantId = runtimeTenantId;
        this.runtimeDeploymentId = runtimeDeploymentId;
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalStateException("Smart Brain operation retention must be positive.");
        }
    }

    public SmartBrainOperationView submitScheduled(
        String scheduleCode,
        String triggerCode,
        Instant scheduledFireTime
    ) {
        if (!StringUtils.hasText(runtimeTenantId) || !StringUtils.hasText(runtimeDeploymentId)) {
            throw new IllegalStateException("Scheduled Smart Brain execution requires deployment-owned tenant context.");
        }
        SmartBrainRuntimeConfig.Trigger trigger = configurationService.requireTrigger(triggerCode);
        Instant effectiveTime = scheduledFireTime == null ? Instant.now() : scheduledFireTime;
        ObjectNode event = objectMapper.createObjectNode();
        event.put("specversion", "1.0");
        event.put("id", "schedule-" + scheduleCode + "-" + effectiveTime.toEpochMilli());
        event.put(
            "source",
            StringUtils.hasText(acceptedRuntimeUrl)
                ? acceptedRuntimeUrl + "/smart-brain/schedules/" + scheduleCode
                : "urn:loomai:smart-brain:schedule:" + scheduleCode
        );
        event.put("type", trigger.eventTypes().get(0));
        event.put("subject", scheduleCode);
        event.put("time", effectiveTime.toString());
        ObjectNode data = event.putObject("data");
        data.put("scheduleCode", scheduleCode);
        data.put("scheduledFor", effectiveTime.toString());
        RuntimeAuthContext auth = RuntimeAuthContext.builder()
            .subjectId("smart-brain-scheduler")
            .subjectType(RuntimeAuthSubjectType.SYSTEM_PROCESS)
            .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .callerType(RuntimeAuthCallerType.SYSTEM_PROCESS)
            .tenantId(runtimeTenantId)
            .deploymentId(runtimeDeploymentId)
            .grantedScopes(List.of())
            .build();
        RuntimeResolvedIdentity identity = RuntimeResolvedIdentity.builder()
            .authContext(auth)
            .warnings(List.of())
            .build();
        return submit(
            identity,
            triggerCode,
            event,
            "schedule:" + scheduleCode + ":" + effectiveTime.toEpochMilli()
        );
    }

    public SmartBrainOperationView submit(
        RuntimeResolvedIdentity identity,
        String triggerCode,
        String cloudEventJson,
        String idempotencyKey
    ) {
        try {
            return submit(identity, triggerCode, objectMapper.readTree(cloudEventJson), idempotencyKey);
        } catch (SmartBrainRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent body must be valid JSON.");
        }
    }

    public SmartBrainOperationView submit(
        RuntimeResolvedIdentity identity,
        String triggerCode,
        JsonNode cloudEvent,
        String idempotencyKey
    ) {
        RuntimeAuthContext auth = requireTrustedApplication(identity);
        SmartBrainRuntimeConfig.Trigger trigger = configurationService.requireTrigger(triggerCode);
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        validateCloudEvent(trigger, cloudEvent);
        String canonicalRequest = canonicalJson.write(requestFingerprintNode(triggerCode, cloudEvent));
        String fingerprint = security.fingerprint(canonicalRequest);

        Optional<SmartBrainOperationEntity> existing = operationRepository
            .findByTenantIdAndDeploymentIdAndTriggerCodeAndIdempotencyKey(
                auth.getTenantId(),
                auth.getDeploymentId(),
                triggerCode,
                normalizedKey
            );
        if (existing.isPresent()) {
            return replayView(existing.get(), fingerprint);
        }

        SmartBrainProtectedRequest protectedRequest = new SmartBrainProtectedRequest(
            cloudEvent.deepCopy(),
            principalId(auth),
            principalType(auth).name(),
            ExecutionSource.APPLICATION.name()
        );
        Instant now = Instant.now();
        SmartBrainOperationEntity operation = new SmartBrainOperationEntity();
        operation.setOperationId("sbo-" + UUID.randomUUID());
        operation.setTenantId(auth.getTenantId());
        operation.setDeploymentId(auth.getDeploymentId());
        operation.setTriggerCode(triggerCode);
        operation.setCloudEventId(cloudEvent.path("id").asText());
        operation.setCloudEventType(cloudEvent.path("type").asText());
        operation.setCloudEventSource(cloudEvent.path("source").asText());
        operation.setIdempotencyKey(normalizedKey);
        operation.setRequestFingerprint(fingerprint);
        operation.setProtectedRequest(security.encrypt(write(protectedRequest)));
        operation.setStatus("ACCEPTED");
        operation.setAcceptedRuntimeUrl(acceptedRuntimeUrl);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        operation.setExpiresAt(now.plus(retention));
        try {
            operationRepository.saveAndFlush(operation);
            return toView(operation, false);
        } catch (DataIntegrityViolationException conflict) {
            SmartBrainOperationEntity raced = operationRepository
                .findByTenantIdAndDeploymentIdAndTriggerCodeAndIdempotencyKey(
                    auth.getTenantId(), auth.getDeploymentId(), triggerCode, normalizedKey
                )
                .orElseThrow(() -> conflict);
            return replayView(raced, fingerprint);
        }
    }

    public SmartBrainOperationView status(RuntimeResolvedIdentity identity, String operationId) {
        RuntimeAuthContext auth = requireTrustedIdentity(identity);
        return toView(requireOwned(auth, operationId), false);
    }

    public SmartBrainOperationView cancel(RuntimeResolvedIdentity identity, String operationId) {
        RuntimeAuthContext auth = requireTrustedApplication(identity);
        SmartBrainOperationEntity operation = requireOwned(auth, operationId);
        if (TERMINAL_STATUSES.contains(operation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Smart Brain operation is already terminal.");
        }
        if (StringUtils.hasText(operation.getInvocationId())) {
            specialistClient.cancel(operation.getInvocationId(), trustedContext(operation));
        }
        operation.setStatus("CANCELLED");
        operation.setCompletedAt(Instant.now());
        operation.setUpdatedAt(operation.getCompletedAt());
        return toView(operationRepository.save(operation), false);
    }

    public SmartBrainOperationView replay(RuntimeResolvedIdentity identity, String operationId) {
        RuntimeAuthContext auth = requireTrustedApplication(identity);
        SmartBrainOperationEntity operation = requireOwned(auth, operationId);
        if (!StringUtils.hasText(operation.getInvocationId())) {
            process(operation);
            operation = requireOwned(auth, operationId);
        } else {
            SmartBrainProtectedRequest request = protectedRequest(operation);
            ExecutionHandle handle = specialistClient.submit(invocation(operation, request));
            if (!operation.getInvocationId().equals(handle.invocationId())) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stored Smart Brain operation no longer replays the same protected execution."
                );
            }
        }
        return toView(operation, true);
    }

    public List<SmartBrainOperationEntity> readyForProcessing() {
        return operationRepository.findTop50ByStatusInOrderByCreatedAtAsc(ACTIVE_STATUSES);
    }

    public void process(SmartBrainOperationEntity operation) {
        if (operation == null || TERMINAL_STATUSES.contains(operation.getStatus())) {
            return;
        }
        SmartBrainProtectedRequest request = protectedRequest(operation);
        TrustedExecutionContext context = trustedContext(operation, request);
        try {
            if (!StringUtils.hasText(operation.getInvocationId())) {
                ExecutionHandle handle = specialistClient.submit(invocation(operation, request));
                operation.setInvocationId(handle.invocationId());
                operation.setStatus(map(handle.status()));
                if (handle.failureReason() != null) {
                    fail(operation, "AI_FABRIC_SUBMISSION_REJECTED", handle.failureReason());
                }
                operation.setUpdatedAt(Instant.now());
                operationRepository.saveAndFlush(operation);
                if (isActive(handle.status())) {
                    return;
                }
            }
            SpecialistExecutionSnapshot<JsonNode> snapshot = specialistClient.find(
                operation.getInvocationId(),
                context
            ).orElseThrow(() -> new IllegalStateException("Durable AI Fabric invocation was not found."));
            operation.setStatus(map(snapshot.handle().status()));
            operation.setUpdatedAt(Instant.now());
            if (snapshot.result() != null) {
                if (snapshot.result().status() == AIExecutionStatus.SUCCEEDED) {
                    operation.setStatus("SUCCEEDED");
                    operation.setProtectedResult(security.encrypt(canonicalJson.write(snapshot.result().output())));
                    operation.setCompletedAt(snapshot.result().completedAt());
                    operation.setFailureCode(null);
                    operation.setFailureMessage(null);
                } else {
                    String reason = snapshot.result().failure() == null
                        ? snapshot.result().status().name()
                        : snapshot.result().failure().reason();
                    String message = snapshot.result().failure() == null
                        ? "Smart Brain analysis did not complete successfully."
                        : snapshot.result().failure().publicMessage();
                    fail(operation, reason, message);
                }
            } else if (isTerminal(snapshot.handle().status())) {
                fail(
                    operation,
                    "AI_FABRIC_EXECUTION_" + snapshot.handle().status().name(),
                    snapshot.handle().failureReason() == null
                        ? "Smart Brain analysis did not complete successfully."
                        : snapshot.handle().failureReason()
                );
            }
            operationRepository.saveAndFlush(operation);
            if (TERMINAL_STATUSES.contains(operation.getStatus())) {
                deliveryService.enqueue(operation);
            }
        } catch (RuntimeException exception) {
            Instant failedAt = Instant.now();
            operation.setStatus("FAILED");
            operation.setUpdatedAt(failedAt);
            operation.setCompletedAt(failedAt);
            operation.setFailureCode("SMART_BRAIN_PROCESSING_FAILED");
            operation.setFailureMessage(bounded(exception.getMessage(), "Smart Brain processing failed."));
            operationRepository.saveAndFlush(operation);
            deliveryService.enqueue(operation);
        }
    }

    public SmartBrainOperationView toView(SmartBrainOperationEntity operation, boolean replayed) {
        JsonNode result = null;
        if (StringUtils.hasText(operation.getProtectedResult())) {
            try {
                result = objectMapper.readTree(security.decrypt(operation.getProtectedResult()));
            } catch (Exception exception) {
                throw new IllegalStateException("Stored Smart Brain result could not be read.", exception);
            }
        }
        SmartBrainOperationView.Failure failure = StringUtils.hasText(operation.getFailureCode())
            ? new SmartBrainOperationView.Failure(operation.getFailureCode(), operation.getFailureMessage())
            : null;
        return new SmartBrainOperationView(
            operation.getOperationId(),
            operation.getTriggerCode(),
            operation.getCloudEventId(),
            operation.getCloudEventType(),
            operation.getStatus(),
            operationUrl(operation),
            result,
            failure,
            operation.getCreatedAt(),
            operation.getUpdatedAt(),
            operation.getCompletedAt(),
            operation.getExpiresAt(),
            replayed
        );
    }

    private SpecialistInvocation<JsonNode> invocation(
        SmartBrainOperationEntity operation,
        SmartBrainProtectedRequest request
    ) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("triggerCode", operation.getTriggerCode());
        input.set("event", request.cloudEvent());
        return new SpecialistInvocation<>(
            input,
            trustedContext(operation, request),
            null,
            null,
            "smart-brain:" + operation.getOperationId()
        );
    }

    private TrustedExecutionContext trustedContext(SmartBrainOperationEntity operation) {
        return trustedContext(operation, protectedRequest(operation));
    }

    private TrustedExecutionContext trustedContext(
        SmartBrainOperationEntity operation,
        SmartBrainProtectedRequest request
    ) {
        return new TrustedExecutionContext(
            new ExecutionPrincipal(
                request.principalId(),
                ExecutionPrincipalType.valueOf(request.principalType())
            ),
            new ExecutionSubjectRef("deployment", operation.getDeploymentId()),
            ExecutionSource.valueOf(request.executionSource()),
            operation.getTenantId(),
            operation.getDeploymentId(),
            INTERNAL_SCOPES,
            null,
            operation.getCreatedAt()
        );
    }

    private SmartBrainProtectedRequest protectedRequest(SmartBrainOperationEntity operation) {
        try {
            return objectMapper.readValue(
                security.decrypt(operation.getProtectedRequest()),
                SmartBrainProtectedRequest.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Stored Smart Brain request could not be read.", exception);
        }
    }

    private RuntimeAuthContext requireTrustedApplication(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext auth = requireTrustedIdentity(identity);
        if (auth.getCallerType() == RuntimeAuthCallerType.PUBLIC_BROWSER) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Smart Brain triggers require backend or service authentication."
            );
        }
        return auth;
    }

    private RuntimeAuthContext requireTrustedIdentity(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext auth = identity == null ? null : identity.getAuthContext();
        if (auth == null || !StringUtils.hasText(auth.getTenantId()) || !StringUtils.hasText(auth.getDeploymentId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Verified runtime tenant and deployment context are required."
            );
        }
        return auth;
    }

    private SmartBrainOperationEntity requireOwned(RuntimeAuthContext auth, String operationId) {
        if (!StringUtils.hasText(operationId) || operationId.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid operation ID is required.");
        }
        return operationRepository.findByOperationIdAndTenantIdAndDeploymentId(
            operationId.trim(), auth.getTenantId(), auth.getDeploymentId()
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Smart Brain operation was not found."));
    }

    private SmartBrainOperationView replayView(SmartBrainOperationEntity operation, String fingerprint) {
        if (!operation.getRequestFingerprint().equals(fingerprint)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Idempotency-Key was already used with a different Smart Brain event."
            );
        }
        return toView(operation, true);
    }

    private void validateCloudEvent(SmartBrainRuntimeConfig.Trigger trigger, JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent must be a JSON object.");
        }
        int bytes = canonicalJson.write(event).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > configurationService.current().maxEventBytes()) {
            throw new SmartBrainRequestException("CLOUD_EVENT_TOO_LARGE", "CloudEvent exceeds the deployment limit.");
        }
        requiredEventField(event, "id", 200);
        String type = requiredEventField(event, "type", 200);
        String source = requiredEventField(event, "source", 500);
        if (!"1.0".equals(event.path("specversion").asText(""))) {
            throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent specversion must be 1.0.");
        }
        try {
            URI parsed = URI.create(source);
            if (!parsed.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent source must be an absolute URI.");
        }
        if (!trigger.eventTypes().contains(type)) {
            throw new SmartBrainRequestException(
                "CLOUD_EVENT_TYPE_NOT_ALLOWED",
                "CloudEvent type is not allowed for this trigger."
            );
        }
        if (event.has("time")) {
            try {
                OffsetDateTime.parse(event.path("time").asText());
            } catch (RuntimeException exception) {
                throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent time must be RFC 3339.");
            }
        }
        if (!event.has("data")) {
            throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent data is required.");
        }
    }

    private String requiredEventField(JsonNode event, String field, int maxLength) {
        String value = event.path(field).asText("").trim();
        if (!StringUtils.hasText(value) || value.length() > maxLength) {
            throw new SmartBrainRequestException("CLOUD_EVENT_INVALID", "CloudEvent " + field + " is invalid.");
        }
        return value;
    }

    private ObjectNode requestFingerprintNode(String triggerCode, JsonNode event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("triggerCode", triggerCode);
        node.set("cloudEvent", event);
        return node;
    }

    private String principalId(RuntimeAuthContext auth) {
        return StringUtils.hasText(auth.getSubjectId()) ? auth.getSubjectId().trim() : "loomai-runtime-service";
    }

    private ExecutionPrincipalType principalType(RuntimeAuthContext auth) {
        return auth.getCallerType() == RuntimeAuthCallerType.PUBLIC_BROWSER
            ? ExecutionPrincipalType.END_USER
            : ExecutionPrincipalType.SERVICE;
    }

    private String requireIdempotencyKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required and must not exceed 160 characters.");
        }
        return value.trim();
    }

    private String operationUrl(SmartBrainOperationEntity operation) {
        String path = "/api/smart-brain/v1/operations/" + operation.getOperationId();
        return StringUtils.hasText(operation.getAcceptedRuntimeUrl())
            ? operation.getAcceptedRuntimeUrl() + path
            : path;
    }

    private String map(ExecutionHandleStatus status) {
        return switch (status) {
            case QUEUED -> "QUEUED";
            case RUNNING, WAITING_FOR_INPUT -> "RUNNING";
            case SUCCEEDED -> "SUCCEEDED";
            case CANCELLED -> "CANCELLED";
            case EXPIRED -> "EXPIRED";
            case FAILED, REJECTED -> "FAILED";
        };
    }

    private boolean isActive(ExecutionHandleStatus status) {
        return status == ExecutionHandleStatus.QUEUED
            || status == ExecutionHandleStatus.RUNNING
            || status == ExecutionHandleStatus.WAITING_FOR_INPUT;
    }

    private boolean isTerminal(ExecutionHandleStatus status) {
        return !isActive(status);
    }

    private void fail(SmartBrainOperationEntity operation, String code, String message) {
        operation.setStatus("FAILED");
        operation.setFailureCode(bounded(code, "SMART_BRAIN_EXECUTION_FAILED"));
        operation.setFailureMessage(bounded(message, "Smart Brain execution failed."));
        operation.setCompletedAt(Instant.now());
        operation.setUpdatedAt(operation.getCompletedAt());
    }

    private String bounded(String value, String fallback) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        return resolved.length() <= 500 ? resolved : resolved.substring(0, 500);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Smart Brain protected request could not be serialized.", exception);
        }
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }
}
