package com.ai.fabric.runtime.documents;

import ai.fabric.core.AICoreService;
import ai.fabric.dto.AISearchRequest;
import ai.fabric.dto.AISearchResponse;
import ai.fabric.entity.IndexingQueueEntry;
import ai.fabric.indexing.api.AIProcessOperation;
import ai.fabric.indexing.api.IndexingStrategy;
import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkStatus;
import ai.fabric.indexing.document.DocumentIndexingQueueAdapter;
import ai.fabric.indexing.document.DocumentQueueSubmissionException;
import ai.fabric.indexing.document.model.DocumentIngestionChunk;
import ai.fabric.indexing.document.model.DocumentIngestionManifest;
import ai.fabric.indexing.document.model.DocumentIngestionPlan;
import ai.fabric.indexing.document.model.DocumentIngestionWarning;
import ai.fabric.indexing.document.model.DocumentManifestChunk;
import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import ai.fabric.indexing.document.springai.SpringAiDocumentIndexingAdapter;
import ai.fabric.indexing.document.springai.SpringAiDocumentIndexingOptions;
import ai.fabric.indexing.document.springai.SpringAiDocumentReaderFactory;
import ai.fabric.indexing.document.springai.SpringAiTrustedResourcePolicy;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.documents.DocumentSourceConnector.DiscoveryPage;
import com.ai.fabric.runtime.documents.DocumentSourceConnector.MaterializedDocument;
import com.ai.fabric.runtime.documents.DocumentSourceConnector.SourceObject;
import com.ai.fabric.runtime.documents.entity.DocumentCommandEntity;
import com.ai.fabric.runtime.documents.entity.DocumentManifestChunkEntity;
import com.ai.fabric.runtime.documents.entity.DocumentManifestEntity;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.entity.DocumentWorkEntity;
import com.ai.fabric.runtime.documents.repository.DocumentCommandRepository;
import com.ai.fabric.runtime.documents.repository.DocumentManifestChunkRepository;
import com.ai.fabric.runtime.documents.repository.DocumentManifestRepository;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import com.ai.fabric.runtime.documents.repository.DocumentWorkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "loomai.documents", name = "enabled", havingValue = "true")
public class DocumentKnowledgeService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Set<String> SYSTEM_METADATA_KEYS = Set.of(
        "tenantId",
        "customerId",
        "deploymentId",
        "datasetId",
        "knowledgeSourceHandleRef"
    );

    private final DocumentKnowledgeProperties properties;
    private final DocumentSourceConnector connector;
    private final DocumentSourceRepository sourceRepository;
    private final DocumentManifestRepository manifestRepository;
    private final DocumentManifestChunkRepository chunkRepository;
    private final DocumentWorkRepository workRepository;
    private final DocumentCommandRepository commandRepository;
    private final SpringAiDocumentReaderFactory readerFactory;
    private final SpringAiDocumentIndexingAdapter planningAdapter;
    private final DocumentIndexingQueueAdapter queueAdapter;
    private final IndexingWorkQuery workQuery;
    private final AICoreService coreService;
    private final DocumentActiveVersionFilter activeVersionFilter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile RetentionCleanupResult lastRetentionCleanup;

    public DocumentKnowledgeService(
        DocumentKnowledgeProperties properties,
        DocumentSourceConnector connector,
        DocumentSourceRepository sourceRepository,
        DocumentManifestRepository manifestRepository,
        DocumentManifestChunkRepository chunkRepository,
        DocumentWorkRepository workRepository,
        DocumentCommandRepository commandRepository,
        SpringAiDocumentReaderFactory readerFactory,
        SpringAiDocumentIndexingAdapter planningAdapter,
        DocumentIndexingQueueAdapter queueAdapter,
        IndexingWorkQuery workQuery,
        AICoreService coreService,
        DocumentActiveVersionFilter activeVersionFilter,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.properties = properties;
        this.connector = connector;
        this.sourceRepository = sourceRepository;
        this.manifestRepository = manifestRepository;
        this.chunkRepository = chunkRepository;
        this.workRepository = workRepository;
        this.commandRepository = commandRepository;
        this.readerFactory = readerFactory;
        this.planningAdapter = planningAdapter;
        this.queueAdapter = queueAdapter;
        this.workQuery = workQuery;
        this.coreService = coreService;
        this.activeVersionFilter = activeVersionFilter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public DocumentSourceConnector.ConnectorStatus connectorStatus(RuntimeResolvedIdentity identity) {
        requireBoundary(identity);
        return connector.status();
    }

    public DiscoveryResult discover(RuntimeResolvedIdentity identity, String datasetId, String cursor, int limit) {
        Boundary boundary = requireBoundary(identity);
        String allowedDatasetId = requireAllowedDataset(datasetId);
        DiscoveryPage page = connector.discover(cursor, limit);
        List<DiscoveredSource> sources = page.objects().stream()
            .map(this::normalizeAndValidate)
            .map(object -> new DiscoveredSource(
                object.objectReference(),
                object.displayName(),
                normalizedMediaType(object),
                object.contentLength(),
                safeProviderRevision(object.providerVersionId()),
                safeProviderRevision(object.etag()),
                object.lastModified(),
                object.providerRevisionFingerprint(),
                sourceRepository.findByLogicalSourceKey(logicalKey(
                    boundary,
                    allowedDatasetId,
                    object.objectReference()
                )).map(DocumentSourceEntity::getId).orElse(null)
            ))
            .toList();
        return new DiscoveryResult(sources, page.nextCursor());
    }

    @Transactional
    public synchronized SourceSummary register(
        RuntimeResolvedIdentity identity,
        RegisterSourceCommand command,
        String idempotencyKey
    ) {
        Boundary boundary = requireBoundary(identity);
        RegisterSourceCommand normalized = normalize(command);
        String requestHash = DocumentConnectorSupport.sha256(
            normalized.datasetId() + "|" + normalized.objectReference() + "|"
                + normalized.visibility() + "|" + writeJson(normalized.metadata())
        );
        boolean claimed = claimCommand(
            boundary,
            "REGISTER",
            normalized.datasetId() + ":" + normalized.objectReference(),
            idempotencyKey,
            requestHash
        );

        SourceObject object = normalizeAndValidate(connector.stat(normalized.objectReference()));
        String logicalKey = logicalKey(boundary, normalized.datasetId(), object.objectReference());
        Optional<DocumentSourceEntity> existing = sourceRepository.findByLogicalSourceKey(logicalKey);
        if (existing.isPresent()) {
            DocumentSourceEntity source = existing.get();
            if (!claimed || source.getStatus() != DocumentSourceEntity.Status.DELETED) {
                return toSummary(source);
            }
            enforceRegistrationLimits(boundary, object.contentLength());
            restoreDeletedRegistration(source, object, normalized);
            return toSummary(sourceRepository.saveAndFlush(source));
        }
        if (!claimed) {
            throw conflict("Idempotent document registration no longer has a matching source record.");
        }
        enforceRegistrationLimits(boundary, object.contentLength());

        Instant now = now();
        DocumentSourceEntity source = new DocumentSourceEntity();
        source.setId("doc-" + logicalKey.substring(0, 32));
        source.setLogicalSourceKey(logicalKey);
        source.setDatasetId(normalized.datasetId());
        source.setTenantId(boundary.tenantId());
        source.setCustomerId(boundary.customerId());
        source.setDeploymentId(boundary.deploymentId());
        source.setConnectorType(connector.type().name());
        source.setConnectorBindingRef(bounded(properties.getConnector().getBindingRef(), 128));
        source.setObjectLocator(object.objectReference());
        source.setObjectLocatorDigest(DocumentConnectorSupport.sha256(object.objectReference()));
        source.setDisplayName(object.displayName());
        source.setMediaType(normalizedMediaType(object));
        source.setProviderVersionId(bounded(object.providerVersionId(), 512));
        source.setProviderEtag(bounded(object.etag(), 256));
        source.setProviderRevisionFingerprint(object.providerRevisionFingerprint());
        source.setContentLength(object.contentLength());
        source.setProviderLastModified(object.lastModified());
        source.setSourceVersion(1);
        source.setVisibility(normalized.visibility());
        source.setMetadataJson(writeJson(normalized.metadata()));
        source.setStatus(DocumentSourceEntity.Status.REGISTERED);
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        return toSummary(sourceRepository.saveAndFlush(source));
    }

    public synchronized List<SourceSummary> list(RuntimeResolvedIdentity identity) {
        Boundary boundary = requireBoundary(identity);
        List<DocumentSourceEntity> sources = sourceRepository
            .findByTenantIdAndDeploymentIdOrderByUpdatedAtDesc(boundary.tenantId(), boundary.deploymentId());
        return sources.stream().map(this::toSummary).toList();
    }

    public synchronized SourceDetail detail(RuntimeResolvedIdentity identity, String sourceId) {
        Boundary boundary = requireBoundary(identity);
        DocumentSourceEntity source = requireSource(boundary, sourceId);
        return new SourceDetail(
            toSummary(source),
            manifestRepository.findBySourceIdOrderBySourceVersionAsc(source.getId()).stream()
                .map(this::toManifestSummary)
                .toList()
        );
    }

    @Transactional
    public synchronized RefreshResult refresh(
        RuntimeResolvedIdentity identity,
        String sourceId,
        String idempotencyKey
    ) {
        Boundary boundary = requireBoundary(identity);
        DocumentSourceEntity source = requireSource(boundary, sourceId);
        if (!claimCommand(boundary, "REFRESH", sourceId, idempotencyKey, sourceId)) {
            return new RefreshResult("IDEMPOTENT_REPLAY", toSummary(source));
        }
        reconcileSource(source, false);
        source = requireSource(boundary, sourceId);
        requireMutable(source);
        SourceObject current = normalizeAndValidate(connector.stat(source.getObjectLocator()));
        if (Objects.equals(current.providerRevisionFingerprint(), source.getProviderRevisionFingerprint())) {
            return new RefreshResult("UNCHANGED", toSummary(source));
        }
        long nextVersion = Math.max(source.getSourceVersion(), source.getActiveSourceVersion() == null
            ? 0 : source.getActiveSourceVersion()) + 1;
        source.setSourceVersion(nextVersion);
        source.setProviderVersionId(bounded(current.providerVersionId(), 512));
        source.setProviderEtag(bounded(current.etag(), 256));
        source.setProviderRevisionFingerprint(current.providerRevisionFingerprint());
        source.setContentFingerprint(null);
        source.setContentLength(current.contentLength());
        source.setProviderLastModified(current.lastModified());
        source.setMediaType(normalizedMediaType(current));
        source.setDisplayName(current.displayName());
        source.setStatus(source.getActiveSourceVersion() == null
            ? DocumentSourceEntity.Status.REGISTERED
            : DocumentSourceEntity.Status.ACTIVE);
        clearFailure(source);
        source.setUpdatedAt(now());
        return new RefreshResult("CHANGED", toSummary(sourceRepository.saveAndFlush(source)));
    }

    public synchronized PreviewResult preview(RuntimeResolvedIdentity identity, String sourceId) {
        Boundary boundary = requireBoundary(identity);
        DocumentSourceEntity source = requireSource(boundary, sourceId);
        PreparedDocument prepared = prepare(source);
        int previewLimit = Math.max(1, Math.min(properties.getPolicy().getPreviewMaxChunks(), 100));
        int characterLimit = Math.max(50, Math.min(properties.getPolicy().getPreviewMaxCharactersPerChunk(), 2_000));
        List<ChunkPreview> chunks = prepared.plan().chunks().stream()
            .limit(previewLimit)
            .map(chunk -> previewChunk(chunk, characterLimit))
            .toList();
        return new PreviewResult(
            prepared.plan().planId(),
            toSummary(source),
            prepared.plan().documentCount(),
            prepared.plan().chunks().size(),
            prepared.plan().totalContentLength(),
            prepared.plan().chunks().size() > chunks.size(),
            chunks,
            prepared.plan().warnings()
        );
    }

    @Transactional
    public synchronized OperationResult index(
        RuntimeResolvedIdentity identity,
        String sourceId,
        String idempotencyKey
    ) {
        Boundary boundary = requireBoundary(identity);
        DocumentSourceEntity source = requireSource(boundary, sourceId);
        if (!claimCommand(
            boundary,
            "INDEX",
            sourceId + ":" + source.getSourceVersion(),
            idempotencyKey,
            source.getProviderRevisionFingerprint()
        )) {
            return operationResult(source, latestManifest(source), "IDEMPOTENT_REPLAY");
        }
        reconcileSource(source, false);
        source = requireSource(boundary, sourceId);
        if (source.getStatus() == DocumentSourceEntity.Status.DELETING
            || source.getStatus() == DocumentSourceEntity.Status.DELETE_PENDING
            || source.getStatus() == DocumentSourceEntity.Status.DELETED) {
            throw conflict("Deleted document sources cannot be indexed.");
        }

        Optional<DocumentManifestEntity> existing = manifestRepository
            .findBySourceIdAndSourceVersion(source.getId(), source.getSourceVersion());
        if (existing.isPresent() && existing.get().getState() != DocumentManifestEntity.State.FAILED) {
            return operationResult(source, existing.get(), "UNCHANGED");
        }

        PreparedDocument prepared = prepare(source);
        DocumentManifestEntity manifest;
        if (existing.isPresent()) {
            manifest = existing.get();
            if (workRepository.findByManifestIdAndOperationOrderByCreatedAtAsc(
                manifest.getManifestId(), DocumentWorkEntity.Operation.DELETE
            ).stream().anyMatch(work -> !work.isTerminal())) {
                throw conflict("Failed candidate cleanup is still in progress.");
            }
            manifest.setFailureCode(null);
            manifest.setFailureMessage(null);
            manifest.setState(DocumentManifestEntity.State.DRAFT);
            manifest.setUpdatedAt(now());
        } else {
            manifest = persistManifest(source, prepared);
        }
        source.setContentFingerprint(prepared.contentFingerprint());
        source.setStatus(source.getActiveSourceVersion() == null
            ? DocumentSourceEntity.Status.INDEXING
            : DocumentSourceEntity.Status.REPLACING);
        clearFailure(source);
        source.setUpdatedAt(now());
        sourceRepository.saveAndFlush(source);

        int indexAttempt = manifest.getIndexSubmissionAttempt() + 1;
        manifest.setIndexSubmissionAttempt(indexAttempt);
        manifest.setAcceptedIndexWorkCount(0);
        manifest.setDeletePurpose(null);
        manifest.setDeletedAt(null);
        manifestRepository.saveAndFlush(manifest);

        try {
            List<IndexingQueueEntry> accepted = queueAdapter.submit(
                prepared.plan(),
                IndexingStrategy.ASYNC,
                localNow()
            );
            List<String> workIds = requireWorkIds(accepted, manifest.getChunkCount());
            persistWork(manifest, DocumentWorkEntity.Operation.INDEX, indexAttempt, workIds);
            manifest.setAcceptedIndexWorkCount(workIds.size());
            manifest.setState(DocumentManifestEntity.State.INDEX_SUBMITTED);
            manifest.setUpdatedAt(now());
            manifestRepository.saveAndFlush(manifest);
            return operationResult(source, manifest, "ACCEPTED");
        } catch (DocumentQueueSubmissionException exception) {
            List<String> acceptedWorkIds = exception.getAcceptedWorkIds();
            persistWork(manifest, DocumentWorkEntity.Operation.INDEX, indexAttempt, acceptedWorkIds);
            manifest.setAcceptedIndexWorkCount(acceptedWorkIds.size());
            manifest.setFailureCode(safeCode(exception.getCode().name()));
            manifest.setFailureMessage(safeMessage(exception.getMessage()));
            manifest.setState(acceptedWorkIds.isEmpty()
                ? DocumentManifestEntity.State.FAILED
                : DocumentManifestEntity.State.INDEX_PARTIAL);
            manifest.setUpdatedAt(now());
            manifestRepository.saveAndFlush(manifest);
            failSourcePreservingActive(source, exception.getCode().name(), exception.getMessage());
            return operationResult(source, manifest, "FAILED");
        }
    }

    @Transactional
    public synchronized OperationResult reconcile(
        RuntimeResolvedIdentity identity,
        String sourceId,
        String idempotencyKey
    ) {
        Boundary boundary = requireBoundary(identity);
        DocumentSourceEntity source = requireSource(boundary, sourceId);
        if (!claimCommand(boundary, "RECONCILE", sourceId, idempotencyKey, sourceId)) {
            return operationResult(source, latestManifest(source), "IDEMPOTENT_REPLAY");
        }
        reconcileSource(source, true);
        source = requireSource(boundary, sourceId);
        DocumentManifestEntity current = manifestRepository
            .findBySourceIdAndSourceVersion(sourceId, source.getSourceVersion())
            .orElseGet(() -> manifestRepository
                .findFirstBySourceIdAndStateOrderBySourceVersionDesc(sourceId, DocumentManifestEntity.State.ACTIVE)
                .orElse(null));
        return operationResult(source, current, "RECONCILED");
    }

    @Transactional
    public synchronized OperationResult removeIndex(
        RuntimeResolvedIdentity identity,
        String sourceId,
        String idempotencyKey
    ) {
        Boundary boundary = requireBoundary(identity);
        DocumentSourceEntity source = requireSource(boundary, sourceId);
        if (!claimCommand(boundary, "DELETE_INDEX", sourceId, idempotencyKey, sourceId)) {
            return operationResult(source, latestManifest(source), "IDEMPOTENT_REPLAY");
        }
        reconcileSource(source, false);
        source = requireSource(boundary, sourceId);
        if (source.getStatus() == DocumentSourceEntity.Status.DELETED) {
            return operationResult(source, null, "UNCHANGED");
        }

        List<DocumentManifestEntity> indexing = manifestRepository.findBySourceIdAndStateInOrderBySourceVersionAsc(
            sourceId,
            List.of(
                DocumentManifestEntity.State.INDEX_SUBMITTED,
                DocumentManifestEntity.State.INDEX_PARTIAL
            )
        );
        if (!indexing.isEmpty()) {
            source.setStatus(DocumentSourceEntity.Status.DELETE_PENDING);
            source.setUpdatedAt(now());
            sourceRepository.saveAndFlush(source);
            return operationResult(source, indexing.getFirst(), "DELETE_PENDING");
        }

        List<DocumentManifestEntity> manifests = manifestRepository.findBySourceIdAndStateInOrderBySourceVersionAsc(
            sourceId,
            List.of(
                DocumentManifestEntity.State.ACTIVE,
                DocumentManifestEntity.State.SUPERSEDED,
                DocumentManifestEntity.State.FAILED,
                DocumentManifestEntity.State.DELETE_SUBMITTED,
                DocumentManifestEntity.State.DELETE_PARTIAL,
                DocumentManifestEntity.State.DELETE_FAILED
            )
        );
        if (manifests.isEmpty()) {
            markSourceDeleted(source);
            return operationResult(source, null, "DELETED");
        }
        source.setStatus(DocumentSourceEntity.Status.DELETING);
        source.setUpdatedAt(now());
        sourceRepository.saveAndFlush(source);
        for (DocumentManifestEntity manifest : manifests) {
            submitDeletes(manifest, DocumentManifestEntity.DeletePurpose.SOURCE_REMOVAL, true);
        }
        return operationResult(source, manifests.getLast(), "ACCEPTED");
    }

    public RetrievalProof retrievalProof(
        RuntimeResolvedIdentity identity,
        String query,
        int requestedLimit
    ) {
        Boundary boundary = requireBoundary(identity);
        String safeQuery = required(query, "query", 1_000);
        int limit = Math.max(1, Math.min(requestedLimit > 0 ? requestedLimit : 5, 20));
        AISearchResponse response = coreService.performSearch(
            AISearchRequest.builder()
                .query(safeQuery)
                .entityType(properties.getEntityType())
                .limit(Math.min(100, limit * 5))
                .threshold(0.0d)
                .metadata(Map.of(
                    "tenantId", boundary.tenantId(),
                    "deploymentId", boundary.deploymentId()
                ))
                .build()
        );
        List<RetrievalEvidence> evidence = response == null || response.getResults() == null
            ? List.of()
            : response.getResults().stream()
                .map(this::toRetrievalEvidence)
                .filter(Objects::nonNull)
                .filter(item -> activeVersionFilter.accepts(item.metadata()))
                .filter(item -> boundary.tenantId().equals(text(item.metadata().get("tenantId"))))
                .filter(item -> boundary.deploymentId().equals(text(item.metadata().get("deploymentId"))))
                .limit(limit)
                .toList();
        return new RetrievalProof(
            safeQuery,
            evidence.size(),
            evidence,
            response != null && response.getProcessingTimeMs() != null ? response.getProcessingTimeMs() : 0L
        );
    }

    public RetentionStatus retentionStatus(RuntimeResolvedIdentity identity) {
        requireBoundary(identity);
        return new RetentionStatus(
            retention(properties.getEvidenceRetention(), "evidence retention").toString(),
            retention(properties.getCommandRetention(), "command retention").toString(),
            retentionBatchSize(),
            lastRetentionCleanup
        );
    }

    @Transactional
    public synchronized RetentionCleanupResult cleanupRetention(RuntimeResolvedIdentity identity) {
        Boundary boundary = requireBoundary(identity);
        return cleanupRetentionInternal(boundary);
    }

    @Scheduled(fixedDelayString = "${loomai.documents.retention-cleanup-interval:PT6H}")
    @Transactional
    public synchronized void cleanupRetentionScheduled() {
        cleanupRetentionInternal(null);
    }

    @Scheduled(fixedDelayString = "${loomai.documents.reconcile-interval:PT15S}")
    @Transactional
    public synchronized void reconcilePending() {
        Collection<DocumentManifestEntity.State> pendingStates = List.of(
            DocumentManifestEntity.State.INDEX_SUBMITTED,
            DocumentManifestEntity.State.INDEX_PARTIAL,
            DocumentManifestEntity.State.DELETE_SUBMITTED,
            DocumentManifestEntity.State.DELETE_PARTIAL
        );
        LinkedHashSet<String> sourceIds = new LinkedHashSet<>();
        manifestRepository.findByStateIn(pendingStates).forEach(manifest -> sourceIds.add(manifest.getSourceId()));
        sourceIds.stream().limit(100).forEach(sourceId -> sourceRepository.findById(sourceId)
            .ifPresent(source -> reconcileSource(source, false)));
    }

    private RetentionCleanupResult cleanupRetentionInternal(Boundary boundary) {
        Instant evidenceCutoff = now().minus(retention(properties.getEvidenceRetention(), "evidence retention"));
        Instant commandCutoff = now().minus(retention(properties.getCommandRetention(), "command retention"));
        PageRequest batch = PageRequest.of(0, retentionBatchSize());
        List<DocumentManifestEntity> candidates = boundary == null
            ? manifestRepository.findByStateAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                DocumentManifestEntity.State.DELETED,
                evidenceCutoff,
                batch
            )
            : manifestRepository.findByTenantIdAndDeploymentIdAndStateAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                boundary.tenantId(),
                boundary.deploymentId(),
                DocumentManifestEntity.State.DELETED,
                evidenceCutoff,
                batch
            );
        long manifestsDeleted = 0;
        long chunksDeleted = 0;
        long workDeleted = 0;
        for (DocumentManifestEntity manifest : candidates) {
            DocumentSourceEntity source = sourceRepository.findById(manifest.getSourceId()).orElse(null);
            if (source == null || Objects.equals(source.getActiveSourceVersion(), manifest.getSourceVersion())) {
                continue;
            }
            chunksDeleted += chunkRepository.countByManifestId(manifest.getManifestId());
            workDeleted += workRepository.findByManifestIdOrderByCreatedAtAsc(manifest.getManifestId()).size();
            chunkRepository.deleteByManifestId(manifest.getManifestId());
            workRepository.deleteByManifestId(manifest.getManifestId());
            manifestRepository.delete(manifest);
            manifestsDeleted++;
        }
        long commandsDeleted = boundary == null
            ? commandRepository.deleteByCreatedAtBefore(commandCutoff)
            : commandRepository.deleteByTenantIdAndDeploymentIdAndCreatedAtBefore(
                boundary.tenantId(), boundary.deploymentId(), commandCutoff
            );
        RetentionCleanupResult result = new RetentionCleanupResult(
            boundary == null ? "DEPLOYMENT_SCHEDULED" : "VERIFIED_BOUNDARY",
            evidenceCutoff,
            commandCutoff,
            manifestsDeleted,
            chunksDeleted,
            workDeleted,
            commandsDeleted,
            now(),
            false
        );
        lastRetentionCleanup = result;
        return result;
    }

    private Duration retention(Duration configured, String label) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new IllegalStateException("Document " + label + " must be a positive duration.");
        }
        return configured;
    }

    private int retentionBatchSize() {
        return Math.max(1, Math.min(properties.getRetentionBatchSize(), 1_000));
    }

    private void reconcileSource(DocumentSourceEntity source, boolean retryFailedDeletes) {
        List<DocumentManifestEntity> manifests = manifestRepository.findBySourceIdOrderBySourceVersionAsc(source.getId());
        for (DocumentManifestEntity manifest : manifests) {
            if (manifest.getState() == DocumentManifestEntity.State.INDEX_SUBMITTED) {
                WorkEvaluation evaluation = evaluate(
                    manifest,
                    DocumentWorkEntity.Operation.INDEX,
                    manifest.getIndexSubmissionAttempt()
                );
                if (evaluation.terminal() && evaluation.failed()) {
                    failManifest(manifest, evaluation.errorCode(), evaluation.message());
                    failSourcePreservingActive(source, evaluation.errorCode(), evaluation.message());
                    submitDeletes(
                        manifest,
                        DocumentManifestEntity.DeletePurpose.FAILED_CANDIDATE_CLEANUP,
                        false
                    );
                } else if (evaluation.successful()) {
                    activate(source, manifest);
                }
            } else if (manifest.getState() == DocumentManifestEntity.State.INDEX_PARTIAL) {
                WorkEvaluation evaluation = evaluate(
                    manifest,
                    DocumentWorkEntity.Operation.INDEX,
                    manifest.getIndexSubmissionAttempt()
                );
                if (evaluation.terminal()) {
                    submitDeletes(
                        manifest,
                        DocumentManifestEntity.DeletePurpose.FAILED_CANDIDATE_CLEANUP,
                        false
                    );
                }
            } else if (manifest.getState() == DocumentManifestEntity.State.DELETE_SUBMITTED) {
                WorkEvaluation evaluation = evaluate(
                    manifest,
                    DocumentWorkEntity.Operation.DELETE,
                    manifest.getDeleteSubmissionAttempt()
                );
                if (evaluation.terminal() && evaluation.failed()) {
                    retryOrFailDelete(source, manifest, evaluation.errorCode(), evaluation.message(), false);
                } else if (evaluation.successful()) {
                    finalizeDelete(manifest);
                }
            } else if (manifest.getState() == DocumentManifestEntity.State.DELETE_PARTIAL) {
                WorkEvaluation evaluation = evaluate(
                    manifest,
                    DocumentWorkEntity.Operation.DELETE,
                    manifest.getDeleteSubmissionAttempt()
                );
                if (evaluation.empty() || evaluation.terminal()) {
                    retryOrFailDelete(
                        source,
                        manifest,
                        "DOCUMENT_DELETE_ACCEPTANCE_INCOMPLETE",
                        "Exact document index deletion was not fully accepted.",
                        false
                    );
                }
            } else if (manifest.getState() == DocumentManifestEntity.State.DELETE_FAILED
                && retryFailedDeletes) {
                submitDeletes(manifest, manifest.getDeletePurpose(), true);
            }
        }

        source = sourceRepository.findById(source.getId()).orElse(source);
        if (source.getStatus() == DocumentSourceEntity.Status.DELETE_PENDING
            && manifestRepository.findBySourceIdAndStateInOrderBySourceVersionAsc(
                source.getId(), List.of(
                    DocumentManifestEntity.State.INDEX_SUBMITTED,
                    DocumentManifestEntity.State.INDEX_PARTIAL
                )
            ).isEmpty()) {
            source.setStatus(DocumentSourceEntity.Status.DELETING);
            sourceRepository.saveAndFlush(source);
            manifestRepository.findBySourceIdAndStateInOrderBySourceVersionAsc(
                source.getId(),
                List.of(
                    DocumentManifestEntity.State.ACTIVE,
                    DocumentManifestEntity.State.SUPERSEDED,
                    DocumentManifestEntity.State.FAILED,
                    DocumentManifestEntity.State.DELETE_PARTIAL,
                    DocumentManifestEntity.State.DELETE_FAILED
                )
            ).forEach(manifest -> submitDeletes(
                manifest,
                DocumentManifestEntity.DeletePurpose.SOURCE_REMOVAL,
                true
            ));
        }
        if (source.getStatus() == DocumentSourceEntity.Status.DELETING) {
            List<DocumentManifestEntity> remaining = manifestRepository.findBySourceIdAndStateInOrderBySourceVersionAsc(
                source.getId(),
                List.of(
                    DocumentManifestEntity.State.DRAFT,
                    DocumentManifestEntity.State.INDEX_SUBMITTED,
                    DocumentManifestEntity.State.INDEX_PARTIAL,
                    DocumentManifestEntity.State.ACTIVE,
                    DocumentManifestEntity.State.SUPERSEDED,
                    DocumentManifestEntity.State.DELETE_SUBMITTED,
                    DocumentManifestEntity.State.DELETE_PARTIAL,
                    DocumentManifestEntity.State.DELETE_FAILED
                )
            );
            if (remaining.isEmpty()) {
                markSourceDeleted(source);
            }
        }
    }

    private void activate(DocumentSourceEntity source, DocumentManifestEntity candidate) {
        Optional<DocumentManifestEntity> oldActive = manifestRepository
            .findFirstBySourceIdAndStateOrderBySourceVersionDesc(source.getId(), DocumentManifestEntity.State.ACTIVE)
            .filter(value -> value.getSourceVersion() != candidate.getSourceVersion());
        candidate.setState(DocumentManifestEntity.State.ACTIVE);
        candidate.setActivatedAt(now());
        candidate.setFailureCode(null);
        candidate.setFailureMessage(null);
        candidate.setUpdatedAt(now());
        manifestRepository.saveAndFlush(candidate);

        source.setActiveSourceVersion(candidate.getSourceVersion());
        source.setStatus(DocumentSourceEntity.Status.ACTIVE);
        clearFailure(source);
        source.setUpdatedAt(now());
        sourceRepository.saveAndFlush(source);

        oldActive.ifPresent(old -> {
            old.setState(DocumentManifestEntity.State.SUPERSEDED);
            old.setSupersededAt(now());
            old.setUpdatedAt(now());
            manifestRepository.saveAndFlush(old);
            submitDeletes(
                old,
                DocumentManifestEntity.DeletePurpose.SUPERSEDED_REPLACEMENT,
                false
            );
        });
    }

    private PreparedDocument prepare(DocumentSourceEntity source) {
        SourceObject current = normalizeAndValidate(connector.stat(source.getObjectLocator()));
        if (!Objects.equals(current.providerRevisionFingerprint(), source.getProviderRevisionFingerprint())) {
            throw conflict("The external document revision changed. Refresh the source before preview or indexing.");
        }
        try (MaterializedDocument materialized = connector.materialize(current)) {
            var resource = new FileSystemResource(materialized.path());
            var policy = SpringAiTrustedResourcePolicy.trustedRoot(materialized.trustedRoot());
            String extension = DocumentConnectorSupport.extension(current.objectReference());
            var reader = ".json".equals(extension)
                ? readerFactory.jsonReader(
                    resource,
                    policy,
                    properties.getPolicy().getJsonContentKeys().toArray(String[]::new)
                )
                : readerFactory.textReader(resource, policy);
            Map<String, Object> metadata = readMetadata(source.getMetadataJson());
            metadata = new LinkedHashMap<>(metadata);
            metadata.put("originalFilename", source.getDisplayName());
            metadata.put("tenantId", source.getTenantId());
            metadata.put("customerId", source.getCustomerId());
            metadata.put("deploymentId", source.getDeploymentId());
            metadata.put("datasetId", source.getDatasetId());
            metadata.put("knowledgeSourceHandleRef", datasetHandleRef(source.getDatasetId()));
            Set<String> allowedKeys = new LinkedHashSet<>(properties.getPolicy().getAllowedMetadataKeys());
            allowedKeys.addAll(SYSTEM_METADATA_KEYS);
            allowedKeys.add("originalFilename");
            SpringAiDocumentIndexingOptions.Builder options = SpringAiDocumentIndexingOptions.builder()
                .entityType(required(properties.getEntityType(), "loomai.documents.entity-type", 128))
                .sourceId(source.getId())
                .sourceVersion(source.getSourceVersion())
                .sourceName(source.getDisplayName())
                .tenantId(source.getTenantId())
                .visibility(source.getVisibility())
                .operation(source.getActiveSourceVersion() == null ? AIProcessOperation.CREATE : AIProcessOperation.UPDATE)
                .splitWithTokenTextSplitter(true)
                .tokenChunkSize(800)
                .maxDocuments(20)
                .maxChunks(properties.getPolicy().getMaxChunksPerSource())
                .maxContentLength(properties.getPolicy().getMaxChunkCharacters())
                .maxTotalContentLength(properties.getPolicy().getMaxTotalCharacters())
                .maxMetadataEntries(24)
                .maxMetadataValueLength(256)
                .allowedMetadataKeys(allowedKeys)
                .correlationId("document-" + source.getId() + "-v" + source.getSourceVersion())
                .occurredAt(now());
            metadata.forEach(options::metadata);
            DocumentIngestionPlan plan = planningAdapter.plan(reader, options.build());
            DocumentIngestionManifest manifest = planningAdapter.manifest(plan);
            return new PreparedDocument(plan, manifest, materialized.contentFingerprint());
        }
    }

    private DocumentManifestEntity persistManifest(DocumentSourceEntity source, PreparedDocument prepared) {
        DocumentIngestionManifest framework = prepared.manifest();
        Instant now = now();
        DocumentManifestEntity entity = new DocumentManifestEntity();
        entity.setManifestId(framework.manifestId());
        entity.setManifestSchemaVersion(framework.schemaVersion());
        entity.setPlanId(framework.planId());
        entity.setSourceId(framework.sourceId());
        entity.setSourceVersion(framework.sourceVersion());
        entity.setProviderRevisionFingerprint(source.getProviderRevisionFingerprint());
        entity.setSourceName(framework.sourceName());
        entity.setEntityType(framework.entityType());
        entity.setTenantId(source.getTenantId());
        entity.setCustomerId(source.getCustomerId());
        entity.setDeploymentId(source.getDeploymentId());
        entity.setVisibility(framework.visibility());
        entity.setState(DocumentManifestEntity.State.DRAFT);
        entity.setChunkCount(framework.chunks().size());
        entity.setWarningsJson(writeJson(prepared.plan().warnings()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        manifestRepository.saveAndFlush(entity);

        List<DocumentManifestChunkEntity> chunks = framework.chunks().stream()
            .map(chunk -> toChunk(entity, chunk))
            .toList();
        chunkRepository.saveAllAndFlush(chunks);
        return entity;
    }

    private DocumentManifestChunkEntity toChunk(DocumentManifestEntity manifest, DocumentManifestChunk chunk) {
        DocumentManifestChunkEntity entity = new DocumentManifestChunkEntity();
        entity.setId(uuid());
        entity.setManifestId(manifest.getManifestId());
        entity.setSourceDocumentId(chunk.sourceDocumentId());
        entity.setChunkId(chunk.chunkId());
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setEntityId(chunk.entityId());
        entity.setContentFingerprint(chunk.contentFingerprint());
        return entity;
    }

    private void submitDeletes(
        DocumentManifestEntity manifest,
        DocumentManifestEntity.DeletePurpose purpose,
        boolean operatorOverride
    ) {
        if (manifest.getState() == DocumentManifestEntity.State.DELETE_SUBMITTED
            || manifest.getState() == DocumentManifestEntity.State.DELETED) {
            return;
        }
        DocumentManifestEntity.DeletePurpose effectivePurpose = purpose == null
            ? manifest.getDeletePurpose()
            : purpose;
        if (effectivePurpose == null) {
            throw new IllegalStateException("Document delete purpose is required.");
        }
        int maxAttempts = Math.max(1, Math.min(properties.getMaxAutomaticDeleteAttempts(), 10));
        if (!operatorOverride && manifest.getDeleteSubmissionAttempt() >= maxAttempts) {
            markDeleteFailed(
                manifest,
                "DOCUMENT_DELETE_RETRY_EXHAUSTED",
                "Exact document index deletion requires operator reconciliation."
            );
            return;
        }
        int deleteAttempt = manifest.getDeleteSubmissionAttempt() + 1;
        manifest.setDeletePurpose(effectivePurpose);
        manifest.setDeleteSubmissionAttempt(deleteAttempt);
        manifest.setAcceptedDeleteWorkCount(0);
        manifest.setUpdatedAt(now());
        manifestRepository.saveAndFlush(manifest);

        DocumentIngestionManifest exactManifest = toFrameworkManifest(manifest);
        try {
            List<IndexingQueueEntry> accepted = queueAdapter.submitDeletes(
                exactManifest,
                IndexingStrategy.ASYNC,
                localNow(),
                now()
            );
            List<String> ids = requireWorkIds(accepted, manifest.getChunkCount());
            persistWork(manifest, DocumentWorkEntity.Operation.DELETE, deleteAttempt, ids);
            manifest.setAcceptedDeleteWorkCount(ids.size());
            manifest.setState(DocumentManifestEntity.State.DELETE_SUBMITTED);
            if (effectivePurpose != DocumentManifestEntity.DeletePurpose.FAILED_CANDIDATE_CLEANUP) {
                manifest.setFailureCode(null);
                manifest.setFailureMessage(null);
            }
            manifest.setUpdatedAt(now());
            manifestRepository.saveAndFlush(manifest);
        } catch (DocumentQueueSubmissionException exception) {
            List<String> acceptedWorkIds = exception.getAcceptedWorkIds();
            persistWork(manifest, DocumentWorkEntity.Operation.DELETE, deleteAttempt, acceptedWorkIds);
            manifest.setAcceptedDeleteWorkCount(acceptedWorkIds.size());
            manifest.setState(DocumentManifestEntity.State.DELETE_PARTIAL);
            if (effectivePurpose != DocumentManifestEntity.DeletePurpose.FAILED_CANDIDATE_CLEANUP) {
                manifest.setFailureCode(safeCode(exception.getCode().name()));
                manifest.setFailureMessage(safeMessage("Exact document index deletion submission was incomplete."));
            }
            manifest.setUpdatedAt(now());
            manifestRepository.saveAndFlush(manifest);
        }
    }

    private void retryOrFailDelete(
        DocumentSourceEntity source,
        DocumentManifestEntity manifest,
        String code,
        String message,
        boolean operatorOverride
    ) {
        int maxAttempts = Math.max(1, Math.min(properties.getMaxAutomaticDeleteAttempts(), 10));
        if (!operatorOverride && manifest.getDeleteSubmissionAttempt() >= maxAttempts) {
            markDeleteFailed(manifest, code, message);
            if (manifest.getDeletePurpose() == DocumentManifestEntity.DeletePurpose.SOURCE_REMOVAL) {
                source.setStatus(DocumentSourceEntity.Status.FAILED);
            }
            source.setLastFailureCode(safeCode(code));
            source.setLastFailureMessage(safeMessage(message));
            source.setUpdatedAt(now());
            sourceRepository.saveAndFlush(source);
            return;
        }
        manifest.setState(DocumentManifestEntity.State.DELETE_PARTIAL);
        manifest.setUpdatedAt(now());
        manifestRepository.saveAndFlush(manifest);
        submitDeletes(manifest, manifest.getDeletePurpose(), operatorOverride);
    }

    private void markDeleteFailed(DocumentManifestEntity manifest, String code, String message) {
        manifest.setState(DocumentManifestEntity.State.DELETE_FAILED);
        if (manifest.getDeletePurpose() != DocumentManifestEntity.DeletePurpose.FAILED_CANDIDATE_CLEANUP) {
            manifest.setFailureCode(safeCode(code));
            manifest.setFailureMessage(safeMessage(message));
        }
        manifest.setUpdatedAt(now());
        manifestRepository.saveAndFlush(manifest);
    }

    private void finalizeDelete(DocumentManifestEntity manifest) {
        boolean failedCandidate = manifest.getDeletePurpose()
            == DocumentManifestEntity.DeletePurpose.FAILED_CANDIDATE_CLEANUP;
        manifest.setState(failedCandidate
            ? DocumentManifestEntity.State.FAILED
            : DocumentManifestEntity.State.DELETED);
        manifest.setDeletedAt(now());
        manifest.setUpdatedAt(now());
        manifestRepository.saveAndFlush(manifest);
    }

    private DocumentIngestionManifest toFrameworkManifest(DocumentManifestEntity manifest) {
        List<DocumentManifestChunk> chunks = chunkRepository
            .findByManifestIdOrderByChunkIndexAsc(manifest.getManifestId()).stream()
            .map(chunk -> new DocumentManifestChunk(
                chunk.getSourceDocumentId(),
                chunk.getChunkId(),
                chunk.getChunkIndex(),
                chunk.getEntityId(),
                chunk.getContentFingerprint()
            ))
            .toList();
        return new DocumentIngestionManifest(
            manifest.getManifestSchemaVersion(),
            manifest.getManifestId(),
            manifest.getPlanId(),
            manifest.getSourceId(),
            manifest.getSourceVersion(),
            manifest.getSourceName(),
            manifest.getEntityType(),
            manifest.getTenantId(),
            manifest.getVisibility(),
            manifest.getCreatedAt(),
            chunks
        );
    }

    private WorkEvaluation evaluate(
        DocumentManifestEntity manifest,
        DocumentWorkEntity.Operation operation,
        int submissionAttempt
    ) {
        List<DocumentWorkEntity> work = workRepository
            .findByManifestIdAndOperationAndSubmissionAttemptOrderByCreatedAtAsc(
                manifest.getManifestId(),
                operation,
                submissionAttempt
            );
        if (work.isEmpty()) {
            return WorkEvaluation.emptyEvaluation();
        }
        boolean allTerminal = true;
        boolean allSuccessful = true;
        String failureCode = null;
        String failureMessage = null;
        for (DocumentWorkEntity row : work) {
            Optional<IndexingWorkStatus> current = workQuery.findByWorkId(row.getFrameworkWorkId());
            if (current.isEmpty()) {
                allTerminal = false;
                allSuccessful = false;
                continue;
            }
            IndexingWorkStatus status = current.get();
            row.setLastWorkState(status.status().name());
            row.setTerminal(status.isTerminal());
            row.setSuccessful(status.isSuccessfulTerminal());
            row.setFailureCode(safeCode(status.errorCode()));
            row.setFailureMessage(safeMessage(status.deadLetterReason()));
            row.setLastObservedAt(now());
            workRepository.save(row);
            if (status.requiresOperatorReview()) {
                failureCode = StringUtils.hasText(status.errorCode())
                    ? status.errorCode()
                    : "INDEXING_WORK_FAILED";
                failureMessage = StringUtils.hasText(status.deadLetterReason())
                    ? status.deadLetterReason()
                    : "Document indexing work requires operator review.";
            }
            allTerminal &= status.isTerminal();
            allSuccessful &= status.isSuccessfulTerminal();
        }
        if (!allTerminal) {
            return WorkEvaluation.inProgress();
        }
        return allSuccessful
            ? WorkEvaluation.success()
            : WorkEvaluation.failure(failureCode, failureMessage);
    }

    private void persistWork(
        DocumentManifestEntity manifest,
        DocumentWorkEntity.Operation operation,
        int submissionAttempt,
        List<String> workIds
    ) {
        if (workIds == null) {
            return;
        }
        Instant now = now();
        for (String workId : workIds) {
            if (!StringUtils.hasText(workId)) {
                continue;
            }
            String normalizedWorkId = workId.trim();
            DocumentWorkEntity row = workRepository
                .findByManifestIdAndOperationAndFrameworkWorkId(
                    manifest.getManifestId(), operation, normalizedWorkId
                )
                .orElseGet(DocumentWorkEntity::new);
            if (row.getId() == null) {
                row.setId(uuid());
                row.setManifestId(manifest.getManifestId());
                row.setOperation(operation);
                row.setFrameworkWorkId(normalizedWorkId);
                row.setCreatedAt(now);
            }
            row.setSubmissionAttempt(submissionAttempt);
            row.setLastWorkState("ACCEPTED");
            row.setTerminal(false);
            row.setSuccessful(false);
            row.setFailureCode(null);
            row.setFailureMessage(null);
            row.setLastObservedAt(now);
            workRepository.save(row);
        }
        workRepository.flush();
    }

    private boolean claimCommand(
        Boundary boundary,
        String operation,
        String resourceKey,
        String idempotencyKey,
        String requestHash
    ) {
        String key = required(idempotencyKey, "Idempotency-Key", 128);
        Optional<DocumentCommandEntity> existing = commandRepository
            .findByTenantIdAndDeploymentIdAndOperationAndIdempotencyKey(
                boundary.tenantId(), boundary.deploymentId(), operation, key
            );
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getRequestHash(), requestHash)
                || !Objects.equals(existing.get().getResourceKey(), resourceKey)) {
                throw conflict("Idempotency-Key was already used for a different document command.");
            }
            return false;
        }
        DocumentCommandEntity command = new DocumentCommandEntity();
        command.setId(uuid());
        command.setTenantId(boundary.tenantId());
        command.setDeploymentId(boundary.deploymentId());
        command.setOperation(operation);
        command.setResourceKey(bounded(resourceKey, 256));
        command.setIdempotencyKey(key);
        command.setRequestHash(requestHash);
        command.setCreatedAt(now());
        commandRepository.saveAndFlush(command);
        return true;
    }

    private void restoreDeletedRegistration(
        DocumentSourceEntity source,
        SourceObject object,
        RegisterSourceCommand command
    ) {
        source.setConnectorType(connector.type().name());
        source.setConnectorBindingRef(bounded(properties.getConnector().getBindingRef(), 128));
        source.setObjectLocator(object.objectReference());
        source.setObjectLocatorDigest(DocumentConnectorSupport.sha256(object.objectReference()));
        source.setDisplayName(object.displayName());
        source.setMediaType(normalizedMediaType(object));
        source.setProviderVersionId(bounded(object.providerVersionId(), 512));
        source.setProviderEtag(bounded(object.etag(), 256));
        source.setProviderRevisionFingerprint(object.providerRevisionFingerprint());
        source.setContentFingerprint(null);
        source.setContentLength(object.contentLength());
        source.setProviderLastModified(object.lastModified());
        source.setSourceVersion(source.getSourceVersion() + 1);
        source.setActiveSourceVersion(null);
        source.setVisibility(command.visibility());
        source.setMetadataJson(writeJson(command.metadata()));
        source.setStatus(DocumentSourceEntity.Status.REGISTERED);
        source.setDeletedAt(null);
        clearFailure(source);
        source.setUpdatedAt(now());
    }

    private DocumentManifestEntity latestManifest(DocumentSourceEntity source) {
        return manifestRepository.findBySourceIdAndSourceVersion(source.getId(), source.getSourceVersion())
            .orElseGet(() -> {
                List<DocumentManifestEntity> manifests = manifestRepository
                    .findBySourceIdOrderBySourceVersionAsc(source.getId());
                return manifests.isEmpty() ? null : manifests.getLast();
            });
    }

    private RegisterSourceCommand normalize(RegisterSourceCommand command) {
        if (command == null) {
            throw badRequest("Document source registration is required.");
        }
        String datasetId = required(command.datasetId(), "datasetId", 128);
        if (!datasetId.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
            throw badRequest("datasetId is invalid.");
        }
        requireAllowedDataset(datasetId);
        String reference = DocumentConnectorSupport.normalizeReference(command.objectReference());
        String visibility = StringUtils.hasText(command.visibility())
            ? required(command.visibility(), "visibility", 64)
            : "internal";
        if (!Set.of("internal", "private", "tenant").contains(visibility.toLowerCase(Locale.ROOT))) {
            throw badRequest("visibility is not allowed by the document policy.");
        }
        return new RegisterSourceCommand(
            datasetId,
            reference,
            visibility.toLowerCase(Locale.ROOT),
            normalizeMetadata(command.metadata())
        );
    }

    private String requireAllowedDataset(String value) {
        String datasetId = required(value, "datasetId", 128);
        boolean allowed = properties.getAllowedDatasetIds() != null
            && properties.getAllowedDatasetIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(datasetId::equals);
        if (!allowed) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_DATASET_NOT_ALLOWED",
                HttpStatus.FORBIDDEN,
                "Document dataset is not enabled for this deployment."
            );
        }
        return datasetId;
    }

    private Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        if (metadata.size() > 16) {
            throw badRequest("Document metadata exceeds the configured entry limit.");
        }
        Set<String> allowed = new LinkedHashSet<>(properties.getPolicy().getAllowedMetadataKeys());
        Map<String, Object> normalized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || !allowed.contains(key) || SYSTEM_METADATA_KEYS.contains(key)) {
                throw badRequest("Document metadata key is not allowed: " + bounded(key, 64));
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw badRequest("Document metadata values must be scalar.");
            }
            String safeValue = value.toString();
            if (safeValue.length() > 256) {
                throw badRequest("Document metadata value exceeds the configured boundary.");
            }
            normalized.put(key, value);
        });
        return Map.copyOf(normalized);
    }

    private SourceObject normalizeAndValidate(SourceObject object) {
        if (object == null) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                "Document source was not found."
            );
        }
        String reference = DocumentConnectorSupport.normalizeReference(object.objectReference());
        String extension = DocumentConnectorSupport.extension(reference).toLowerCase(Locale.ROOT);
        boolean allowedExtension = properties.getPolicy().getAllowedExtensions().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(extension::equals);
        if (!allowedExtension) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_TYPE_UNSUPPORTED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document source type is not supported."
            );
        }
        String mediaType = normalizedMediaType(object);
        boolean allowedMedia = properties.getPolicy().getAllowedMediaTypes().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(mediaType::equals);
        if (!allowedMedia
            || (".txt".equals(extension) && !"text/plain".equals(mediaType))
            || (".json".equals(extension) && !"application/json".equals(mediaType))) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_TYPE_UNSUPPORTED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document media type does not match the configured document policy."
            );
        }
        if (object.contentLength() <= 0 || object.contentLength() > properties.getPolicy().getMaxSourceBytes()) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_LIMIT_EXCEEDED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document source exceeds the configured size boundary."
            );
        }
        return new SourceObject(
            reference,
            bounded(object.displayName(), 255),
            mediaType,
            object.contentLength(),
            object.providerVersionId(),
            object.etag(),
            object.lastModified(),
            required(object.providerRevisionFingerprint(), "providerRevisionFingerprint", 64)
        );
    }

    private String normalizedMediaType(SourceObject object) {
        String value = object.mediaType();
        if (StringUtils.hasText(value)) {
            String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!"application/octet-stream".equals(normalized)) {
                if (normalized.endsWith("+json")) {
                    return "application/json";
                }
                return normalized;
            }
        }
        return ".json".equals(DocumentConnectorSupport.extension(object.objectReference()).toLowerCase(Locale.ROOT))
            ? "application/json"
            : "text/plain";
    }

    private void enforceRegistrationLimits(Boundary boundary, long bytes) {
        long sourceCount = sourceRepository.countByTenantIdAndDeploymentIdAndStatusNot(
            boundary.tenantId(), boundary.deploymentId(), DocumentSourceEntity.Status.DELETED
        );
        if (sourceCount >= properties.getPolicy().getMaxSources()) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_SOURCE_LIMIT_EXCEEDED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document source count exceeds the configured deployment limit."
            );
        }
        long currentBytes = sourceRepository.sumContentLengthForBoundary(boundary.tenantId(), boundary.deploymentId());
        if (currentBytes + bytes > properties.getPolicy().getMaxTotalIndexedBytes()) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_BYTE_LIMIT_EXCEEDED",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document source bytes exceed the configured deployment limit."
            );
        }
    }

    private DocumentSourceEntity requireSource(Boundary boundary, String sourceId) {
        String id = required(sourceId, "sourceId", 64);
        return sourceRepository.findByIdAndTenantIdAndDeploymentId(id, boundary.tenantId(), boundary.deploymentId())
            .orElseThrow(() -> new EntityNotFoundException("Document source was not found."));
    }

    private void requireMutable(DocumentSourceEntity source) {
        if (Set.of(
            DocumentSourceEntity.Status.INDEXING,
            DocumentSourceEntity.Status.REPLACING,
            DocumentSourceEntity.Status.DELETE_PENDING,
            DocumentSourceEntity.Status.DELETING,
            DocumentSourceEntity.Status.DELETED
        ).contains(source.getStatus())) {
            throw conflict("Document source has lifecycle work in progress or is deleted.");
        }
    }

    private Boundary requireBoundary(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext context = identity != null ? identity.getAuthContext() : null;
        if (context == null) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_AUTH_CONTEXT_REQUIRED",
                HttpStatus.UNAUTHORIZED,
                "Verified document administration context is required."
            );
        }
        return new Boundary(
            required(context.getTenantId(), "verified tenantId", 128),
            required(context.getCustomerId(), "verified customerId", 128),
            required(context.getDeploymentId(), "verified deploymentId", 128)
        );
    }

    private void failManifest(DocumentManifestEntity manifest, String code, String message) {
        manifest.setState(DocumentManifestEntity.State.FAILED);
        manifest.setFailureCode(safeCode(code));
        manifest.setFailureMessage(safeMessage(message));
        manifest.setUpdatedAt(now());
        manifestRepository.saveAndFlush(manifest);
    }

    private void failSourcePreservingActive(DocumentSourceEntity source, String code, String message) {
        source.setStatus(source.getActiveSourceVersion() == null
            ? DocumentSourceEntity.Status.FAILED
            : DocumentSourceEntity.Status.ACTIVE);
        source.setLastFailureCode(safeCode(code));
        source.setLastFailureMessage(safeMessage(message));
        source.setUpdatedAt(now());
        sourceRepository.saveAndFlush(source);
    }

    private void markSourceDeleted(DocumentSourceEntity source) {
        source.setStatus(DocumentSourceEntity.Status.DELETED);
        source.setActiveSourceVersion(null);
        source.setDeletedAt(now());
        source.setUpdatedAt(now());
        clearFailure(source);
        sourceRepository.saveAndFlush(source);
    }

    private void clearFailure(DocumentSourceEntity source) {
        source.setLastFailureCode(null);
        source.setLastFailureMessage(null);
    }

    private SourceSummary toSummary(DocumentSourceEntity source) {
        return new SourceSummary(
            source.getId(),
            source.getDatasetId(),
            source.getDisplayName(),
            source.getObjectLocatorDigest(),
            source.getConnectorType(),
            source.getConnectorBindingRef(),
            source.getMediaType(),
            source.getContentLength(),
            source.getSourceVersion(),
            source.getActiveSourceVersion(),
            source.getProviderRevisionFingerprint(),
            safeProviderRevision(source.getProviderVersionId()),
            safeProviderRevision(source.getProviderEtag()),
            source.getProviderLastModified(),
            source.getVisibility(),
            source.getStatus().name(),
            source.getLastFailureCode(),
            source.getLastFailureMessage(),
            source.getUpdatedAt()
        );
    }

    private ManifestSummary toManifestSummary(DocumentManifestEntity manifest) {
        List<WorkSummary> work = workRepository.findByManifestIdOrderByCreatedAtAsc(manifest.getManifestId()).stream()
            .map(row -> new WorkSummary(
                row.getFrameworkWorkId(),
                row.getOperation().name(),
                row.getSubmissionAttempt(),
                row.getLastWorkState(),
                row.isTerminal(),
                row.isSuccessful(),
                row.getFailureCode(),
                row.getFailureMessage(),
                row.getLastObservedAt()
            ))
            .toList();
        return new ManifestSummary(
            manifest.getManifestId(),
            manifest.getSourceVersion(),
            manifest.getProviderRevisionFingerprint(),
            manifest.getState().name(),
            manifest.getChunkCount(),
            manifest.getAcceptedIndexWorkCount(),
            manifest.getAcceptedDeleteWorkCount(),
            manifest.getIndexSubmissionAttempt(),
            manifest.getDeleteSubmissionAttempt(),
            manifest.getDeletePurpose() == null ? null : manifest.getDeletePurpose().name(),
            readWarnings(manifest.getWarningsJson()),
            manifest.getFailureCode(),
            manifest.getFailureMessage(),
            manifest.getCreatedAt(),
            manifest.getActivatedAt(),
            manifest.getSupersededAt(),
            manifest.getDeletedAt(),
            work
        );
    }

    private OperationResult operationResult(
        DocumentSourceEntity source,
        DocumentManifestEntity manifest,
        String outcome
    ) {
        return new OperationResult(
            outcome,
            toSummary(sourceRepository.findById(source.getId()).orElse(source)),
            manifest == null ? null : toManifestSummary(
                manifestRepository.findById(manifest.getManifestId()).orElse(manifest)
            )
        );
    }

    private ChunkPreview previewChunk(DocumentIngestionChunk chunk, int limit) {
        String content = chunk.indexDocument().semanticSearchText();
        boolean truncated = content.length() > limit;
        return new ChunkPreview(
            chunk.sourceDocumentId(),
            chunk.chunkId(),
            chunk.chunkIndex(),
            chunk.chunkCount(),
            chunk.entityId(),
            truncated ? content.substring(0, limit) : content,
            chunk.contentLength(),
            truncated,
            chunk.contentFingerprint(),
            chunk.warnings()
        );
    }

    private RetrievalEvidence toRetrievalEvidence(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Map<String, Object> metadata = metadata(row.get("metadata"));
        String entityId = text(row.get("id"));
        if (!StringUtils.hasText(entityId)) {
            return null;
        }
        return new RetrievalEvidence(
            entityId,
            text(metadata.get(DocumentMetadataKeys.SOURCE_ID)),
            longValue(metadata.get(DocumentMetadataKeys.SOURCE_VERSION)),
            text(metadata.get(DocumentMetadataKeys.SOURCE_NAME)),
            text(metadata.get(DocumentMetadataKeys.CHUNK_ID)),
            intValue(metadata.get(DocumentMetadataKeys.CHUNK_INDEX)),
            bounded(text(row.get("content")), 4_000),
            doubleValue(row.get("score")),
            Map.copyOf(metadata)
        );
    }

    private List<String> requireWorkIds(List<IndexingQueueEntry> entries, int expected) {
        if (entries == null || entries.size() != expected) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_INDEXING_ACCEPTANCE_INCOMPLETE",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document indexing queue returned incomplete acceptance evidence."
            );
        }
        List<String> ids = entries.stream()
            .map(IndexingQueueEntry::getId)
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .toList();
        if (ids.size() != expected || new LinkedHashSet<>(ids).size() != expected) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_INDEXING_ACCEPTANCE_INCOMPLETE",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Document indexing queue returned incomplete work identifiers."
            );
        }
        return ids;
    }

    private String logicalKey(Boundary boundary, String datasetId, String reference) {
        return DocumentConnectorSupport.sha256(
            boundary.tenantId() + "|" + boundary.customerId() + "|" + boundary.deploymentId()
                + "|" + datasetId + "|" + reference
        );
    }

    private String datasetHandleRef(String datasetId) {
        try {
            JsonNode configured = objectMapper.readTree(properties.getDatasetHandleRefsJson());
            String handleRef = configured.path(datasetId).asText("").trim();
            if (!StringUtils.hasText(handleRef) || handleRef.length() > 512) {
                throw new IllegalStateException("Document dataset handle is missing for dataset " + datasetId + ".");
            }
            return handleRef;
        } catch (DocumentKnowledgeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentKnowledgeException(
                "DOCUMENT_DATASET_HANDLE_INVALID",
                HttpStatus.SERVICE_UNAVAILABLE,
                "Document dataset routing is not configured.",
                exception
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize document lifecycle evidence", exception);
        }
    }

    private Map<String, Object> readMetadata(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored document metadata is invalid", exception);
        }
    }

    private List<DocumentIngestionWarning> readWarnings(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                value,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DocumentIngestionWarning.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> metadata(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> {
                if (key != null && item != null) {
                    result.put(key.toString(), item);
                }
            });
            return result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return objectMapper.readValue(text, MAP_TYPE);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private String safeProviderRevision(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return DocumentConnectorSupport.sha256(value.trim());
    }

    private String safeCode(String value) {
        return bounded(value, 128);
    }

    private String safeMessage(String value) {
        return bounded(value, 512);
    }

    private String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value
            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String required(String value, String name, int max) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(name + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw badRequest(name + " exceeds the configured boundary.");
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        Long number = longValue(value);
        return number == null ? null : number.intValue();
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Instant now() {
        return clock.instant();
    }

    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(now(), ZoneOffset.UTC);
    }

    private DocumentKnowledgeException badRequest(String message) {
        return new DocumentKnowledgeException("INVALID_DOCUMENT_REQUEST", HttpStatus.BAD_REQUEST, message);
    }

    private DocumentKnowledgeException conflict(String message) {
        return new DocumentKnowledgeException("DOCUMENT_LIFECYCLE_CONFLICT", HttpStatus.CONFLICT, message);
    }

    public record RegisterSourceCommand(
        String datasetId,
        String objectReference,
        String visibility,
        Map<String, Object> metadata
    ) { }

    public record DiscoveredSource(
        String objectReference,
        String displayName,
        String mediaType,
        long contentLength,
        String providerVersionDigest,
        String etagDigest,
        Instant lastModified,
        String providerRevisionFingerprint,
        String registeredSourceId
    ) { }

    public record DiscoveryResult(List<DiscoveredSource> sources, String nextCursor) { }

    public record SourceSummary(
        String sourceId,
        String datasetId,
        String displayName,
        String objectLocatorDigest,
        String connectorType,
        String connectorBindingRef,
        String mediaType,
        long contentLength,
        long candidateVersion,
        Long activeVersion,
        String providerRevisionFingerprint,
        String providerVersionDigest,
        String etagDigest,
        Instant providerLastModified,
        String visibility,
        String status,
        String failureCode,
        String failureMessage,
        Instant updatedAt
    ) { }

    public record RefreshResult(String outcome, SourceSummary source) { }

    public record SourceDetail(SourceSummary source, List<ManifestSummary> manifests) { }

    public record PreviewResult(
        String planId,
        SourceSummary source,
        int documentCount,
        int chunkCount,
        int totalContentLength,
        boolean chunksTruncated,
        List<ChunkPreview> chunks,
        List<DocumentIngestionWarning> warnings
    ) { }

    public record ChunkPreview(
        String sourceDocumentId,
        String chunkId,
        int chunkIndex,
        int chunkCount,
        String entityId,
        String contentPreview,
        int contentLength,
        boolean truncated,
        String contentFingerprint,
        List<DocumentIngestionWarning> warnings
    ) { }

    public record OperationResult(String outcome, SourceSummary source, ManifestSummary manifest) { }

    public record ManifestSummary(
        String manifestId,
        long sourceVersion,
        String providerRevisionFingerprint,
        String state,
        int chunkCount,
        int acceptedIndexWorkCount,
        int acceptedDeleteWorkCount,
        int indexSubmissionAttempt,
        int deleteSubmissionAttempt,
        String deletePurpose,
        List<DocumentIngestionWarning> warnings,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant activatedAt,
        Instant supersededAt,
        Instant deletedAt,
        List<WorkSummary> work
    ) { }

    public record WorkSummary(
        String workId,
        String operation,
        int submissionAttempt,
        String state,
        boolean terminal,
        boolean successful,
        String failureCode,
        String failureMessage,
        Instant lastObservedAt
    ) { }

    public record RetrievalProof(
        String query,
        int evidenceCount,
        List<RetrievalEvidence> evidence,
        long processingTimeMs
    ) { }

    public record RetrievalEvidence(
        String entityId,
        String sourceId,
        Long sourceVersion,
        String sourceName,
        String chunkId,
        Integer chunkIndex,
        String content,
        Double score,
        Map<String, Object> metadata
    ) { }

    public record RetentionStatus(
        String evidenceRetention,
        String commandRetention,
        int batchSize,
        RetentionCleanupResult lastCleanup
    ) { }

    public record RetentionCleanupResult(
        String scope,
        Instant evidenceCutoff,
        Instant commandCutoff,
        long manifestsDeleted,
        long chunksDeleted,
        long workDeleted,
        long commandsDeleted,
        Instant completedAt,
        boolean customerSourceObjectsDeleted
    ) { }

    private record Boundary(String tenantId, String customerId, String deploymentId) { }
    private record PreparedDocument(DocumentIngestionPlan plan, DocumentIngestionManifest manifest, String contentFingerprint) { }
    private record WorkEvaluation(
        boolean terminal,
        boolean successful,
        boolean failed,
        boolean empty,
        String errorCode,
        String message
    ) {
        private static WorkEvaluation success() {
            return new WorkEvaluation(true, true, false, false, null, null);
        }
        private static WorkEvaluation inProgress() {
            return new WorkEvaluation(false, false, false, false, null, null);
        }
        private static WorkEvaluation emptyEvaluation() {
            return new WorkEvaluation(false, false, false, true, null, null);
        }
        private static WorkEvaluation failure(String code, String message) {
            return new WorkEvaluation(
                true,
                false,
                true,
                false,
                StringUtils.hasText(code) ? code : "INDEXING_WORK_FAILED",
                StringUtils.hasText(message) ? message : "Document indexing work requires operator review."
            );
        }
    }
}
