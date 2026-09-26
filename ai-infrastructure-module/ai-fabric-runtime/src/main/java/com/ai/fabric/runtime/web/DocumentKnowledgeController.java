package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.DiscoveryResult;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.OperationResult;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.PreviewResult;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.RefreshResult;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.RegisterSourceCommand;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.RetrievalProof;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.SourceDetail;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService.SourceSummary;
import com.ai.fabric.runtime.documents.DocumentSourceConnector.ConnectorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@ConditionalOnProperty(prefix = "loomai.documents", name = "enabled", havingValue = "true")
public class DocumentKnowledgeController {

    private final RuntimeRequestAuthResolver authResolver;
    private final DocumentKnowledgeService service;

    public DocumentKnowledgeController(
        RuntimeRequestAuthResolver authResolver,
        DocumentKnowledgeService service
    ) {
        this.authResolver = authResolver;
        this.service = service;
    }

    @GetMapping("/source-connector/status")
    public ConnectorStatus connectorStatus(HttpServletRequest request) {
        return service.connectorStatus(authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document connector status"));
    }

    @PostMapping("/sources/discover")
    public DiscoveryResult discover(
        @Valid @RequestBody DiscoverRequest body,
        HttpServletRequest request
    ) {
        return service.discover(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document source discovery"),
            body.datasetId(),
            body.cursor(),
            body.limit() == null ? 0 : body.limit()
        );
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public SourceSummary register(
        @Valid @RequestBody RegisterSourceRequest body,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return service.register(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_REGISTER, "document source registration"),
            new RegisterSourceCommand(body.datasetId(), body.objectReference(), body.visibility(), body.metadata()),
            idempotencyKey
        );
    }

    @GetMapping("/sources")
    public List<SourceSummary> list(HttpServletRequest request) {
        return service.list(authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document source list"));
    }

    @GetMapping("/sources/{sourceId}")
    public SourceDetail detail(@PathVariable String sourceId, HttpServletRequest request) {
        return service.detail(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document source detail"),
            sourceId
        );
    }

    @PostMapping("/sources/{sourceId}/refresh")
    public RefreshResult refresh(
        @PathVariable String sourceId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return service.refresh(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document source refresh"),
            sourceId,
            idempotencyKey
        );
    }

    @GetMapping("/sources/{sourceId}/preview")
    public PreviewResult preview(@PathVariable String sourceId, HttpServletRequest request) {
        return service.preview(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document source preview"),
            sourceId
        );
    }

    @PostMapping("/sources/{sourceId}/index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperationResult index(
        @PathVariable String sourceId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return service.index(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document source indexing"),
            sourceId,
            idempotencyKey
        );
    }

    @PostMapping("/sources/{sourceId}/reconcile")
    public OperationResult reconcile(
        @PathVariable String sourceId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return service.reconcile(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document source reconciliation"),
            sourceId,
            idempotencyKey
        );
    }

    @DeleteMapping("/sources/{sourceId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperationResult removeIndex(
        @PathVariable String sourceId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        return service.removeIndex(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_DELETE_INDEX, "document index removal"),
            sourceId,
            idempotencyKey
        );
    }

    @PostMapping("/retrieval-proof")
    public RetrievalProof retrievalProof(
        @Valid @RequestBody RetrievalProofRequest body,
        HttpServletRequest request
    ) {
        return service.retrievalProof(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document retrieval proof"),
            body.query(),
            body.limit() == null ? 5 : body.limit()
        );
    }

    @GetMapping("/retention")
    public DocumentKnowledgeService.RetentionStatus retentionStatus(HttpServletRequest request) {
        return service.retentionStatus(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_READ, "document retention status")
        );
    }

    @PostMapping("/retention/cleanup")
    public DocumentKnowledgeService.RetentionCleanupResult cleanupRetention(HttpServletRequest request) {
        return service.cleanupRetention(
            authorize(request, RuntimeScopeCatalog.DOCUMENTS_INDEX, "document retention cleanup")
        );
    }

    private RuntimeResolvedIdentity authorize(HttpServletRequest request, String scope, String surface) {
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedPrivateContext(request, surface);
        authResolver.requireScope(identity, scope, surface);
        return identity;
    }

    public record DiscoverRequest(
        @NotBlank @Size(max = 128) String datasetId,
        @Size(max = 2048) String cursor,
        @Min(1) @Max(200) Integer limit
    ) { }

    public record RegisterSourceRequest(
        @NotBlank @Size(max = 128) String datasetId,
        @NotBlank @Size(max = 1024) String objectReference,
        @Size(max = 64) String visibility,
        @Size(max = 16) Map<String, Object> metadata
    ) { }

    public record RetrievalProofRequest(
        @NotBlank @Size(max = 1000) String query,
        @Min(1) @Max(20) Integer limit
    ) { }
}
