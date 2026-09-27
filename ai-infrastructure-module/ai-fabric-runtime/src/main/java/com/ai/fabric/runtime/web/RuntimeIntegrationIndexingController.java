package com.ai.fabric.runtime.web;

import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkStatus;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/integrations/indexing")
public class RuntimeIntegrationIndexingController {

    private final RuntimeRequestAuthResolver authResolver;
    private final ObjectProvider<IndexingWorkQuery> workQueryProvider;

    public RuntimeIntegrationIndexingController(
        RuntimeRequestAuthResolver authResolver,
        ObjectProvider<IndexingWorkQuery> workQueryProvider
    ) {
        this.authResolver = authResolver;
        this.workQueryProvider = workQueryProvider;
    }

    @GetMapping("/work/{workId}")
    public ResponseEntity<?> work(
        @PathVariable String workId,
        HttpServletRequest request
    ) {
        authResolver.requireIntegrationServiceIngress(request, "integration indexing-work read");
        IndexingWorkQuery query = workQueryProvider.getIfAvailable();
        if (query == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false,
                "errorCode", "INDEXING_WORK_STATUS_UNAVAILABLE",
                "message", "Indexing work status is unavailable."
            ));
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("workId", work.workId());
        body.put("status", work.status().name());
        body.put("terminal", work.isTerminal());
        body.put("successfulTerminal", work.isSuccessfulTerminal());
        body.put("requiresOperatorReview", work.requiresOperatorReview());
        body.put("entityType", work.entityType());
        body.put("entityId", work.entityId());
        body.put("operation", work.sourceOperation().name());
        body.put("retryCount", work.retryCount());
        body.put("maxRetries", work.maxRetries());
        body.put("errorCode", work.errorCode());
        body.values().removeIf(java.util.Objects::isNull);
        return ResponseEntity.ok(body);
    }
}
