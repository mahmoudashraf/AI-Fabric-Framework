package com.ai.fabric.runtime.web.admin;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.dto.VectorScanPage;
import ai.fabric.dto.VectorScanRequest;
import ai.fabric.indexing.IndexingStatus;
import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkStatus;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.repository.IndexingQueueRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/indexing")
@RequiredArgsConstructor
public class VectorIndexAdminController {

    private final VectorDatabaseService vectorDatabaseService;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final RuntimeRequestAuthResolver runtimeRequestAuthResolver;
    private final ObjectProvider<IndexingWorkQuery> indexingWorkQueryProvider;
    private final ObjectProvider<IndexingQueueRepository> indexingQueueRepositoryProvider;

    /**
     * Lightweight status endpoint so you can confirm indexing is happening.
     */
    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest httpRequest) {
        authorize(httpRequest, RuntimeAdminScopeCatalog.RUNTIME_INDEXING_OVERVIEW, "/api/admin/indexing/overview");

        Set<String> entityTypes = entityConfigurationLoader != null
            ? entityConfigurationLoader.getSupportedEntityTypes()
            : Set.of();

        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (String entityType : entityTypes) {
            if (!StringUtils.hasText(entityType)) {
                continue;
            }
            long count = vectorDatabaseService.getVectorCountByEntityType(entityType);
            counts.put(entityType, count);
            total += count;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("vectorDb", vectorDatabaseService.getClass().getSimpleName());
        body.put(
            "supportsVectorScan",
            vectorDatabaseService.supportsVectorScan()
        );
        body.put("entityTypes", entityTypes);
        body.put("countsByEntityType", counts);
        body.put("totalVectors", total);
        body.put("queue", queueDiagnostics());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/work/{workId}")
    public ResponseEntity<?> workStatus(
        @PathVariable("workId") String workId,
        HttpServletRequest httpRequest
    ) {
        String surface = "/api/admin/indexing/work/{workId}";
        authorize(
            httpRequest,
            RuntimeAdminScopeCatalog.RUNTIME_INDEXING_OVERVIEW,
            surface
        );

        IndexingWorkQuery workQuery =
            indexingWorkQueryProvider.getIfAvailable();
        if (workQuery == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                Map.of(
                    "success", false,
                    "errorCode", "INDEXING_WORK_STATUS_UNAVAILABLE",
                    "message", "Indexing work status is unavailable."
                )
            );
        }

        IndexingWorkStatus work;
        try {
            work = workQuery.findByWorkId(workId).orElse(null);
        } catch (IllegalArgumentException ignored) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
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
        body.put("entityType", safeText(work.entityType()));
        body.put("entityId", safeText(work.entityId()));
        body.put(
            "workType",
            work.workType() == null ? null : work.workType().name()
        );
        body.put(
            "sourceOperation",
            work.sourceOperation() == null
                ? null
                : work.sourceOperation().name()
        );
        body.put(
            "strategy",
            work.strategy() == null ? null : work.strategy().name()
        );
        body.put("retryCount", work.retryCount());
        body.put("maxRetries", work.maxRetries());
        body.put("errorCode", safeText(work.errorCode()));
        body.put("deadLetterReason", safeText(work.deadLetterReason()));
        body.put("correlationId", safeText(work.correlationId()));
        body.put("requestedAt", stringValue(work.requestedAt()));
        body.put("scheduledFor", stringValue(work.scheduledFor()));
        body.put("startedAt", stringValue(work.startedAt()));
        body.put("completedAt", stringValue(work.completedAt()));
        body.put("lastErrorAt", stringValue(work.lastErrorAt()));
        body.put("updatedAt", stringValue(work.updatedAt()));
        return ResponseEntity.ok(body);
    }

    /**
     * Paged inspection endpoint.
     *
     * <p>Supports offset-based paging (as requested) and also returns/accepts the framework cursor.</p>
     */
    @GetMapping("/vectors")
    public ResponseEntity<?> vectors(@RequestParam("entityType") String entityType,
                                     @RequestParam(value = "offset", required = false) Integer offset,
                                     @RequestParam(value = "limit", required = false) Integer limit,
                                     @RequestParam(value = "cursor", required = false) String cursor,
                                     @RequestParam(value = "includeContent", required = false) Boolean includeContent,
                                     @RequestParam(value = "includeEmbedding", required = false) Boolean includeEmbedding,
                                     @RequestParam(value = "includeMetadata", required = false) Boolean includeMetadata,
                                     HttpServletRequest httpRequest) {
        authorize(httpRequest, RuntimeAdminScopeCatalog.RUNTIME_INDEXING_VECTORS, "/api/admin/indexing/vectors");
        if (!StringUtils.hasText(entityType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "message", "entityType is required"
            ));
        }

        Integer safeLimit = limit != null ? Math.max(1, Math.min(limit, 500)) : 200;
        String effectiveCursor = StringUtils.hasText(cursor) ? cursor : null;
        if (effectiveCursor == null && offset != null && offset >= 0) {
            effectiveCursor = encodeOffsetCursor(offset);
        }

        VectorScanRequest req = VectorScanRequest.builder()
            .entityType(entityType)
            .limit(safeLimit)
            .cursor(effectiveCursor)
            .includeContent(includeContent == null || includeContent)
            .includeEmbedding(includeEmbedding != null && includeEmbedding)
            .includeMetadata(includeMetadata == null || includeMetadata)
            .build();

        VectorScanPage page = vectorDatabaseService.scan(req);
        Integer nextOffset = decodeOffsetCursor(page != null ? page.getNextCursor() : null);
        int resolvedOffset = offset != null ? offset : (effectiveCursor != null ? decodeOffsetCursor(effectiveCursor) : 0);

        // Use a mutable map so we can safely return nulls (Map.of forbids null values).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("entityType", entityType);
        body.put("limit", safeLimit);
        body.put("cursor", effectiveCursor);
        body.put("nextCursor", page != null ? page.getNextCursor() : null);
        body.put("offset", resolvedOffset);
        body.put("nextOffset", nextOffset);
        body.put("hasMore", page != null && page.isHasMore());
        body.put("vectors", page != null && page.getVectors() != null ? page.getVectors() : java.util.List.of());
        return ResponseEntity.ok(body);
    }

    private static String encodeOffsetCursor(int offset) {
        String raw = "offset:" + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Integer decodeOffsetCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            if (!raw.startsWith("offset:")) {
                return null;
            }
            return Integer.parseInt(raw.substring("offset:".length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value
            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.length() <= 240
            ? normalized
            : normalized.substring(0, 240);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> queueDiagnostics() {
        IndexingQueueRepository repository =
            indexingQueueRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return Map.of(
                "ready",
                false,
                "errorCode",
                "INDEXING_QUEUE_DIAGNOSTICS_UNAVAILABLE"
            );
        }
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("ready", true);
        queue.put(
            "commitPending",
            repository.countByStatus(IndexingStatus.COMMIT_PENDING)
        );
        queue.put(
            "pending",
            repository.countByStatus(IndexingStatus.PENDING)
        );
        queue.put(
            "processing",
            repository.countByStatus(IndexingStatus.PROCESSING)
        );
        queue.put(
            "completed",
            repository.countByStatus(IndexingStatus.COMPLETED)
        );
        queue.put(
            "superseded",
            repository.countByStatus(IndexingStatus.SUPERSEDED)
        );
        queue.put(
            "deadLetters",
            repository.countByStatus(IndexingStatus.DEAD_LETTER)
        );
        return queue;
    }

    private void authorize(HttpServletRequest request, String scope, String surface) {
        runtimeRequestAuthResolver.requireScope(
            runtimeRequestAuthResolver.resolveVerifiedPrivateContext(request, surface),
            scope,
            surface
        );
    }
}
