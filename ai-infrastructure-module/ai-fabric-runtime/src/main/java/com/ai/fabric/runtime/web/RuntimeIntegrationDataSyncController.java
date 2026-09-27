package com.ai.fabric.runtime.web;

import ai.fabric.datasync.dto.DataSyncBatchRequest;
import ai.fabric.datasync.dto.DataSyncBatchResponse;
import ai.fabric.datasync.dto.DataSyncTrace;
import ai.fabric.datasync.dto.DataSyncVerifiedAuthContext;
import ai.fabric.datasync.service.DataSyncService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/integrations/data-sync")
public class RuntimeIntegrationDataSyncController {

    private static final String SUBJECT_ID = "system:platform-deployment-integration";
    private static final String ISSUER = "platform-deployment-integration";

    private final RuntimeRequestAuthResolver authResolver;
    private final RuntimeAuthProperties authProperties;
    private final ObjectProvider<DataSyncService> dataSyncServiceProvider;

    public RuntimeIntegrationDataSyncController(
        RuntimeRequestAuthResolver authResolver,
        RuntimeAuthProperties authProperties,
        ObjectProvider<DataSyncService> dataSyncServiceProvider
    ) {
        this.authResolver = authResolver;
        this.authProperties = authProperties;
        this.dataSyncServiceProvider = dataSyncServiceProvider;
    }

    @PostMapping("/batch")
    public ResponseEntity<?> batch(
        @Valid @RequestBody DataSyncBatchRequest request,
        HttpServletRequest servletRequest
    ) {
        authResolver.requireIntegrationServiceIngress(servletRequest, "integration data-sync batch");
        DataSyncService service = dataSyncServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false,
                "errorCode", "DATA_SYNC_UNAVAILABLE",
                "message", "Runtime Data Sync is unavailable."
            ));
        }
        request.setTrace(serverOwnedTrace(request.getTrace()));
        DataSyncBatchResponse response = service.batch(request);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(status(response.getErrorCode())).body(response);
    }

    private DataSyncTrace serverOwnedTrace(DataSyncTrace supplied) {
        RuntimeAuthProperties.IntegrationService integration = authProperties.getIngress().getIntegrationService();
        DataSyncTrace trace = new DataSyncTrace();
        trace.setRequestId(requestId(supplied));
        trace.setMetadata(Map.of("source", "deployment-integration-connector"));

        DataSyncVerifiedAuthContext auth = new DataSyncVerifiedAuthContext();
        auth.setSubjectId(SUBJECT_ID);
        auth.setSubjectType("SYSTEM_PROCESS");
        auth.setAuthMode("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        auth.setCallerType("SYSTEM_PROCESS");
        auth.setDeploymentId(integration.getDeploymentId().trim());
        auth.setTenantId(integration.getTenantId().trim());
        auth.setIssuer(ISSUER);
        auth.setGrantedScopes(List.of("data-sync:upsert", "data-sync:delete"));
        trace.setAuthContext(auth);
        return trace;
    }

    private String requestId(DataSyncTrace supplied) {
        String candidate = supplied != null && StringUtils.hasText(supplied.getRequestId())
            ? supplied.getRequestId().trim()
            : "";
        return candidate.matches("[A-Za-z0-9._:-]{1,160}")
            ? candidate
            : "integration-sync-" + UUID.randomUUID();
    }

    private HttpStatus status(String errorCode) {
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
}
