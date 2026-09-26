package com.ai.fabric.runtime.documents;

import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.config.AIIndexingProperties;
import ai.fabric.core.AICoreService;
import ai.fabric.dto.AIEntityConfig;
import ai.fabric.dto.AIEntityIndexingPolicy;
import ai.fabric.dto.AIMetadataField;
import ai.fabric.entity.IndexingQueueEntry;
import ai.fabric.indexing.api.AIIndexWorkType;
import ai.fabric.indexing.api.IndexingStrategy;
import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkState;
import ai.fabric.indexing.api.IndexingWorkStatus;
import ai.fabric.indexing.document.DocumentChunkIdentity;
import ai.fabric.indexing.document.DocumentEntityPolicyValidator;
import ai.fabric.indexing.document.DocumentIndexingQueueAdapter;
import ai.fabric.indexing.document.DocumentManifestOperations;
import ai.fabric.indexing.document.DocumentMetadataNormalizer;
import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import ai.fabric.indexing.document.springai.SpringAiDocumentIndexingAdapter;
import ai.fabric.indexing.document.springai.SpringAiDocumentReaderFactory;
import ai.fabric.indexing.model.AIIndexDocument;
import ai.fabric.indexing.queue.IndexingQueueService;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.documents.entity.DocumentManifestEntity;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentCommandRepository;
import com.ai.fabric.runtime.documents.repository.DocumentManifestChunkRepository;
import com.ai.fabric.runtime.documents.repository.DocumentManifestRepository;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import com.ai.fabric.runtime.documents.repository.DocumentWorkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@EntityScan(basePackageClasses = DocumentSourceEntity.class)
@EnableJpaRepositories(basePackageClasses = DocumentSourceRepository.class)
class DocumentKnowledgeServiceLifecycleTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-26T02:00:00Z"),
        ZoneOffset.UTC
    );

    @TempDir
    Path testRoot;

    @jakarta.annotation.Resource
    private DocumentSourceRepository sourceRepository;

    @jakarta.annotation.Resource
    private DocumentManifestRepository manifestRepository;

    @jakarta.annotation.Resource
    private DocumentManifestChunkRepository chunkRepository;

    @jakarta.annotation.Resource
    private DocumentWorkRepository workRepository;

    @jakarta.annotation.Resource
    private DocumentCommandRepository commandRepository;

    private final IndexingQueueService queueService = mock(IndexingQueueService.class);
    private final IndexingWorkQuery workQuery = mock(IndexingWorkQuery.class);
    private final AICoreService coreService = mock(AICoreService.class);
    private final Map<String, IndexingWorkStatus> workStatuses = new LinkedHashMap<>();
    private final List<AIIndexDocument> queuedDocuments = new ArrayList<>();
    private final AtomicLong workSequence = new AtomicLong();

    private Path sourceRoot;
    private DocumentKnowledgeProperties documentProperties;
    private DocumentSourceConnector documentConnector;
    private SpringAiDocumentIndexingAdapter planningAdapter;
    private DocumentIndexingQueueAdapter queueAdapter;
    private DocumentKnowledgeService service;

    @BeforeEach
    void setUp() throws Exception {
        sourceRoot = Files.createDirectories(testRoot.resolve("customer-source"));
        Path runtimeTemp = testRoot.resolve("runtime-temp");
        DocumentTemporaryFileManager temporaryFiles = new DocumentTemporaryFileManager(
            runtimeTemp,
            Duration.ofHours(1),
            CLOCK
        );
        temporaryFiles.initialize();

        AIEntityConfigurationLoader configurationLoader = mock(AIEntityConfigurationLoader.class);
        when(configurationLoader.getEntityConfig("document")).thenReturn(
            AIEntityConfig.builder()
                .entityType("document")
                .indexing(AIEntityIndexingPolicy.builder().enabled(true).build())
                .metadataFields(List.of(
                    AIMetadataField.builder().name(DocumentMetadataKeys.TENANT_ID).required(true).build(),
                    AIMetadataField.builder().name(DocumentMetadataKeys.SOURCE_ID).required(true).build(),
                    AIMetadataField.builder().name(DocumentMetadataKeys.SOURCE_VERSION).required(true).build()
                ))
                .build()
        );
        when(queueService.enqueue(
            any(AIIndexDocument.class),
            any(IndexingStrategy.class),
            any(LocalDateTime.class)
        )).thenAnswer(invocation -> accept(
            invocation.getArgument(0),
            invocation.getArgument(1)
        ));
        when(workQuery.findByWorkId(any(String.class))).thenAnswer(invocation ->
            Optional.ofNullable(workStatuses.get(invocation.getArgument(0)))
        );

        DocumentChunkIdentity identity = new DocumentChunkIdentity();
        DocumentEntityPolicyValidator entityPolicy = new DocumentEntityPolicyValidator(configurationLoader);
        DocumentManifestOperations manifestOperations = new DocumentManifestOperations(identity, entityPolicy);
        planningAdapter = new SpringAiDocumentIndexingAdapter(
            new AIIndexingProperties.DocumentProperties(),
            identity,
            new DocumentMetadataNormalizer(),
            entityPolicy,
            manifestOperations
        );
        queueAdapter = new DocumentIndexingQueueAdapter(
            queueService,
            identity,
            entityPolicy,
            manifestOperations
        );

        documentProperties = properties();
        documentConnector = new MountedFolderDocumentSourceConnector(
            sourceRoot,
            "dsh-test-binding",
            documentProperties.getPolicy(),
            temporaryFiles
        );
        service = newService();
    }

    private DocumentKnowledgeService newService() {
        return new DocumentKnowledgeService(
            documentProperties,
            documentConnector,
            sourceRepository,
            manifestRepository,
            chunkRepository,
            workRepository,
            commandRepository,
            new SpringAiDocumentReaderFactory(),
            planningAdapter,
            queueAdapter,
            workQuery,
            coreService,
            new DocumentActiveVersionFilter(sourceRepository),
            new ObjectMapper(),
            CLOCK
        );
    }

    @Test
    void previewIsBoundedAndHasNoQueueSideEffect() throws Exception {
        writeSource("handbook.txt", "Reset credentials and notify the account owner. ".repeat(20), 1);
        var source = register("handbook.txt", "register-preview");

        var preview = service.preview(tenantA(), source.sourceId());

        assertThat(preview.planId()).startsWith("aiplan-");
        assertThat(preview.chunkCount()).isPositive();
        assertThat(preview.chunks()).isNotEmpty();
        assertThat(preview.chunks().getFirst().contentPreview()).hasSizeLessThanOrEqualTo(120);
        assertThat(preview.toString()).doesNotContain(sourceRoot.toString());
        verifyNoInteractions(queueService);
    }

    @Test
    void jsonReaderUsesConfiguredContentFieldsAndCompletesTheIndexLifecycle() throws Exception {
        writeSource(
            "service-policy.json",
            """
                {
                  "title": "Service policy",
                  "content": "Brake fluid inspection is included in the annual service plan.",
                  "body": "Customers may book the service online or by telephone.",
                  "internalNote": "This field is not a configured content source."
                }
                """,
            1
        );
        var source = register("service-policy.json", "register-json");

        var preview = service.preview(tenantA(), source.sourceId());

        assertThat(preview.chunkCount()).isPositive();
        assertThat(preview.chunks())
            .anySatisfy(chunk -> assertThat(chunk.contentPreview()).contains("Brake fluid inspection"));
        assertThat(preview.chunks())
            .anySatisfy(chunk -> assertThat(chunk.contentPreview()).contains("book the service"));
        assertThat(preview.chunks())
            .allSatisfy(chunk -> assertThat(chunk.contentPreview()).doesNotContain("internalNote"));
        verifyNoInteractions(queueService);

        var submitted = service.index(tenantA(), source.sourceId(), "index-json");
        assertThat(submitted.outcome()).isEqualTo("ACCEPTED");
        assertThat(queuedDocuments)
            .anySatisfy(document -> assertThat(document.semanticSearchText()).contains("Brake fluid inspection"));

        complete(indexWorkIds(submitted));
        var reconciled = service.reconcile(tenantA(), source.sourceId(), "reconcile-json");
        assertThat(reconciled.source().status()).isEqualTo("ACTIVE");
        assertThat(reconciled.source().activeVersion()).isEqualTo(1L);
    }

    @Test
    void indexingActivatesOnlyAfterEveryAcceptedWorkItemCompletes() throws Exception {
        writeSource("runbook.txt", "Stable account recovery guidance. ".repeat(60), 1);
        var source = register("runbook.txt", "register-index");

        var submitted = service.index(tenantA(), source.sourceId(), "index-v1");

        assertThat(submitted.outcome()).isEqualTo("ACCEPTED");
        assertThat(submitted.source().status()).isEqualTo("INDEXING");
        assertThat(submitted.source().activeVersion()).isNull();
        assertThat(indexWorkIds(submitted)).isNotEmpty();

        complete(indexWorkIds(submitted));
        var reconciled = service.reconcile(tenantA(), source.sourceId(), "reconcile-v1");

        assertThat(reconciled.source().status()).isEqualTo("ACTIVE");
        assertThat(reconciled.source().activeVersion()).isEqualTo(1L);
        assertThat(reconciled.manifest().state()).isEqualTo("ACTIVE");
    }

    @Test
    void pendingIndexReconcilesFromPersistedStateAfterRuntimeServiceRestart() throws Exception {
        writeSource("restart-runbook.txt", "Durable restart recovery guidance. ".repeat(60), 1);
        var source = register("restart-runbook.txt", "register-restart");
        var submitted = service.index(tenantA(), source.sourceId(), "index-before-restart");
        complete(indexWorkIds(submitted));

        service = newService();
        service.reconcilePending();

        var recovered = service.detail(tenantA(), source.sourceId());
        assertThat(recovered.source().status()).isEqualTo("ACTIVE");
        assertThat(recovered.source().activeVersion()).isEqualTo(1L);
        assertThat(recovered.manifests())
            .singleElement()
            .satisfies(manifest -> assertThat(manifest.state()).isEqualTo("ACTIVE"));
    }

    @Test
    void partialQueueAcceptanceFailsClosedAndCleansExactCandidateIds() throws Exception {
        writeSource("large-runbook.txt", "Boundary and recovery guidance. ".repeat(200), 1);
        var source = register("large-runbook.txt", "register-partial");
        AtomicInteger submission = new AtomicInteger();
        when(queueService.enqueue(
            any(AIIndexDocument.class),
            any(IndexingStrategy.class),
            any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            if (submission.incrementAndGet() == 2) {
                throw new IllegalStateException("simulated partial queue outage");
            }
            return accept(invocation.getArgument(0), invocation.getArgument(1));
        });

        var result = service.index(tenantA(), source.sourceId(), "index-partial");

        assertThat(result.outcome()).isEqualTo("FAILED");
        assertThat(result.source().status()).isEqualTo("FAILED");
        assertThat(result.source().activeVersion()).isNull();
        assertThat(result.manifest().state()).isEqualTo("INDEX_PARTIAL");
        assertThat(result.manifest().acceptedIndexWorkCount()).isEqualTo(1);

        complete(indexWorkIds(result));
        var cleanup = service.reconcile(tenantA(), source.sourceId(), "reconcile-partial");
        assertThat(cleanup.manifest().state()).isEqualTo("DELETE_SUBMITTED");
        assertThat(queuedDocuments.stream().filter(document -> document.workType() == AIIndexWorkType.DELETE))
            .hasSize(result.manifest().chunkCount());

        complete(deleteWorkIds(service.detail(tenantA(), source.sourceId()).manifests().getFirst()));
        var terminal = service.reconcile(tenantA(), source.sourceId(), "reconcile-partial-cleanup");
        assertThat(terminal.manifest().state()).isEqualTo("FAILED");
        assertThat(terminal.source().activeVersion()).isNull();
    }

    @Test
    void successfulReplacementActivatesNewVersionBeforeDeletingTheOldManifest() throws Exception {
        writeSource("policy.txt", "Version one policy evidence. ".repeat(30), 1);
        var source = register("policy.txt", "register-replacement");
        var first = service.index(tenantA(), source.sourceId(), "index-replacement-v1");
        complete(indexWorkIds(first));
        service.reconcile(tenantA(), source.sourceId(), "reconcile-replacement-v1");
        List<String> oldEntityIds = entityIds(first);
        queuedDocuments.clear();

        writeSource("policy.txt", "Version two policy evidence with owner approval. ".repeat(35), 2);
        var refreshed = service.refresh(tenantA(), source.sourceId(), "refresh-replacement-v2");
        assertThat(refreshed.outcome()).isEqualTo("CHANGED");
        assertThat(refreshed.source().candidateVersion()).isEqualTo(2L);
        assertThat(refreshed.source().activeVersion()).isEqualTo(1L);

        var replacement = service.index(tenantA(), source.sourceId(), "index-replacement-v2");
        assertThat(replacement.source().status()).isEqualTo("REPLACING");
        assertThat(replacement.source().activeVersion()).isEqualTo(1L);
        complete(indexWorkIds(replacement));
        var activated = service.reconcile(tenantA(), source.sourceId(), "reconcile-replacement-v2");

        assertThat(activated.source().activeVersion()).isEqualTo(2L);
        assertThat(queuedDocuments.stream()
            .filter(document -> document.workType() == AIIndexWorkType.DELETE)
            .map(AIIndexDocument::entityId))
            .containsExactlyElementsOf(oldEntityIds);
        var oldManifest = service.detail(tenantA(), source.sourceId()).manifests().stream()
            .filter(manifest -> manifest.sourceVersion() == 1L)
            .findFirst()
            .orElseThrow();
        assertThat(oldManifest.state()).isEqualTo("DELETE_SUBMITTED");
        complete(deleteWorkIds(oldManifest));
        service.reconcile(tenantA(), source.sourceId(), "reconcile-old-delete");
        assertThat(service.detail(tenantA(), source.sourceId()).manifests())
            .anySatisfy(manifest -> {
                assertThat(manifest.sourceVersion()).isEqualTo(1L);
                assertThat(manifest.state()).isEqualTo("DELETED");
            })
            .anySatisfy(manifest -> {
                assertThat(manifest.sourceVersion()).isEqualTo(2L);
                assertThat(manifest.state()).isEqualTo("ACTIVE");
            });
    }

    @Test
    void failedReplacementPreservesThePreviouslyActiveVersion() throws Exception {
        writeSource("failure-policy.txt", "Stable version one evidence. ".repeat(30), 1);
        var source = register("failure-policy.txt", "register-failed-replacement");
        var first = service.index(tenantA(), source.sourceId(), "index-failed-v1");
        complete(indexWorkIds(first));
        service.reconcile(tenantA(), source.sourceId(), "reconcile-failed-v1");

        writeSource("failure-policy.txt", "Candidate version two evidence. ".repeat(35), 2);
        service.refresh(tenantA(), source.sourceId(), "refresh-failed-v2");
        var candidate = service.index(tenantA(), source.sourceId(), "index-failed-v2");
        List<String> candidateWork = indexWorkIds(candidate);
        fail(candidateWork.getFirst(), "EMBEDDING_PROVIDER_FAILED");
        candidateWork.stream().skip(1).forEach(this::complete);

        service.reconcile(tenantA(), source.sourceId(), "reconcile-failed-v2");
        var detail = service.detail(tenantA(), source.sourceId());

        assertThat(detail.source().status()).isEqualTo("ACTIVE");
        assertThat(detail.source().activeVersion()).isEqualTo(1L);
        assertThat(detail.source().failureCode()).isEqualTo("EMBEDDING_PROVIDER_FAILED");
        assertThat(detail.manifests())
            .anySatisfy(manifest -> {
                assertThat(manifest.sourceVersion()).isEqualTo(1L);
                assertThat(manifest.state()).isEqualTo("ACTIVE");
            })
            .anySatisfy(manifest -> {
                assertThat(manifest.sourceVersion()).isEqualTo(2L);
                assertThat(manifest.state()).isEqualTo("DELETE_SUBMITTED");
                assertThat(manifest.deletePurpose()).isEqualTo("FAILED_CANDIDATE_CLEANUP");
            });
    }

    @Test
    void deletionIsIdempotentWaitsForExactWorkAndNeverRemovesTheCustomerObject() throws Exception {
        Path customerObject = writeSource("delete-me.txt", "Source object must survive index removal. ".repeat(30), 1);
        var source = register("delete-me.txt", "register-delete");
        var indexed = service.index(tenantA(), source.sourceId(), "index-delete");
        complete(indexWorkIds(indexed));
        service.reconcile(tenantA(), source.sourceId(), "reconcile-delete-index");

        var deletion = service.removeIndex(tenantA(), source.sourceId(), "delete-index");
        assertThat(deletion.source().status()).isEqualTo("DELETING");
        assertThat(deletion.manifest().state()).isEqualTo("DELETE_SUBMITTED");

        var replay = service.removeIndex(tenantA(), source.sourceId(), "delete-index");
        assertThat(replay.outcome()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(replay.source().status()).isEqualTo("DELETING");
        var secondCommand = service.removeIndex(tenantA(), source.sourceId(), "delete-index-second-command");
        assertThat(secondCommand.source().status()).isEqualTo("DELETING");
        assertThat(Files.exists(customerObject)).isTrue();

        complete(deleteWorkIds(deletion.manifest()));
        var terminal = service.reconcile(tenantA(), source.sourceId(), "reconcile-delete-terminal");
        assertThat(terminal.source().status()).isEqualTo("DELETED");
        assertThat(terminal.source().activeVersion()).isNull();
        assertThat(Files.readString(customerObject)).contains("Source object must survive");

        var restored = service.register(
            tenantA(),
            new DocumentKnowledgeService.RegisterSourceCommand(
                "document-knowledge", "delete-me.txt", "tenant", Map.of()
            ),
            "register-after-delete"
        );
        assertThat(restored.status()).isEqualTo("REGISTERED");
        assertThat(restored.candidateVersion()).isEqualTo(2L);
    }

    @Test
    void retentionPrunesOnlyExpiredDeletedEvidenceInsideTheVerifiedBoundary() throws Exception {
        Path customerObject = writeSource(
            "retained-customer-source.txt",
            "Customer-owned source content remains outside the runtime lifecycle. ".repeat(30),
            1
        );
        var source = register("retained-customer-source.txt", "register-retention");
        var indexed = service.index(tenantA(), source.sourceId(), "index-retention");
        complete(indexWorkIds(indexed));
        service.reconcile(tenantA(), source.sourceId(), "reconcile-retention-index");
        var deletion = service.removeIndex(tenantA(), source.sourceId(), "delete-retention-index");
        complete(deleteWorkIds(deletion.manifest()));
        service.reconcile(tenantA(), source.sourceId(), "reconcile-retention-delete");

        writeSource("active-retention-control.txt", "Active evidence must never be pruned. ".repeat(30), 2);
        var activeSource = register("active-retention-control.txt", "register-active-retention");
        var activeIndex = service.index(tenantA(), activeSource.sourceId(), "index-active-retention");
        complete(indexWorkIds(activeIndex));
        service.reconcile(tenantA(), activeSource.sourceId(), "reconcile-active-retention");

        service.register(
            deploymentB(),
            new DocumentKnowledgeService.RegisterSourceCommand(
                "document-knowledge", "retained-customer-source.txt", "tenant", Map.of()
            ),
            "other-deployment-command"
        );

        Instant expired = CLOCK.instant().minus(Duration.ofDays(31));
        DocumentManifestEntity deletedManifest = manifestRepository.findById(deletion.manifest().manifestId()).orElseThrow();
        deletedManifest.setUpdatedAt(expired);
        manifestRepository.saveAndFlush(deletedManifest);
        DocumentManifestEntity activeManifest = manifestRepository.findById(activeIndex.manifest().manifestId()).orElseThrow();
        activeManifest.setUpdatedAt(expired);
        manifestRepository.saveAndFlush(activeManifest);
        commandRepository.findAll().forEach(command -> command.setCreatedAt(expired));
        commandRepository.flush();

        var result = service.cleanupRetention(tenantA());

        assertThat(result.scope()).isEqualTo("VERIFIED_BOUNDARY");
        assertThat(result.manifestsDeleted()).isEqualTo(1);
        assertThat(result.chunksDeleted()).isPositive();
        assertThat(result.workDeleted()).isPositive();
        assertThat(result.commandsDeleted()).isPositive();
        assertThat(result.customerSourceObjectsDeleted()).isFalse();
        assertThat(manifestRepository.findById(deletedManifest.getManifestId())).isEmpty();
        assertThat(manifestRepository.findById(activeManifest.getManifestId())).isPresent();
        assertThat(service.detail(tenantA(), source.sourceId()).source().status()).isEqualTo("DELETED");
        assertThat(Files.exists(customerObject)).isTrue();
        assertThat(commandRepository.findAll())
            .anySatisfy(command -> assertThat(command.getDeploymentId()).isEqualTo("deployment-b"));
        assertThat(service.retentionStatus(tenantA()).lastCleanup()).isEqualTo(result);
    }

    @Test
    void identityMetadataAndIdempotencyBoundariesFailClosed() throws Exception {
        writeSource("isolated.txt", "Tenant A only evidence", 1);
        var source = register("isolated.txt", "register-isolated");

        assertThatThrownBy(() -> service.detail(tenantB(), source.sourceId()))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.detail(deploymentB(), source.sourceId()))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.register(
            tenantA(),
            new DocumentKnowledgeService.RegisterSourceCommand(
                "document-knowledge",
                "isolated.txt",
                "tenant",
                Map.of("tenantId", "attacker")
            ),
            "metadata-override"
        )).isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("INVALID_DOCUMENT_REQUEST");

        var firstRefresh = service.refresh(tenantA(), source.sourceId(), "stable-refresh-key");
        assertThat(firstRefresh.outcome()).isEqualTo("UNCHANGED");
        writeSource("isolated.txt", "A later revision must not reuse an old command key", 2);
        var replay = service.refresh(tenantA(), source.sourceId(), "stable-refresh-key");
        assertThat(replay.outcome()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(replay.source().candidateVersion()).isEqualTo(1L);
    }

    private DocumentKnowledgeProperties properties() {
        DocumentKnowledgeProperties properties = new DocumentKnowledgeProperties();
        properties.setEnabled(true);
        properties.setEntityType("document");
        properties.setAllowedDatasetIds(List.of("document-knowledge"));
        properties.setDatasetHandleRefsJson("{\"document-knowledge\":\"kh-document-test\"}");
        properties.setEvidenceRetention(Duration.ofDays(30));
        properties.setCommandRetention(Duration.ofDays(30));
        properties.setRetentionBatchSize(100);
        properties.setMaxAutomaticDeleteAttempts(3);
        properties.getConnector().setType(DocumentKnowledgeProperties.ConnectorType.MOUNTED_FOLDER);
        properties.getConnector().setBindingRef("dsh-test-binding");
        properties.getPolicy().setMaxSourceBytes(1_000_000);
        properties.getPolicy().setMaxSources(20);
        properties.getPolicy().setMaxTotalIndexedBytes(5_000_000);
        properties.getPolicy().setMaxChunksPerSource(100);
        properties.getPolicy().setMaxChunkCharacters(8_000);
        properties.getPolicy().setMaxTotalCharacters(500_000);
        properties.getPolicy().setPreviewMaxChunks(5);
        properties.getPolicy().setPreviewMaxCharactersPerChunk(120);
        return properties;
    }

    private DocumentKnowledgeService.SourceSummary register(String reference, String key) {
        return service.register(
            tenantA(),
            new DocumentKnowledgeService.RegisterSourceCommand(
                "document-knowledge",
                reference,
                "tenant",
                Map.of("sourceCategory", "test")
            ),
            key
        );
    }

    private Path writeSource(String reference, String content, int revision) throws Exception {
        Path path = sourceRoot.resolve(reference);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setLastModifiedTime(path, FileTime.from(Instant.parse("2026-09-26T02:00:00Z").plusSeconds(revision)));
        return path;
    }

    private RuntimeResolvedIdentity tenantA() {
        return identity("tenant-a", "customer-a", "deployment-a");
    }

    private RuntimeResolvedIdentity tenantB() {
        return identity("tenant-b", "customer-a", "deployment-a");
    }

    private RuntimeResolvedIdentity deploymentB() {
        return identity("tenant-a", "customer-a", "deployment-b");
    }

    private RuntimeResolvedIdentity identity(String tenant, String customer, String deployment) {
        return RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .tenantId(tenant)
                .customerId(customer)
                .deploymentId(deployment)
                .build())
            .warnings(List.of())
            .build();
    }

    private IndexingQueueEntry accept(AIIndexDocument document, IndexingStrategy strategy) {
        long id = workSequence.incrementAndGet();
        queuedDocuments.add(document);
        IndexingQueueEntry entry = new IndexingQueueEntry();
        ReflectionTestUtils.setField(entry, "id", id);
        workStatuses.put(String.valueOf(id), status(
            String.valueOf(id), document, strategy, IndexingWorkState.PENDING, null, null
        ));
        return entry;
    }

    private void complete(List<String> workIds) {
        workIds.forEach(this::complete);
    }

    private void complete(String workId) {
        IndexingWorkStatus current = workStatuses.get(workId);
        workStatuses.put(workId, status(workId, current, IndexingWorkState.COMPLETED, null, null));
    }

    private void fail(String workId, String code) {
        IndexingWorkStatus current = workStatuses.get(workId);
        workStatuses.put(workId, status(
            workId, current, IndexingWorkState.DEAD_LETTER, code, "Simulated provider failure"
        ));
    }

    private List<String> indexWorkIds(DocumentKnowledgeService.OperationResult result) {
        return result.manifest().work().stream()
            .filter(work -> "INDEX".equals(work.operation()))
            .filter(work -> work.submissionAttempt() == result.manifest().indexSubmissionAttempt())
            .map(DocumentKnowledgeService.WorkSummary::workId)
            .toList();
    }

    private List<String> deleteWorkIds(DocumentKnowledgeService.ManifestSummary manifest) {
        return manifest.work().stream()
            .filter(work -> "DELETE".equals(work.operation()))
            .filter(work -> work.submissionAttempt() == manifest.deleteSubmissionAttempt())
            .map(DocumentKnowledgeService.WorkSummary::workId)
            .toList();
    }

    private List<String> entityIds(DocumentKnowledgeService.OperationResult result) {
        return chunkRepository.findByManifestIdOrderByChunkIndexAsc(result.manifest().manifestId()).stream()
            .map(chunk -> chunk.getEntityId())
            .toList();
    }

    private IndexingWorkStatus status(
        String workId,
        AIIndexDocument document,
        IndexingStrategy strategy,
        IndexingWorkState state,
        String errorCode,
        String reason
    ) {
        LocalDateTime now = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        return new IndexingWorkStatus(
            workId,
            document.entityType(),
            document.entityId(),
            document.workType(),
            document.sourceOperation(),
            strategy,
            state,
            0,
            5,
            errorCode,
            reason,
            document.correlationId(),
            now,
            now,
            null,
            state.isTerminal() ? now : null,
            state.requiresOperatorReview() ? now : null,
            now
        );
    }

    private IndexingWorkStatus status(
        String workId,
        IndexingWorkStatus current,
        IndexingWorkState state,
        String errorCode,
        String reason
    ) {
        LocalDateTime now = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        return new IndexingWorkStatus(
            workId,
            current.entityType(),
            current.entityId(),
            current.workType(),
            current.sourceOperation(),
            current.strategy(),
            state,
            current.retryCount(),
            current.maxRetries(),
            errorCode,
            reason,
            current.correlationId(),
            current.requestedAt(),
            current.scheduledFor(),
            current.startedAt(),
            state.isTerminal() ? now : null,
            state.requiresOperatorReview() ? now : null,
            now
        );
    }
}
