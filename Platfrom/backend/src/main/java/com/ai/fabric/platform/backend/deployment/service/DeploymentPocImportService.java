package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocImportRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRecordRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRunSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocImportRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentPocImportService {

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    private static final String SOURCE_TYPE = "JSON_UPLOAD";
    private static final String IMPORT_SURFACE_RUNTIME = "runtime";
    public static final int MAX_RECORDS_PER_RUN = 100;
    public static final int MAX_CONTENT_LENGTH = 16_000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentPocImportRunRepository deploymentPocImportRunRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeploymentPocImportService(DeploymentRepository deploymentRepository,
                                      DeploymentPocImportRunRepository deploymentPocImportRunRepository,
                                      DeploymentAccessService deploymentAccessService,
                                      PlatformSecretService platformSecretService,
                                      PlatformAuditService platformAuditService,
                                      ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentPocImportRunRepository = deploymentPocImportRunRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public DeploymentPocImportRunSummary importDataset(String deploymentId, DeploymentPocImportRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        String datasetLabel = normalizeDatasetLabel(request);
        String vectorSpace = normalizeVectorSpace(request);
        List<DeploymentPocImportRecordRequest> records = normalizeRecords(request);
        ImportSurface importSurface = resolveImportSurface(deployment);

        ImportActor actor = currentActor();
        String runId = "pir-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();

        int importedCount = 0;
        int failedCount = records.size();
        String status = "FAILED";
        String errorMessage = null;

        try {
            JsonNode response = sendBatch(importSurface, deployment, vectorSpace, records, actor, runId, datasetLabel);
            importedCount = response.path("succeededOperations").asInt(0);
            failedCount = response.path("failedOperations").asInt(Math.max(records.size() - importedCount, 0));
            boolean success = response.path("success").asBoolean(failedCount == 0);
            status = success && failedCount == 0 ? "SUCCEEDED" : importedCount > 0 ? "PARTIAL" : "FAILED";
            errorMessage = firstNonBlank(textOrNull(response, "message"), textOrNull(response, "errorCode"));
        } catch (ResponseStatusException ex) {
            errorMessage = ex.getReason() == null ? ex.getMessage() : ex.getReason();
        }

        DeploymentPocImportRunEntity entity = new DeploymentPocImportRunEntity();
        entity.setId(runId);
        entity.setDeploymentId(deployment.getId());
        entity.setDatasetLabel(datasetLabel);
        entity.setSourceType(SOURCE_TYPE);
        entity.setVectorSpace(vectorSpace);
        entity.setStatus(status);
        entity.setRecordCount(records.size());
        entity.setImportedCount(importedCount);
        entity.setFailedCount(failedCount);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedByActorId(actor.actorId());
        entity.setCreatedByDisplayName(actor.displayName());
        entity.setCreatedAt(now);
        deploymentPocImportRunRepository.save(entity);

        platformAuditService.record(
            "DEPLOYMENT_POC_IMPORT_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "importRunId", runId,
                "datasetLabel", datasetLabel,
                "vectorSpace", vectorSpace,
                "status", status,
                "recordCount", records.size(),
                "importedCount", importedCount,
                "failedCount", failedCount,
                "transportSurface", importSurface.surfaceKey()
            )
        );

        return toSummary(entity);
    }

    private ImportSurface resolveImportSurface(DeploymentEntity deployment) {
        String runtimeTrustedBackendApiKey = platformSecretService.resolveSecret(RUNTIME_TRUSTED_BACKEND_SECRET_NAME);
        if (StringUtils.hasText(deployment.getRuntimeBaseUrl()) && StringUtils.hasText(runtimeTrustedBackendApiKey)) {
            return new ImportSurface(
                IMPORT_SURFACE_RUNTIME,
                "secured runtime import surface",
                absoluteUri(deployment.getRuntimeBaseUrl(), "/api/ai/data-sync/batch", "runtime"),
                RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER,
                runtimeTrustedBackendApiKey.trim()
            );
        }

        throw new ResponseStatusException(
            BAD_REQUEST,
            "No secured POC import surface is available. Configure runtime URL plus AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY."
        );
    }

    private JsonNode sendBatch(ImportSurface importSurface,
                               DeploymentEntity deployment,
                               String vectorSpace,
                               List<DeploymentPocImportRecordRequest> records,
                               ImportActor actor,
                               String runId,
                               String datasetLabel) {
        URI target = importSurface.target();
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode trace = body.putObject("trace");
        trace.put("requestId", runId);
        trace.set("authContext", buildVerifiedAuthContext(deployment, actor));
        ObjectNode traceMetadata = trace.putObject("metadata");
        traceMetadata.put("datasetLabel", datasetLabel);
        traceMetadata.put("deploymentId", deployment.getId());
        traceMetadata.put("sourceType", SOURCE_TYPE);
        traceMetadata.put("transportSurface", importSurface.surfaceKey());
        traceMetadata.put("identityContract", "VERIFIED_AUTH_CONTEXT_ONLY");

        ArrayNode operations = body.putArray("operations");
        for (DeploymentPocImportRecordRequest record : records) {
            ObjectNode operation = operations.addObject();
            operation.put("type", "UPSERT");
            operation.put("vectorSpace", vectorSpace);
            operation.put("id", record.id().trim());
            if (StringUtils.hasText(record.content())) {
                operation.put("content", record.content().trim());
            }
            if (record.entity() != null && !record.entity().isEmpty()) {
                operation.set("entity", objectMapper.valueToTree(record.entity()));
            }
            Map<String, Object> verifiedMetadata = verifiedRecordMetadata(
                deployment,
                record.metadata()
            );
            if (!verifiedMetadata.isEmpty()) {
                operation.set(
                    "metadata",
                    objectMapper.valueToTree(verifiedMetadata)
                );
            }
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(importSurface.authHeaderName(), importSurface.secretValue())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "POC import request failed with HTTP " + response.statusCode() + "."
                );
            }
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "Failed to reach deployment " + importSurface.surfaceLabel() + ": " + ex.getMessage(),
                ex
            );
        }
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        return deployment;
    }

    private String normalizeDatasetLabel(DeploymentPocImportRequest request) {
        String label = request == null ? null : request.datasetLabel();
        if (!StringUtils.hasText(label)) {
            return "Operator POC import";
        }
        String trimmed = label.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String normalizeVectorSpace(DeploymentPocImportRequest request) {
        if (request == null || !StringUtils.hasText(request.vectorSpace())) {
            throw new ResponseStatusException(BAD_REQUEST, "vectorSpace is required.");
        }
        String vectorSpace = request.vectorSpace().trim();
        if (!vectorSpace.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(BAD_REQUEST, "vectorSpace must use only letters, numbers, hyphen, or underscore.");
        }
        return vectorSpace;
    }

    private List<DeploymentPocImportRecordRequest> normalizeRecords(DeploymentPocImportRequest request) {
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "records must contain at least one item.");
        }
        if (request.records().size() > MAX_RECORDS_PER_RUN) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "POC imports are limited to " + MAX_RECORDS_PER_RUN + " records per run."
            );
        }

        return request.records().stream()
            .map(this::normalizeRecord)
            .toList();
    }

    private DeploymentPocImportRecordRequest normalizeRecord(DeploymentPocImportRecordRequest record) {
        if (record == null || !StringUtils.hasText(record.id())) {
            throw new ResponseStatusException(BAD_REQUEST, "Each import record requires a non-empty id.");
        }
        String id = record.id().trim();
        if (!StringUtils.hasText(record.content()) && (record.entity() == null || record.entity().isEmpty())) {
            throw new ResponseStatusException(BAD_REQUEST, "Each import record requires content or entity fields.");
        }
        String content = StringUtils.hasText(record.content()) ? record.content().trim() : null;
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Record content length must be <= " + MAX_CONTENT_LENGTH + " characters."
            );
        }
        Map<String, Object> entity = record.entity() == null || record.entity().isEmpty()
            ? null
            : new LinkedHashMap<>(record.entity());
        Map<String, Object> metadata = record.metadata() == null || record.metadata().isEmpty()
            ? null
            : new LinkedHashMap<>(record.metadata());
        return new DeploymentPocImportRecordRequest(id, content, entity, metadata);
    }

    private URI absoluteUri(String baseUrl, String path, String surfaceLabel) {
        try {
            URI base = URI.create(baseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
                throw new IllegalArgumentException(surfaceLabel + " base URL must be absolute.");
            }
            return base.resolve(path);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid " + surfaceLabel + " base URL: " + baseUrl);
        }
    }

    private DeploymentPocImportRunSummary toSummary(DeploymentPocImportRunEntity entity) {
        return new DeploymentPocImportRunSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getDatasetLabel(),
            entity.getSourceType(),
            entity.getVectorSpace(),
            entity.getStatus(),
            entity.getRecordCount(),
            entity.getImportedCount(),
            entity.getFailedCount(),
            entity.getErrorMessage(),
            entity.getCreatedByActorId(),
            entity.getCreatedByDisplayName(),
            entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString()
        );
    }

    private String textOrNull(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.path(key);
        if (!child.isTextual()) {
            return null;
        }
        String value = child.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private ImportActor currentActor() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            return new ImportActor(
                "system",
                "System",
                "platform-poc-import-system",
                "platform-poc-import-system-session",
                "SYSTEM_PROCESS",
                "PLATFORM_PROXY_SESSION",
                "SYSTEM_PROCESS",
                "platform-poc-import-system"
            );
        }
        String actorSource = principal.actorId() + "|" + principal.role().name() + "|" + principal.authenticationMode();
        return new ImportActor(
            principal.actorId(),
            principal.displayName(),
            "platform-poc-import-" + shortSha(actorSource),
            "platform-poc-import-session-" + shortSha(actorSource + "|session"),
            "INTERNAL_PLATFORM_USER",
            "PLATFORM_PROXY_SESSION",
            "PLATFORM_PROXY",
            "platform-poc-import"
        );
    }

    private ObjectNode buildVerifiedAuthContext(DeploymentEntity deployment, ImportActor actor) {
        ObjectNode authContext = objectMapper.createObjectNode();
        authContext.put("subjectId", actor.traceUserId());
        authContext.put("subjectType", actor.subjectType());
        authContext.put("authMode", actor.authMode());
        authContext.put("callerType", actor.callerType());
        authContext.put("sessionId", actor.traceSessionId());
        putIfText(authContext, "deploymentId", deployment == null ? null : deployment.getId());
        putIfText(authContext, "customerId", deployment == null ? null : deployment.getCustomerId());
        putIfText(authContext, "tenantId", deployment == null ? null : deployment.getTenantId());
        authContext.put("issuer", actor.issuer());
        ArrayNode scopes = authContext.putArray("grantedScopes");
        scopes.add("data-sync:batch");
        scopes.add("poc:import");
        return authContext;
    }

    private Map<String, Object> verifiedRecordMetadata(
        DeploymentEntity deployment,
        Map<String, Object> suppliedMetadata
    ) {
        Map<String, Object> verifiedMetadata = new LinkedHashMap<>();
        if (suppliedMetadata != null) {
            verifiedMetadata.putAll(suppliedMetadata);
        }
        if (deployment != null) {
            putIfText(
                verifiedMetadata,
                "deploymentId",
                deployment.getId()
            );
            putIfText(
                verifiedMetadata,
                "customerId",
                deployment.getCustomerId()
            );
            putIfText(
                verifiedMetadata,
                "tenantId",
                deployment.getTenantId()
            );
        }
        return Map.copyOf(verifiedMetadata);
    }

    private void putIfText(ObjectNode target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key.trim(), value.trim());
    }

    private void putIfText(
        Map<String, Object> target,
        String key,
        String value
    ) {
        if (target == null
            || !StringUtils.hasText(key)
            || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key.trim(), value.trim());
    }

    private String shortSha(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 6 && i < hashed.length; i++) {
                out.append(String.format("%02x", hashed[i]));
            }
            return out.toString();
        } catch (Exception ex) {
            return "fallback";
        }
    }

    private record ImportActor(
        String actorId,
        String displayName,
        String traceUserId,
        String traceSessionId,
        String subjectType,
        String authMode,
        String callerType,
        String issuer
    ) {
    }

    private record ImportSurface(
        String surfaceKey,
        String surfaceLabel,
        URI target,
        String authHeaderName,
        String secretValue
    ) {
    }
}
