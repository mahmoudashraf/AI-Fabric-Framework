package com.ai.fabric.runtime.web;

import ai.fabric.datasync.dto.DataSyncBatchRequest;
import ai.fabric.datasync.dto.DataSyncBatchResponse;
import ai.fabric.datasync.dto.DataSyncOperation;
import ai.fabric.datasync.dto.DataSyncOperationType;
import ai.fabric.datasync.dto.DataSyncTrace;
import ai.fabric.datasync.dto.DataSyncVerifiedAuthContext;
import ai.fabric.datasync.service.DataSyncService;
import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkStatus;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Deployment-local ingestion surface for explicitly authorized customer backends.
 */
@RestController
@RequestMapping("/api/private/ingestion")
public class RuntimeCustomerIngestionController {

    private static final String UPSERT_SCOPE = "data-sync:upsert";
    private static final String DELETE_SCOPE = "data-sync:delete";
    private static final String INDEX_OVERVIEW_SCOPE = "runtime:index:overview";
    private static final String DATA_SYNC_SUBJECT = "system:platform-customer-ingestion";
    private static final String DATA_SYNC_ISSUER = "platform-customer-ingestion";

    private final RuntimeRequestAuthResolver authResolver;
    private final RuntimeAuthProperties authProperties;
    private final ObjectProvider<DataSyncService> dataSyncServiceProvider;
    private final ObjectProvider<IndexingWorkQuery> workQueryProvider;

    public RuntimeCustomerIngestionController(
        RuntimeRequestAuthResolver authResolver,
        RuntimeAuthProperties authProperties,
        ObjectProvider<DataSyncService> dataSyncServiceProvider,
        ObjectProvider<IndexingWorkQuery> workQueryProvider
    ) {
        this.authResolver = authResolver;
        this.authProperties = authProperties;
        this.dataSyncServiceProvider = dataSyncServiceProvider;
        this.workQueryProvider = workQueryProvider;
    }

    @PostMapping("/data-sync/batch")
    public ResponseEntity<?> batch(
        @Valid @RequestBody DataSyncBatchRequest request,
        HttpServletRequest servletRequest
    ) {
        RuntimeResolvedIdentity identity = authorize(servletRequest, "customer ingestion batch");
        validateOperations(identity, request.getOperations());
        DataSyncService service = dataSyncServiceProvider.getIfAvailable();
        if (service == null) {
            return unavailable("DATA_SYNC_UNAVAILABLE", "Runtime Data Sync is unavailable.");
        }
        request.setTrace(serverOwnedTrace(identity.getAuthContext(), request.getTrace(), request.getOperations()));
        DataSyncBatchResponse response = service.batch(request);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(dataSyncStatus(response.getErrorCode())).body(response);
    }

    @GetMapping("/indexing/work/{workId}")
    public ResponseEntity<?> work(
        @PathVariable String workId,
        HttpServletRequest request
    ) {
        RuntimeAuthProperties.CustomerIngestion config = config();
        if (!config.isWorkStatusEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer indexing-work status is not enabled.");
        }
        RuntimeResolvedIdentity identity = authorize(request, "customer indexing-work status");
        authResolver.requireScope(identity, INDEX_OVERVIEW_SCOPE, "customer indexing-work status");
        IndexingWorkQuery query = workQueryProvider.getIfAvailable();
        if (query == null) {
            return unavailable(
                "INDEXING_WORK_STATUS_UNAVAILABLE",
                "Indexing work status is unavailable."
            );
        }
        IndexingWorkStatus work;
        try {
            work = query.findByWorkId(workId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "errorCode", "INVALID_INDEXING_WORK_ID",
                "message", "workId is invalid."
            ));
        }
        if (work == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "errorCode", "INDEXING_WORK_NOT_FOUND",
                "message", "Indexing work was not found."
            ));
        }
        Set<String> allowedEntityTypes = normalizedValues(config.getAllowedWorkStatusEntityTypes());
        if (!allowedEntityTypes.contains(trim(work.entityType()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "errorCode", "INDEXING_WORK_NOT_FOUND",
                "message", "Indexing work was not found."
            ));
        }
        return ResponseEntity.ok(workProjection(work));
    }

    @GetMapping("/readiness")
    public ResponseEntity<?> readiness(HttpServletRequest request) {
        RuntimeAuthProperties.CustomerIngestion config = config();
        if (!config.isReadinessEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer ingestion readiness is not enabled.");
        }
        RuntimeResolvedIdentity identity = authorize(request, "customer ingestion readiness");
        authResolver.requireScope(identity, INDEX_OVERVIEW_SCOPE, "customer ingestion readiness");
        boolean dataSyncAvailable = dataSyncServiceProvider.getIfAvailable() != null;
        boolean workStatusAvailable = workQueryProvider.getIfAvailable() != null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", dataSyncAvailable);
        body.put("status", dataSyncAvailable ? "READY" : "NOT_READY");
        body.put("dataSyncAvailable", dataSyncAvailable);
        body.put("workStatusAvailable", workStatusAvailable);
        body.put("deploymentId", config.getDeploymentId());
        body.put("tenantId", config.getTenantId());
        body.put("upsertEntityTypes", normalizedValues(config.getAllowedUpsertEntityTypes()));
        body.put("deleteEntityTypes", normalizedValues(config.getAllowedDeleteEntityTypes()));
        body.put("workStatusEntityTypes", normalizedValues(config.getAllowedWorkStatusEntityTypes()));
        return ResponseEntity.status(dataSyncAvailable ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private RuntimeResolvedIdentity authorize(HttpServletRequest request, String surface) {
        RuntimeAuthProperties.CustomerIngestion config = config();
        if (!config.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer backend ingestion is not enabled.");
        }
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedPrivateContext(request, surface);
        RuntimeAuthContext context = identity.getAuthContext();
        if (context == null
            || !Objects.equals(trim(config.getDeploymentId()), trim(context.getDeploymentId()))
            || !Objects.equals(trim(config.getTenantId()), trim(context.getTenantId()))) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Runtime auth context does not own this deployment ingestion surface."
            );
        }
        return identity;
    }

    private void validateOperations(RuntimeResolvedIdentity identity, List<DataSyncOperation> operations) {
        RuntimeAuthProperties.CustomerIngestion config = config();
        Set<String> allowedUpserts = normalizedValues(config.getAllowedUpsertEntityTypes());
        Set<String> allowedDeletes = normalizedValues(config.getAllowedDeleteEntityTypes());
        for (DataSyncOperation operation : operations) {
            DataSyncOperationType type = operation.getType();
            String vectorSpace = trim(operation.getVectorSpace());
            if (type == DataSyncOperationType.UPSERT) {
                authResolver.requireScope(identity, UPSERT_SCOPE, "customer ingestion upsert");
                requireAllowedEntityType(vectorSpace, allowedUpserts, "UPSERT");
            } else if (type == DataSyncOperationType.DELETE) {
                authResolver.requireScope(identity, DELETE_SCOPE, "customer ingestion delete");
                requireAllowedEntityType(vectorSpace, allowedDeletes, "DELETE");
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Data Sync operation type.");
            }
        }
    }

    private void requireAllowedEntityType(String entityType, Set<String> allowed, String operation) {
        if (!StringUtils.hasText(entityType) || !allowed.contains(entityType)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                operation + " is not enabled for entity type " + (entityType == null ? "" : entityType) + "."
            );
        }
    }

    private DataSyncTrace serverOwnedTrace(RuntimeAuthContext context,
                                           DataSyncTrace supplied,
                                           List<DataSyncOperation> operations) {
        DataSyncTrace trace = new DataSyncTrace();
        trace.setRequestId(requestId(supplied));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "customer-backend-ingestion");
        metadata.put(
            "verifiedCallerSubjectType",
            context.getSubjectType() == null ? null : context.getSubjectType().name()
        );
        metadata.put("verifiedCallerIssuer", context.getIssuer());
        metadata.values().removeIf(Objects::isNull);
        trace.setMetadata(Map.copyOf(metadata));

        DataSyncVerifiedAuthContext auth = new DataSyncVerifiedAuthContext();
        auth.setSubjectId(DATA_SYNC_SUBJECT);
        auth.setSubjectType("SYSTEM_PROCESS");
        auth.setAuthMode("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        auth.setCallerType("SYSTEM_PROCESS");
        auth.setDeploymentId(context.getDeploymentId());
        auth.setCustomerId(context.getCustomerId());
        auth.setTenantId(context.getTenantId());
        auth.setIssuer(DATA_SYNC_ISSUER);
        auth.setGrantedScopes(requiredDataSyncScopes(operations));
        trace.setAuthContext(auth);
        return trace;
    }

    private List<String> requiredDataSyncScopes(List<DataSyncOperation> operations) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        for (DataSyncOperation operation : operations) {
            if (operation.getType() == DataSyncOperationType.UPSERT) {
                scopes.add(UPSERT_SCOPE);
            } else if (operation.getType() == DataSyncOperationType.DELETE) {
                scopes.add(DELETE_SCOPE);
            }
        }
        return List.copyOf(scopes);
    }

    private Map<String, Object> workProjection(IndexingWorkStatus work) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("workId", work.workId());
        body.put("status", work.status().name());
        body.put("terminal", work.isTerminal());
        body.put("successfulTerminal", work.isSuccessfulTerminal());
        body.put("requiresOperatorReview", work.requiresOperatorReview());
        body.put("entityType", work.entityType());
        body.put("entityId", work.entityId());
        body.put("operation", work.sourceOperation() == null ? null : work.sourceOperation().name());
        body.put("retryCount", work.retryCount());
        body.put("maxRetries", work.maxRetries());
        body.put("errorCode", work.errorCode());
        body.values().removeIf(Objects::isNull);
        return body;
    }

    private ResponseEntity<?> unavailable(String errorCode, String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "success", false,
            "errorCode", errorCode,
            "message", message
        ));
    }

    private String requestId(DataSyncTrace supplied) {
        String candidate = supplied != null ? trim(supplied.getRequestId()) : null;
        return candidate != null && candidate.matches("[A-Za-z0-9._:-]{1,160}")
            ? candidate
            : "customer-ingestion-" + UUID.randomUUID();
    }

    private HttpStatus dataSyncStatus(String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return HttpStatus.OK;
        }
        return switch (errorCode.trim()) {
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "VECTOR_SPACE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_REQUEST", "BATCH_TOO_LARGE", "VECTOR_SPACE_NOT_INDEXABLE", "PROJECTION_REJECTED" ->
                HttpStatus.BAD_REQUEST;
            case "INDEXING_RETRYABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private RuntimeAuthProperties.CustomerIngestion config() {
        RuntimeAuthProperties.CustomerIngestion config = authProperties.getIngress().getCustomerIngestion();
        return config == null ? new RuntimeAuthProperties.CustomerIngestion() : config;
    }

    private Set<String> normalizedValues(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                .map(this::trim)
                .filter(StringUtils::hasText)
                .forEach(normalized::add);
        }
        return Set.copyOf(normalized);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
