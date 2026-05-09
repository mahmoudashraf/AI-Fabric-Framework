package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentAssignmentRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentVerificationRolloutService {

    private static final String HARD_RESET_REASON = "Canonical verification rollout hard reset";
    private static final String ENVIRONMENT = "dev";
    private static final String CURATED_MODULE_ID = "commerce";
    private static final String ECOMMERCE_ACTIONS_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/runtime/config/ai-actions.yml";
    private static final String ECOMMERCE_ENTITIES_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/runtime/config/ai-entity-config.yml";
    private static final String ECOMMERCE_ROUTING_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/rest-connector/actions-routing.yml";
    private static final String ECOMMERCE_UPSTREAM_BASE_URL = "https://ai-fabric-framework-production-a247.up.railway.app";
    private static final String PUBLIC_RUNTIME_TOKEN_ISSUER = "ecommerce-demo";
    private static final String PUBLIC_RUNTIME_ACCEPTED_ISSUERS =
        PUBLIC_RUNTIME_TOKEN_ISSUER + ",runtime-public-bootstrap";
    private static final String PUBLIC_RUNTIME_ACCEPTED_AUDIENCES = "ecommerce-demo-chat";
    private static final String PUBLIC_RUNTIME_DEFAULT_AUDIENCE = "ecommerce-demo-chat";
    private static final double DEFAULT_RAG_SIMILARITY_THRESHOLD = 0.1d;
    private static final boolean DEFAULT_SMART_SUGGESTIONS_ENABLED = false;
    private static final String MARKETPLACE_KNOWLEDGE_SOURCE_ID = "deployment-marketplace-knowledge";
    private static final String MARKETPLACE_SHARED_POLICY_SOURCE_ID = "shared-marketplace-refund-policy";
    private static final String MARKETPLACE_SHARED_POLICY_HANDLE_REF = "commerce-catalog/refund-policy";
    private static final String MARKETPLACE_SHARED_POLICY_DATASET_ID = "shared-marketplace-refund-policy-seed";
    private static final String MARKETPLACE_SHARED_POLICY_PLUGIN_ID = "platform-marketplace-runtime-rollout";
    private static final String MARKETPLACE_SHARED_POLICY_PLUGIN_VERSION_ID = "platform-marketplace-runtime-rollout-v1";
    private static final String MARKETPLACE_SHARED_POLICY_DATASET_HASH = "marketplace-runtime-refund-policy-v1";
    private static final String MARKETPLACE_SHARED_POLICY_DATASET_REF =
        "classpath:marketplace/datasets/verification/refund-policy.jsonl";
    private static final int ECOMMERCE_VECTOR_DIMENSIONS = 512;
    private static final int OPENAI_VECTOR_DIMENSIONS = 1536;
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final String QDRANT_PROVIDER = "aws";
    private static final String QDRANT_REGION = "eu-west-1";
    private static final String TEST_WEAVIATE_HOST = "weaviate.example.test";
    private static final String ZILLIZ_PROJECT_ID = "proj-a58a34b87ccfe2c80d6ec2";
    private static final String ZILLIZ_REGION_ID = "aws-eu-central-1";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentService deploymentService;
    private final DeploymentReleaseRecoveryService deploymentReleaseRecoveryService;
    private final DeploymentAssignmentRepository deploymentAssignmentRepository;
    private final DeploymentAssignmentService deploymentAssignmentService;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformSecretService platformSecretService;
    private final DeploymentVectorizationVerificationService deploymentVectorizationVerificationService;
    private final VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository;
    private final VectorizationPlanRepository vectorizationPlanRepository;
    private final VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final ResourceLoader resourceLoader;
    private final PlatformVerificationSuiteProperties suiteProperties;
    private final String ecommerceUpstreamBaseUrl;

    @Autowired
    public DeploymentVerificationRolloutService(DeploymentRepository deploymentRepository,
                                                DeploymentReleaseRepository releaseRepository,
                                                DeploymentVersionRepository deploymentVersionRepository,
                                                DeploymentService deploymentService,
                                                DeploymentReleaseRecoveryService deploymentReleaseRecoveryService,
                                                DeploymentAssignmentRepository deploymentAssignmentRepository,
                                                DeploymentAssignmentService deploymentAssignmentService,
                                                PlatformUserRepository platformUserRepository,
                                                PlatformSecretService platformSecretService,
                                                DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                                VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                                VectorizationPlanRepository vectorizationPlanRepository,
                                                VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                                PlatformVerificationSuiteProperties suiteProperties,
                                                @Value("${platform.verification.suites.ecommerce-upstream-base-url:}") String ecommerceUpstreamBaseUrl,
                                                ObjectMapper objectMapper,
                                                ResourceLoader resourceLoader,
                                                @Qualifier("canonicalRolloutExecutor") Executor rolloutExecutor) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentService = deploymentService;
        this.deploymentReleaseRecoveryService = deploymentReleaseRecoveryService;
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.deploymentAssignmentService = deploymentAssignmentService;
        this.platformUserRepository = platformUserRepository;
        this.platformSecretService = platformSecretService;
        this.deploymentVectorizationVerificationService = deploymentVectorizationVerificationService;
        this.vectorizationSourceConnectionRepository = vectorizationSourceConnectionRepository;
        this.vectorizationPlanRepository = vectorizationPlanRepository;
        this.vectorizationPlanRevisionRepository = vectorizationPlanRevisionRepository;
        this.objectMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceLoader = resourceLoader;
        this.suiteProperties = suiteProperties;
        this.ecommerceUpstreamBaseUrl = normalizeBaseUrl(firstNonBlank(ecommerceUpstreamBaseUrl, ECOMMERCE_UPSTREAM_BASE_URL));
    }

    public DeploymentVerificationRolloutService(DeploymentRepository deploymentRepository,
                                                DeploymentReleaseRepository releaseRepository,
                                                DeploymentService deploymentService,
                                                DeploymentReleaseRecoveryService deploymentReleaseRecoveryService,
                                                DeploymentAssignmentRepository deploymentAssignmentRepository,
                                                DeploymentAssignmentService deploymentAssignmentService,
                                                PlatformUserRepository platformUserRepository,
                                                PlatformSecretService platformSecretService,
                                                DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                                VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                                VectorizationPlanRepository vectorizationPlanRepository,
                                                VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                                ObjectMapper objectMapper,
                                                ResourceLoader resourceLoader,
                                                Executor rolloutExecutor) {
        this(
            deploymentRepository,
            releaseRepository,
            null,
            deploymentService,
            deploymentReleaseRecoveryService,
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            vectorizationSourceConnectionRepository,
            vectorizationPlanRepository,
            vectorizationPlanRevisionRepository,
            defaultSuiteProperties(),
            ECOMMERCE_UPSTREAM_BASE_URL,
            objectMapper,
            resourceLoader,
            rolloutExecutor
        );
    }

    public DeploymentVerificationRolloutService(DeploymentRepository deploymentRepository,
                                                DeploymentReleaseRepository releaseRepository,
                                                DeploymentService deploymentService,
                                                DeploymentReleaseRecoveryService deploymentReleaseRecoveryService,
                                                DeploymentAssignmentRepository deploymentAssignmentRepository,
                                                DeploymentAssignmentService deploymentAssignmentService,
                                                PlatformUserRepository platformUserRepository,
                                                PlatformSecretService platformSecretService,
                                                DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                                VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                                VectorizationPlanRepository vectorizationPlanRepository,
                                                VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                                ObjectMapper objectMapper,
                                                ResourceLoader resourceLoader) {
        this(
            deploymentRepository,
            releaseRepository,
            null,
            deploymentService,
            deploymentReleaseRecoveryService,
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            vectorizationSourceConnectionRepository,
            vectorizationPlanRepository,
            vectorizationPlanRevisionRepository,
            defaultSuiteProperties(),
            ECOMMERCE_UPSTREAM_BASE_URL,
            objectMapper,
            resourceLoader,
            Runnable::run
        );
    }

    DeploymentVerificationRolloutService(DeploymentRepository deploymentRepository,
                                         DeploymentReleaseRepository releaseRepository,
                                         DeploymentService deploymentService,
                                         DeploymentAssignmentRepository deploymentAssignmentRepository,
                                         DeploymentAssignmentService deploymentAssignmentService,
                                         PlatformUserRepository platformUserRepository,
                                         PlatformSecretService platformSecretService,
                                         DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                         VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                         VectorizationPlanRepository vectorizationPlanRepository,
                                         VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                         ObjectMapper objectMapper,
                                         ResourceLoader resourceLoader) {
        this(
            deploymentRepository,
            releaseRepository,
            null,
            deploymentService,
            null,
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            vectorizationSourceConnectionRepository,
            vectorizationPlanRepository,
            vectorizationPlanRevisionRepository,
            defaultSuiteProperties(),
            ECOMMERCE_UPSTREAM_BASE_URL,
            objectMapper,
            resourceLoader,
            Runnable::run
        );
    }

    public DeploymentVerificationRolloutSummary listRollouts() {
        return buildSummary(null);
    }

    public DeploymentVerificationRolloutSummary recreateRollouts() {
        return recreateRollouts(null);
    }

    public DeploymentVerificationRolloutSummary recreateRollouts(List<String> selectedKeys) {
        List<VerificationRolloutDefinition> selected = selectedDefinitions(selectedKeys);
        executeSequentially(selected, this::ensureDeployment, "create/apply");
        return buildSummary("Created or reapplied " + selected.size() + " canonical verification rollout deployment(s) sequentially.");
    }

    public DeploymentVerificationRolloutSummary cleanupRollouts(List<String> selectedKeys) {
        List<VerificationRolloutDefinition> selected = selectedDefinitions(selectedKeys);
        int deleted = 0;
        int missing = 0;
        for (VerificationRolloutDefinition definition : selected) {
            DeploymentEntity existing = resolveExisting(deploymentRepository.findAllByOrderByCreatedAtDesc(), definition);
            if (existing == null) {
                missing++;
                continue;
            }
            if (existing.getArchivedAt() == null) {
                deploymentService.archiveDeployment(existing.getId());
            }
            deploymentService.deleteDeployment(
                existing.getId(),
                new DeleteDeploymentRequest(true, null, "Canonical verification rollout cleanup")
            );
            deleted++;
        }
        return buildSummary(
            "Queued cleanup for " + deleted + " canonical verification rollout deployment(s)."
                + (missing > 0 ? " " + missing + " selected rollout(s) were already absent." : "")
        );
    }

    public DeploymentVerificationRolloutSummary hardResetRollouts(List<String> selectedKeys) {
        List<VerificationRolloutDefinition> selected = selectedDefinitions(selectedKeys);
        List<String> blocked = new ArrayList<>();
        List<String> backgroundCleanup = new ArrayList<>();
        int missing = 0;
        int queued = 0;

        for (VerificationRolloutDefinition definition : selected) {
            DeploymentEntity existing = resolveExisting(deploymentRepository.findAllByOrderByCreatedAtDesc(), definition);
            if (existing == null) {
                missing++;
                continue;
            }

            if (existing.getArchivedAt() == null) {
                try {
                    deploymentService.archiveDeployment(existing.getId());
                    existing = deploymentRepository.findById(existing.getId()).orElse(existing);
                } catch (ResponseStatusException ex) {
                    blocked.add(
                        definition.displayName() + " (archive blocked: "
                            + defaultText(ex.getReason(), "cleanup request not accepted") + ")"
                    );
                    continue;
                }
            }

            if (isDeletionInProgress(existing)) {
                backgroundCleanup.add(definition.displayName() + " (" + existing.getDeletionStatus() + ")");
                continue;
            }

            try {
                deploymentService.deleteDeployment(
                    existing.getId(),
                    new DeleteDeploymentRequest(true, null, HARD_RESET_REASON)
                );
                queued++;
            } catch (ResponseStatusException ex) {
                blocked.add(formatResetFailure(definition, ex.getReason()));
            }
        }

        StringBuilder message = new StringBuilder();
        message.append("Force hard cleanup queued hard delete for ")
            .append(queued)
            .append(" canonical verification rollout deployment(s).");
        if (missing > 0) {
            message.append(" ")
                .append(missing)
                .append(" selected rollout(s) were already absent.");
        }
        if (!backgroundCleanup.isEmpty()) {
            message.append(" Background cleanup continues for ")
                .append(String.join(", ", backgroundCleanup))
                .append(".");
        }
        if (!blocked.isEmpty()) {
            message.append(" Blocked: ")
                .append(String.join(" | ", blocked));
        }
        return buildSummary(message.toString());
    }

    private DeploymentVerificationRolloutSummary buildSummary(String overrideSummaryMessage) {
        List<DeploymentEntity> deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
        if (deploymentReleaseRecoveryService != null) {
            boolean recovered = false;
            for (VerificationRolloutDefinition definition : definitions()) {
                DeploymentEntity existing = resolveExisting(deployments, definition);
                if (existing == null) {
                    continue;
                }
                recovered = deploymentReleaseRecoveryService.reconcileLatestInProgressRelease(existing.getId()) || recovered;
            }
            if (recovered) {
                deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
            }
        }
        List<DeploymentEntity> deploymentsSnapshot = deployments;
        List<DeploymentVerificationRolloutItemSummary> items = definitions().stream()
            .map(definition -> toSummary(definition, resolveExisting(deploymentsSnapshot, definition)))
            .toList();
        long ready = items.stream().filter(DeploymentVerificationRolloutItemSummary::verificationReady).count();
        return new DeploymentVerificationRolloutSummary(
            overrideSummaryMessage != null
                ? overrideSummaryMessage
                : ready + " of " + items.size() + " canonical verification deployments are ready to verify.",
            items
        );
    }

    private boolean isDeletionInProgress(DeploymentEntity deployment) {
        return "QUEUED".equalsIgnoreCase(deployment.getDeletionStatus())
            || "RUNNING".equalsIgnoreCase(deployment.getDeletionStatus());
    }

    private String formatResetFailure(VerificationRolloutDefinition definition, String message) {
        return definition.displayName() + ": " + defaultText(message, "Reset could not complete.");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public boolean isCanonicalRolloutDeployment(String deploymentId) {
        return canonicalVerificationProfile(deploymentId) != null;
    }

    public String canonicalVerificationProfile(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return null;
        }
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null || deployment.getArchivedAt() != null) {
            return null;
        }
        VerificationRolloutDefinition definition = resolveDefinition(deployment);
        return definition == null ? null : definition.verificationProfile();
    }

    private void ensureDeployment(VerificationRolloutDefinition definition) {
        List<DeploymentEntity> deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
        DeploymentEntity existing = resolveExisting(deployments, definition);
        String deploymentId;

        if (existing == null) {
            DeploymentSummary created = deploymentService.createDeployment(new CreateDeploymentRequest(
                definition.deploymentName(),
                ENVIRONMENT,
                definition.templateId(),
                CURATED_MODULE_ID,
                definition.vectorProvisioningMode()
            ));
            deploymentId = created.id();
        } else {
            deploymentId = existing.getId();
            if (existing.getArchivedAt() != null) {
                deploymentService.restoreDeploymentInternal(existing.getId());
            }
        }

        forceRedispatchLatestQueuedApply(deploymentId);
        ensureCanonicalOwnershipAssignments(deploymentId);
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeploymentInternal(deploymentId);
        UpdateDeploymentDraftRequest request = definition.updateDraft(draft);
        deploymentService.updateDraftInternal(draft.id(), request);
        seedCanonicalVectorization(deploymentId);

        DraftValidationResponse validation = deploymentService.validateDraftInternal(draft.id());
        if (!validation.publishReady()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Canonical verification rollout '" + definition.displayName() + "' is not publish ready: " + summarizeIssues(validation.issues())
            );
        }

        DeploymentVersionSummary version = deploymentService.publishDraftInternal(draft.id(), true);
        deploymentService.applyVersionInternal(deploymentId, version.id(), null, true);
        forceRedispatchLatestQueuedApply(deploymentId);
    }

    private void executeSequentially(List<VerificationRolloutDefinition> definitions,
                                     java.util.function.Consumer<VerificationRolloutDefinition> operation,
                                     String operationLabel) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }

        List<RolloutExecutionFailure> failures = new ArrayList<>();
        for (VerificationRolloutDefinition definition : definitions) {
            RolloutExecutionFailure failure = executeOperation(definition, operation);
            if (failure != null) {
                failures.add(failure);
            }
        }

        if (!failures.isEmpty()) {
            String details = failures.stream()
                .map(failure -> failure.displayName() + ": " + failure.message())
                .collect(java.util.stream.Collectors.joining(" | "));
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Canonical rollout " + operationLabel + " failed for " + failures.size() + " deployment(s): " + details
            );
        }
    }

    private RolloutExecutionFailure executeOperation(VerificationRolloutDefinition definition,
                                                     java.util.function.Consumer<VerificationRolloutDefinition> operation) {
        try {
            operation.accept(definition);
            return null;
        } catch (ResponseStatusException ex) {
            return new RolloutExecutionFailure(
                definition.displayName(),
                defaultText(ex.getReason(), ex.getStatusCode().toString())
            );
        } catch (Exception ex) {
            return new RolloutExecutionFailure(
                definition.displayName(),
                defaultText(ex.getMessage(), "Unexpected rollout failure.")
            );
        }
    }

    private void ensureFreshDeployment(VerificationRolloutDefinition definition) {
        DeploymentSummary created = deploymentService.createDeployment(new CreateDeploymentRequest(
            definition.deploymentName(),
            ENVIRONMENT,
            definition.templateId(),
            CURATED_MODULE_ID,
            definition.vectorProvisioningMode()
        ));
        String deploymentId = created.id();

        ensureCanonicalOwnershipAssignments(deploymentId);
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeploymentInternal(deploymentId);
        UpdateDeploymentDraftRequest request = definition.updateDraft(draft);
        deploymentService.updateDraftInternal(draft.id(), request);
        seedCanonicalVectorization(deploymentId);

        DraftValidationResponse validation = deploymentService.validateDraftInternal(draft.id());
        if (!validation.publishReady()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Canonical verification rollout '" + definition.displayName() + "' is not publish ready: " + summarizeIssues(validation.issues())
            );
        }

        DeploymentVersionSummary version = deploymentService.publishDraftInternal(draft.id(), true);
        deploymentService.applyVersionInternal(deploymentId, version.id(), null, true);
        forceRedispatchLatestQueuedApply(deploymentId);
    }

    private void forceRedispatchLatestQueuedApply(String deploymentId) {
        if (deploymentReleaseRecoveryService == null || !hasText(deploymentId)) {
            return;
        }
        deploymentReleaseRecoveryService.reconcileLatestInProgressRelease(deploymentId, true);
    }

    private void ensureCanonicalOwnershipAssignments(String deploymentId) {
        List<DeploymentAssignmentEntity> assignments = deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
        Set<String> existingRoles = assignments.stream()
            .map(DeploymentAssignmentEntity::getAssignmentRole)
            .filter(Objects::nonNull)
            .map(role -> role.trim().toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        Set<String> assignedUserIds = assignments.stream()
            .map(DeploymentAssignmentEntity::getUserId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        if (existingRoles.contains("DEPLOYMENT_ADMIN") && existingRoles.contains("DEPLOYMENT_OPERATOR")) {
            return;
        }

        List<PlatformUserEntity> eligibleUsers = platformUserRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(this::isCanonicalAssignmentCandidate)
            .filter(user -> !assignedUserIds.contains(user.getId()))
            .toList();

        PlatformUserEntity adminCandidate = eligibleUsers.stream()
            .filter(user -> PlatformRole.PLATFORM_ADMIN.name().equalsIgnoreCase(user.getRole()))
            .findFirst()
            .orElse(null);

        PlatformUserEntity operatorCandidate = eligibleUsers.stream()
            .filter(user -> adminCandidate == null || !Objects.equals(user.getId(), adminCandidate.getId()))
            .filter(user -> PlatformRole.PLATFORM_OPERATOR.name().equalsIgnoreCase(user.getRole()))
            .findFirst()
            .orElseGet(() -> eligibleUsers.stream()
                .filter(user -> adminCandidate == null || !Objects.equals(user.getId(), adminCandidate.getId()))
                .findFirst()
                .orElse(null));

        if (!existingRoles.contains("DEPLOYMENT_ADMIN") && adminCandidate != null) {
            deploymentAssignmentService.upsertAssignmentInternal(
                deploymentId,
                new UpsertDeploymentAssignmentRequest(adminCandidate.getId(), "DEPLOYMENT_ADMIN")
            );
        }
        if (!existingRoles.contains("DEPLOYMENT_OPERATOR") && operatorCandidate != null) {
            deploymentAssignmentService.upsertAssignmentInternal(
                deploymentId,
                new UpsertDeploymentAssignmentRequest(operatorCandidate.getId(), "DEPLOYMENT_OPERATOR")
            );
        }
    }

    private boolean isCanonicalAssignmentCandidate(PlatformUserEntity user) {
        if (user == null || !hasText(user.getId()) || !hasText(user.getRole())) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return false;
        }
        return PlatformRole.PLATFORM_ADMIN.name().equalsIgnoreCase(user.getRole())
            || PlatformRole.PLATFORM_OPERATOR.name().equalsIgnoreCase(user.getRole());
    }

    private DeploymentVerificationRolloutItemSummary toSummary(VerificationRolloutDefinition definition,
                                                               DeploymentEntity deployment) {
        List<String> missingPrerequisites = missingPrerequisites(definition);
        DeploymentReleaseEntity latestRelease = deployment == null
            ? null
            : releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        DeploymentVersionEntity activeVersion = deployment == null || deploymentVersionRepository == null || !hasText(deployment.getActiveVersionId())
            ? null
            : deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
        RolloutReadiness readiness = evaluateReadiness(definition, deployment, latestRelease, activeVersion, missingPrerequisites);

        return new DeploymentVerificationRolloutItemSummary(
            definition.key(),
            definition.displayName(),
            definition.description(),
            definition.verificationProfile(),
            definition.writeVerificationSupported(),
            deployment == null ? null : deployment.getId(),
            ENVIRONMENT,
            deployment != null,
            deployment != null && deployment.getArchivedAt() != null,
            readiness.ready(),
            deployment == null ? "MISSING" : deployment.getStatus(),
            deployment == null ? null : deployment.getActiveVersionId(),
            latestRelease == null ? null : latestRelease.getStatus(),
            latestRelease == null ? null : latestRelease.getProvisioningStatus(),
            latestRelease == null ? null : latestRelease.getVerificationStatus(),
            deployment == null ? null : deployment.getRuntimeBaseUrl(),
            deployment != null && hasText(deployment.getConnectorBaseUrl()),
            readiness.message(),
            readiness.repairRecommended(),
            readiness.repairReasons(),
            missingPrerequisites
        );
    }

    private RolloutReadiness evaluateReadiness(VerificationRolloutDefinition definition,
                                               DeploymentEntity deployment,
                                               DeploymentReleaseEntity latestRelease,
                                               DeploymentVersionEntity activeVersion,
                                               List<String> missingPrerequisites) {
        if (deployment == null) {
            return new RolloutReadiness(false, "This canonical rollout has not been created yet.");
        }
        if (deployment.getArchivedAt() != null) {
            return new RolloutReadiness(false, "This canonical rollout is archived and must be restored before verification.");
        }
        if (!missingPrerequisites.isEmpty()) {
            return new RolloutReadiness(false, "Missing prerequisites: " + String.join(", ", missingPrerequisites));
        }
        if (!hasText(deployment.getActiveVersionId())) {
            return new RolloutReadiness(false, "A published active version is required before hosted verification can run.");
        }
        if (!hasText(deployment.getRuntimeBaseUrl()) || !hasText(deployment.getConnectorBaseUrl())) {
            return new RolloutReadiness(
                false,
                "This rollout exists, but it is not verification-ready yet. Wait for the apply to finish so the runtime URL and internal connector service are attached."
            );
        }
        if (latestRelease == null) {
            return new RolloutReadiness(
                false,
                "Runtime and the internal connector service are live, but no release record is available for hosted verification evidence yet."
            );
        }
        if (!"APPLIED_VERIFIED".equalsIgnoreCase(latestRelease.getStatus())
            || !"PASSED".equalsIgnoreCase(latestRelease.getVerificationStatus())) {
            return new RolloutReadiness(
                false,
                "The latest release is not in a verified ready state (status="
                    + firstNonBlank(latestRelease.getStatus(), "UNKNOWN")
                    + ", verification="
                    + firstNonBlank(latestRelease.getVerificationStatus(), "UNKNOWN")
                    + "). Resolve the latest rollout failure before using this canonical deployment as verification-ready."
            );
        }
        List<String> configDriftReasons = canonicalConfigDriftReasons(definition, deployment, activeVersion);
        if (!configDriftReasons.isEmpty()) {
            return RolloutReadiness.repairable(
                "Canonical rollout config drift detected: " + String.join(", ", configDriftReasons),
                configDriftReasons
            );
        }

        DeploymentVectorizationVerificationSummary vectorization = deploymentVectorizationVerificationService == null
            ? null
            : deploymentVectorizationVerificationService.build(deployment, objectMapper.createObjectNode());
        if (vectorization != null && vectorization.planPresent()) {
            if (!vectorization.configured()) {
                return new RolloutReadiness(
                    false,
                    "Runtime and the internal connector service are live, but the canonical vectorization plan is not fully linked yet."
                );
            }
            if (vectorization.runnerRequired() && !runnerRegistrationReady(vectorization)) {
                return new RolloutReadiness(
                    false,
                    "Runtime and the internal connector service are live, but the vectorization runner registration is not active yet."
                );
            }
            if (vectorization.platformManagedRunnerExpected() && !runnerServiceProvisioned(latestRelease)) {
                return new RolloutReadiness(
                    false,
                    "Runtime and the internal connector service are live, but the managed vectorization runner service has not been provisioned on the latest release yet."
                );
            }
            return new RolloutReadiness(true, "Runtime, the internal connector service, and the vectorization runner are ready for hosted verification.");
        }

        return new RolloutReadiness(true, "Runtime and the internal connector service are live, and the rollout is ready for hosted verification.");
    }

    private List<String> canonicalConfigDriftReasons(VerificationRolloutDefinition definition,
                                                     DeploymentEntity deployment,
                                                     DeploymentVersionEntity activeVersion) {
        if (definition == null || deployment == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        String expectedBaseUrl = ecommerceUpstreamBaseUrl();
        if (activeVersion == null) {
            if (deploymentVersionRepository == null) {
                return reasons;
            }
            reasons.add("ACTIVE_VERSION_NOT_RESOLVED");
            return reasons;
        }
        JsonNode routingConfig = readJson(activeVersion.getRoutingConfigJson());
        JsonNode securityConfig = readJson(activeVersion.getSecurityConfigJson());
        String expectedAuthzMode = expectedAuthzMode(definition);
        if (!expectedAuthzMode.equalsIgnoreCase(securityConfig.path("authzMode").asText(""))) {
            reasons.add("SECURITY_AUTHZ_MODE_DRIFT");
        }
        if (!baseUrlsEqual(expectedBaseUrl, routingConfig.path("connector").path("upstream").path("base-url").asText(""))) {
            reasons.add("CONNECTOR_UPSTREAM_BASE_URL_DRIFT");
        }
        if (!baseUrlsEqual(expectedBaseUrl, routingConfig.path("authz").path("upstream").path("base-url").asText(""))) {
            reasons.add("AUTHZ_UPSTREAM_BASE_URL_DRIFT");
        }
        if (ManagedDeploymentProfileCatalog.AUTHZ_MODE_REMOTE_HTTP.equals(expectedAuthzMode)
            && !baseUrlsEqual(expectedBaseUrl, securityConfig.path("authzBaseUrl").asText(""))) {
            reasons.add("SECURITY_AUTHZ_BASE_URL_DRIFT");
        }
        if (vectorizationSourceConnectionRepository != null) {
            vectorizationSourceConnectionRepository.findByDeploymentId(deployment.getId()).ifPresent(connection -> {
                JsonNode connectionConfig = readJson(connection.getConnectionConfigJson());
                if (!baseUrlsEqual(expectedBaseUrl, connectionConfig.path("baseUrl").asText(""))) {
                    reasons.add("VECTORIZATION_SOURCE_BASE_URL_DRIFT");
                }
            });
        }
        return reasons.stream().distinct().toList();
    }

    private String expectedAuthzMode(VerificationRolloutDefinition definition) {
        return ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED;
    }

    private List<String> missingPrerequisites(VerificationRolloutDefinition definition) {
        List<String> required = new ArrayList<>(List.of(
            "OPENAI_API_KEY",
            "CONNECTOR_API_KEY",
            "ACTIONS_CONNECTOR_API_KEY",
            "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
            "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY",
            "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY"
        ));
        required.addAll(definition.requiredSecrets());
        return required.stream()
            .filter(name -> !platformSecretService.isSecretPresent(name))
            .distinct()
            .toList();
    }

    private DeploymentEntity resolveExisting(List<DeploymentEntity> deployments,
                                             VerificationRolloutDefinition definition) {
        DeploymentEntity active = deployments.stream()
            .filter(candidate -> matches(candidate, definition) && candidate.getArchivedAt() == null)
            .findFirst()
            .orElse(null);
        if (active != null) {
            return active;
        }
        return deployments.stream()
            .filter(candidate -> matches(candidate, definition))
            .findFirst()
            .orElse(null);
    }

    private boolean matches(DeploymentEntity candidate, VerificationRolloutDefinition definition) {
        return definition.deploymentName().equalsIgnoreCase(candidate.getName())
            && ENVIRONMENT.equalsIgnoreCase(candidate.getEnvironmentName());
    }

    private VerificationRolloutDefinition resolveDefinition(DeploymentEntity deployment) {
        return definitions().stream()
            .filter(definition -> matches(deployment, definition))
            .findFirst()
            .orElse(null);
    }

    private List<VerificationRolloutDefinition> selectedDefinitions(List<String> selectedKeys) {
        if (selectedKeys == null || selectedKeys.isEmpty()) {
            return definitions();
        }
        Set<String> requested = selectedKeys.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (requested.isEmpty()) {
            return definitions();
        }
        List<VerificationRolloutDefinition> selected = definitions().stream()
            .filter(definition -> requested.contains(definition.key()))
            .toList();
        if (selected.size() != requested.size()) {
            Set<String> known = definitions().stream().map(VerificationRolloutDefinition::key).collect(java.util.stream.Collectors.toSet());
            List<String> unknown = requested.stream().filter(key -> !known.contains(key)).toList();
            throw new ResponseStatusException(BAD_REQUEST, "Unknown canonical rollout preset(s): " + String.join(", ", unknown));
        }
        return selected;
    }

    private List<VerificationRolloutDefinition> definitions() {
        return List.of(
            new VerificationRolloutDefinition(
                "ecommerce",
                "Ecommerce Verification",
                "Canonical ecommerce verification deployment with the full commerce actions, entities, and upstream routing bootstrap.",
                "dev-openai-lucene",
                "LOCAL_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    return new UpdateDeploymentDraftRequest(
                        readYaml(ECOMMERCE_ACTIONS_RESOURCE),
                        ecommerceEntityConfig(),
                        ecommerceRoutingConfig(),
                        normalizeProviderConfig(draft.providerConfig(), ECOMMERCE_VECTOR_DIMENSIONS),
                        ecommerceSecurityConfig(draft.securityConfig()),
                        withDefaultPromptLatencyTuning(ensureObject(draft.promptConfig()))
                    );
                }
            },
            new VerificationRolloutDefinition(
                "marketplace",
                "Marketplace Runtime Verification",
                "Canonical marketplace-runtime verification deployment with resolved shell config, two-source retrieval, and rollout-owned shared vector backing.",
                "dev-openai-qdrant",
                "PLATFORM_MANAGED",
                "marketplace-runtime",
                false
            ) {
                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("llmProvider", "openai");
                    provider.put("orchestrationLlmProvider", "openai");
                    provider.remove("orchestrationEndpointProfile");
                    provider.remove("orchestrationManagedServiceRef");
                    provider.put("orchestrationModel", ManagedDeploymentProfileCatalog.recommendedOrchestrationModel("openai"));
                    provider.put("generationLlmProvider", "openai");
                    provider.remove("generationEndpointProfile");
                    provider.remove("generationManagedServiceRef");
                    provider.put("generationModel", ManagedDeploymentProfileCatalog.recommendedGenerationModel("openai"));
                    provider.put("embeddingProvider", "openai");
                    provider.remove("embeddingEndpointProfile");
                    provider.remove("embeddingManagedServiceRef");
                    provider.remove("embeddingServiceMode");
                    provider.put("openaiEmbeddingModel", "text-embedding-3-small");
                    provider.put("openaiEmbeddingDimensions", OPENAI_VECTOR_DIMENSIONS);
                    provider.put("vectorStrategy", "qdrant");
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("vectorStoragePosture", "SHARED");
                    provider.put("qdrantManagedCollectionsEnabled", true);
                    provider.put("qdrantCloudProviderId", QDRANT_PROVIDER);
                    provider.put("qdrantCloudRegionId", QDRANT_REGION);
                    return new UpdateDeploymentDraftRequest(
                        ensureObject(readYaml(ECOMMERCE_ACTIONS_RESOURCE)),
                        ecommerceEntityConfig(OPENAI_VECTOR_DIMENSIONS),
                        ecommerceRoutingConfig(),
                        normalizeProviderConfig(provider, OPENAI_VECTOR_DIMENSIONS),
                        marketplaceSecurityConfig(draft.securityConfig()),
                        withDefaultPromptLatencyTuning(ensureObject(draft.promptConfig())),
                        marketplaceKnowledgeSourceConfig(),
                        marketplaceShellConfig(),
                        marketplaceDatasetConfig()
                    );
                }
            },
            new VerificationRolloutDefinition(
                "qdrant",
                "OpenAI Qdrant Verification",
                "Canonical OpenAI plus platform-managed Qdrant Cloud verification deployment.",
                "dev-openai-qdrant",
                "PLATFORM_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("qdrantManagedCollectionsEnabled", true);
                    provider.put("qdrantCloudProviderId", QDRANT_PROVIDER);
                    provider.put("qdrantCloudRegionId", QDRANT_REGION);
                    return vectorDraftUpdate(draft, provider);
                }
            },
            new VerificationRolloutDefinition(
                "pinecone",
                "OpenAI Pinecone Verification",
                "Canonical OpenAI plus platform-managed Pinecone verification deployment.",
                "dev-openai-pinecone",
                "PLATFORM_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                List<String> requiredSecrets() {
                    return List.of("PINECONE_API_KEY");
                }

                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("pineconeManagedIndexEnabled", true);
                    if (!hasText(provider.path("pineconeRegion").asText(""))) {
                        provider.put("pineconeRegion", "us-east-1");
                    }
                    if (!hasText(provider.path("pineconeCloud").asText(""))) {
                        provider.put("pineconeCloud", "aws");
                    }
                    if (!hasText(provider.path("pineconeMetric").asText(""))) {
                        provider.put("pineconeMetric", "cosine");
                    }
                    return vectorDraftUpdate(draft, provider);
                }
            },
            new VerificationRolloutDefinition(
                "milvus",
                "OpenAI Milvus Verification",
                "Canonical OpenAI plus platform-managed Zilliz Cloud verification deployment.",
                "dev-openai-milvus",
                "PLATFORM_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                List<String> requiredSecrets() {
                    return List.of("ZILLIZ_CLOUD_API_KEY");
                }

                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("zillizCloudProjectId", ZILLIZ_PROJECT_ID);
                    provider.put("zillizCloudRegionId", ZILLIZ_REGION_ID);
                    provider.put("zillizCloudClusterPlan", "Serverless");
                    provider.remove("zillizCloudCuType");
                    provider.remove("zillizCloudCuSize");
                    provider.put("milvusSecure", true);
                    provider.put("milvusPort", 443);
                    return vectorDraftUpdate(draft, provider);
                }
            },
            new VerificationRolloutDefinition(
                "weaviate",
                "OpenAI Weaviate Verification",
                "Canonical OpenAI plus external-existing Weaviate Cloud verification deployment.",
                "dev-openai-weaviate",
                "EXTERNAL_EXISTING",
                "ecommerce",
                true
            ) {
                @Override
                List<String> requiredSecrets() {
                    return List.of("WEAVIATE_API_KEY");
                }

                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "EXTERNAL_EXISTING");
                    provider.put("weaviateScheme", "https");
                    provider.put("weaviateHost", verificationWeaviateHost());
                    provider.put("weaviatePort", 443);
                    return vectorDraftUpdate(draft, provider);
                }
            }
        );
    }

    private UpdateDeploymentDraftRequest vectorDraftUpdate(DeploymentDraftResponse draft, ObjectNode providerConfig) {
        return new UpdateDeploymentDraftRequest(
            ensureObject(readYaml(ECOMMERCE_ACTIONS_RESOURCE)),
            ecommerceEntityConfig(OPENAI_VECTOR_DIMENSIONS),
            ecommerceRoutingConfig(),
            normalizeProviderConfig(providerConfig, OPENAI_VECTOR_DIMENSIONS),
            ecommerceSecurityConfig(draft.securityConfig()),
            withDefaultPromptLatencyTuning(ensureObject(draft.promptConfig()))
        );
    }

    private ObjectNode ecommerceEntityConfig() {
        return ecommerceEntityConfig(ECOMMERCE_VECTOR_DIMENSIONS);
    }

    private ObjectNode ecommerceEntityConfig(int vectorDimensions) {
        ObjectNode root = ensureObject(readYaml(ECOMMERCE_ENTITIES_RESOURCE));
        ObjectNode aiConfig = root.with("ai-config");
        aiConfig.put("vector-dimensions", vectorDimensions);
        root.with("ai-entities");
        return root;
    }

    private ObjectNode normalizeProviderConfig(JsonNode source, int vectorDimensions) {
        ObjectNode root = ensureObject(source);
        String llmProvider = ManagedDeploymentProfileCatalog.resolveLlmProvider(root);
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI.equals(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(root)
        )) {
            root.put("openaiEmbeddingModel", ManagedDeploymentProfileCatalog.openAiEmbeddingModel(root));
            root.put("openaiEmbeddingDimensions", vectorDimensions);
        }
        if (ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI.equals(llmProvider)) {
            if (!hasConcreteValue(root.path("orchestrationLlmProvider").asText(""))) {
                root.put("orchestrationLlmProvider", llmProvider);
            }
            if (!hasConcreteValue(root.path("orchestrationModel").asText(""))) {
                root.put("orchestrationModel", ManagedDeploymentProfileCatalog.recommendedOrchestrationModel(llmProvider));
            }
            if (!hasConcreteValue(root.path("generationLlmProvider").asText(""))) {
                root.put("generationLlmProvider", llmProvider);
            }
            if (!hasConcreteValue(root.path("generationModel").asText(""))) {
                root.put("generationModel", ManagedDeploymentProfileCatalog.recommendedGenerationModel(llmProvider));
            }
            if (!hasConcreteValue(root.path("generationMaxTokens").asText(""))) {
                Integer recommendedGenerationMaxTokens =
                    ManagedDeploymentProfileCatalog.recommendedGenerationMaxTokens(llmProvider);
                if (recommendedGenerationMaxTokens != null) {
                    root.put("generationMaxTokens", recommendedGenerationMaxTokens);
                }
            }
        }
        return root;
    }

    private ObjectNode ecommerceRoutingConfig() {
        ObjectNode root = ensureObject(readYaml(ECOMMERCE_ROUTING_RESOURCE));

        ObjectNode connector = root.with("connector");
        ObjectNode inboundAuth = connector.with("inbound-auth");
        inboundAuth.put("allow-unauthenticated", false);
        ObjectNode apiKey = inboundAuth.with("api-key");
        apiKey.put("enabled", true);
        apiKey.put("header", "X-AIFABRIC-API-KEY");
        apiKey.put("value", "${CONNECTOR_API_KEY}");

        ObjectNode upstream = connector.with("upstream");
        upstream.put("base-url", ecommerceUpstreamBaseUrl());
        ObjectNode upstreamAuth = upstream.with("auth");
        if (!hasConcreteValue(upstreamAuth.path("type").asText(""))) {
            upstreamAuth.put("type", "NONE");
        }
        if (!hasConcreteValue(upstreamAuth.path("header").asText(""))) {
            upstreamAuth.put("header", "Authorization");
        }
        if (!hasConcreteValue(upstreamAuth.path("value").asText(""))) {
            upstreamAuth.put("value", "");
        }

        ObjectNode authz = root.with("authz");
        authz.put("enabled", true);
        authz.put("path", "/api/authz/check");
        ObjectNode authzUpstream = authz.with("upstream");
        authzUpstream.put("base-url", ecommerceUpstreamBaseUrl());
        ObjectNode authzUpstreamAuth = authzUpstream.with("auth");
        if (!hasConcreteValue(authzUpstreamAuth.path("type").asText(""))) {
            authzUpstreamAuth.put("type", "NONE");
        }
        if (!hasConcreteValue(authzUpstreamAuth.path("header").asText(""))) {
            authzUpstreamAuth.put("header", "Authorization");
        }
        if (!hasConcreteValue(authzUpstreamAuth.path("value").asText(""))) {
            authzUpstreamAuth.put("value", "");
        }

        root.with("actions");
        return root;
    }

    private void seedCanonicalVectorization(String deploymentId) {
        if (vectorizationSourceConnectionRepository == null
            || vectorizationPlanRepository == null
            || vectorizationPlanRevisionRepository == null) {
            return;
        }
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Deployment not found for canonical vectorization seed: " + deploymentId));
        Instant now = Instant.now();

        VectorizationSourceConnectionEntity connection = vectorizationSourceConnectionRepository.findByDeploymentId(deploymentId)
            .orElseGet(() -> {
                VectorizationSourceConnectionEntity created = new VectorizationSourceConnectionEntity();
                created.setId(generateId("vcn"));
                created.setDeploymentId(deployment.getId());
                created.setCustomerId(deployment.getCustomerId());
                created.setTenantId(deployment.getTenantId());
                created.setCreatedAt(now);
                return created;
            });
        connection.setName(deployment.getName() + " store REST source");
        connection.setAdapterType("REST_API");
        connection.setAuthMode("NONE");
        connection.setStatus("READY");
        connection.setConnectionConfigJson(writeJson(canonicalVectorizationConnectionConfig()));
        connection.setSecretReferencesJson(writeJson(objectMapper.createObjectNode()));
        connection.setDiscoverySummaryJson(writeJson(canonicalDiscoverySummary()));
        connection.setUpdatedAt(now);
        vectorizationSourceConnectionRepository.save(connection);

        VectorizationPlanEntity plan = vectorizationPlanRepository.findByDeploymentId(deploymentId)
            .orElseGet(() -> {
                VectorizationPlanEntity created = new VectorizationPlanEntity();
                created.setId(generateId("vpl"));
                created.setDeploymentId(deployment.getId());
                created.setCustomerId(deployment.getCustomerId());
                created.setTenantId(deployment.getTenantId());
                created.setCreatedAt(now);
                return created;
            });
        plan.setName(deployment.getName() + " vectorization");
        plan.setStatus("ACTIVE");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
        plan.setSyncState("BOOTSTRAP_REQUIRED");
        plan.setSyncReasonCodesJson(writeJson(objectMapper.createArrayNode().add("PLAN_CREATED").add("CANONICAL_ROLLOUT_SEEDED")));
        plan.setSyncReasonDetailsJson(writeJson(objectMapper.createObjectNode()));
        plan.setSourceConnectionId(connection.getId());
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);

        VectorizationPlanRevisionEntity revision = plan.getActiveRevisionId() == null
            ? null
            : vectorizationPlanRevisionRepository.findById(plan.getActiveRevisionId()).orElse(null);
        if (revision == null) {
            revision = vectorizationPlanRevisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(plan.getId())
                .orElseGet(() -> {
                    VectorizationPlanRevisionEntity created = new VectorizationPlanRevisionEntity();
                    created.setId(generateId("vpr"));
                    created.setPlanId(plan.getId());
                    created.setDeploymentId(deployment.getId());
                    created.setRevisionNumber(1);
                    created.setCreatedAt(now);
                    return created;
                });
        }
        revision.setStatus("ACTIVE");
        revision.setSourceConnectionId(connection.getId());
        revision.setEntityScopeJson(writeJson(canonicalEntityScope()));
        revision.setMappingConfigJson(writeJson(canonicalMappingConfig()));
        revision.setExecutionConfigJson(writeJson(canonicalExecutionConfig()));
        revision.setUpdatedAt(now);
        vectorizationPlanRevisionRepository.save(revision);

        plan.setActiveRevisionId(revision.getId());
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);
    }

    private JsonNode canonicalVectorizationConnectionConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("baseUrl", ecommerceUpstreamBaseUrl());
        ObjectNode datasets = root.putObject("datasets");
        datasets.set("product", canonicalDatasetConfig("/api/products?limit=500"));
        datasets.set("review", canonicalDatasetConfig("/api/reviews?limit=500"));
        datasets.set("policy", canonicalDatasetConfig("/api/policies?limit=500"));
        return root;
    }

    private JsonNode canonicalDiscoverySummary() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode counts = root.putObject("countsByEntityType");
        counts.put("policy", 20);
        counts.put("product", 100);
        counts.put("review", 200);
        ObjectNode methods = root.putObject("countMethodByEntityType");
        methods.put("policy", "EXACT");
        methods.put("product", "EXACT");
        methods.put("review", "EXACT");
        return root;
    }

    private JsonNode canonicalEntityScope() {
        return objectMapper.createArrayNode()
            .add("policy")
            .add("product")
            .add("review");
    }

    private JsonNode canonicalMappingConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode entityMappings = root.putObject("entityMappings");

        ObjectNode product = entityMappings.putObject("product");
        product.put("dataset", "product");
        product.put("recordIdField", "sku");
        product.put("recordVersionField", "updatedAt");
        ObjectNode productFields = product.putObject("entityFieldMappings");
        productFields.put("name", "name");
        productFields.put("description", "description");
        productFields.put("category", "category");
        productFields.put("tags", "tags");
        productFields.put("sku", "sku");
        productFields.put("price", "price");
        productFields.put("currency", "currency");
        productFields.put("inStockQty", "inStockQty");
        ObjectNode productMetadata = product.putObject("metadataFieldMappings");
        productMetadata.put("sku", "sku");
        productMetadata.put("category", "category");
        productMetadata.put("price", "price");
        productMetadata.put("currency", "currency");
        productMetadata.put("inStockQty", "inStockQty");

        ObjectNode review = entityMappings.putObject("review");
        review.put("dataset", "review");
        review.put("recordIdField", "id");
        review.put("recordVersionField", "updatedAt");
        ObjectNode reviewFields = review.putObject("entityFieldMappings");
        reviewFields.put("text", "text");
        reviewFields.put("sku", "sku");
        reviewFields.put("rating", "rating");
        ObjectNode reviewMetadata = review.putObject("metadataFieldMappings");
        reviewMetadata.put("sku", "sku");
        reviewMetadata.put("rating", "rating");

        ObjectNode policy = entityMappings.putObject("policy");
        policy.put("dataset", "policy");
        policy.put("recordIdField", "id");
        policy.put("recordVersionField", "updatedAt");
        ObjectNode policyFields = policy.putObject("entityFieldMappings");
        policyFields.put("title", "title");
        policyFields.put("text", "text");
        policyFields.put("classification", "classification");
        ObjectNode policyMetadata = policy.putObject("metadataFieldMappings");
        policyMetadata.put("title", "title");
        policyMetadata.put("classification", "classification");
        return root;
    }

    private JsonNode canonicalExecutionConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("batchSize", DEFAULT_BATCH_SIZE);
        root.put("pageSize", DEFAULT_PAGE_SIZE);
        return root;
    }

    private ObjectNode canonicalDatasetConfig(String path) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("path", path);
        node.put("paginationMode", "NONE");
        return node;
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize canonical vectorization seed.", ex);
        }
    }

    private JsonNode readJson(String json) {
        if (!hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private ObjectNode ecommerceSecurityConfig(JsonNode source) {
        ObjectNode root = ensureObject(source);
        root.put("authzMode", ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED);
        root.put("adminApiKeyEnabled", true);
        root.put("connectorApiKeyEnabled", true);
        root.remove("authzBaseUrl");
        root.put("publicRuntimeBootstrapEnabled", true);
        root.put("publicRuntimeTokenIssuer", PUBLIC_RUNTIME_TOKEN_ISSUER);
        root.put("publicRuntimeAcceptedIssuers", PUBLIC_RUNTIME_ACCEPTED_ISSUERS);
        root.put("publicRuntimeAcceptedAudiences", PUBLIC_RUNTIME_ACCEPTED_AUDIENCES);
        root.put("publicRuntimeDefaultAudience", PUBLIC_RUNTIME_DEFAULT_AUDIENCE);
        return root;
    }

    private ObjectNode marketplaceSecurityConfig(JsonNode source) {
        ObjectNode root = ecommerceSecurityConfig(source);
        root.put("authzMode", ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED);
        root.remove("authzBaseUrl");
        return root;
    }

    private ObjectNode withDefaultPromptLatencyTuning(ObjectNode promptConfig) {
        ObjectNode root = ensureObject(promptConfig);
        JsonNode candidate = root.path("ragSimilarityThreshold");
        if (candidate.isMissingNode()
            || candidate.isNull()
            || (candidate.isTextual() && candidate.asText("").trim().isEmpty())) {
            root.put("ragSimilarityThreshold", DEFAULT_RAG_SIMILARITY_THRESHOLD);
        }
        if (!root.path("smartSuggestionsEnabled").isBoolean()) {
            root.put("smartSuggestionsEnabled", DEFAULT_SMART_SUGGESTIONS_ENABLED);
        }
        return root;
    }

    private ObjectNode marketplaceKnowledgeSourceConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractVersion", "KNOWLEDGE_SOURCE_CONFIG_V1");
        root.putArray("sources")
            .addObject()
            .put("id", MARKETPLACE_KNOWLEDGE_SOURCE_ID)
            .put("type", "deployment-private-vector")
            .put("adapterType", "deployment-private-vector")
            .put("attributionLabel", "Deployment marketplace knowledge");
        ObjectNode sharedPolicySource = root.withArray("sources")
            .addObject()
            .put("id", MARKETPLACE_SHARED_POLICY_SOURCE_ID)
            .put("type", "shared-vector")
            .put("adapterType", "shared-index")
            .put("attributionLabel", "Shared refund policy knowledge")
            .put("datasetRef", MARKETPLACE_SHARED_POLICY_DATASET_ID)
            .put("entityType", "policy")
            .put("handleRef", MARKETPLACE_SHARED_POLICY_HANDLE_REF)
            .put("enabled", true);
        sharedPolicySource.putArray("authModes")
            .add("PUBLIC_RUNTIME_AUTHENTICATED")
            .add("PLATFORM_PROXY_SESSION")
            .add("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        sharedPolicySource.putObject("filters")
            .put("classification", "refund");
        return root;
    }

    private ObjectNode marketplaceDatasetConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractVersion", "MARKETPLACE_DATASET_CONFIG_V1");
        ObjectNode dataset = root.putArray("datasets")
            .addObject()
            .put("systemManaged", true)
            .put("marketplacePluginId", MARKETPLACE_SHARED_POLICY_PLUGIN_ID)
            .put("marketplacePluginVersionId", MARKETPLACE_SHARED_POLICY_PLUGIN_VERSION_ID)
            .put("datasetId", MARKETPLACE_SHARED_POLICY_DATASET_ID)
            .put("entityType", "policy")
            .put("storageScope", "PLUGIN_SCOPED")
            .put("sharingScope", "TENANT_SHARED")
            .put("ingestionMode", "PACKAGED_SEED")
            .put("updateStrategy", "UPSERT_BY_ID")
            .put("handleRef", MARKETPLACE_SHARED_POLICY_HANDLE_REF)
            .put("datasetHash", MARKETPLACE_SHARED_POLICY_DATASET_HASH)
            .put("seedDatasetRef", MARKETPLACE_SHARED_POLICY_DATASET_REF);
        dataset.putObject("config")
            .put("scope", "all");
        return root;
    }

    private ObjectNode marketplaceShellConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("contractVersion", "SHELL_CONFIG_V1");
        root.putObject("greeting")
            .put("title", "Marketplace Assistant")
            .put("message", "Browse products, policy, and order flows through the resolved shell config.");

        root.putArray("starterPrompts")
            .addObject()
            .put("id", "marketplace-featured-products")
            .put("label", "Browse featured products")
            .put("query", "Show me featured products")
            .put("moduleId", "product-catalog")
            .put("cardId", "product-list");
        root.withArray("starterPrompts")
            .addObject()
            .put("id", "marketplace-return-policy")
            .put("label", "Summarize return policy")
            .put("query", "Summarize return policy")
            .put("moduleId", "policies")
            .put("cardId", "policy-summary");

        root.putArray("modules")
            .addObject()
            .put("id", "product-catalog")
            .put("enabled", true);
        root.withArray("modules")
            .addObject()
            .put("id", "policies")
            .put("enabled", true);
        root.withArray("modules")
            .addObject()
            .put("id", "orders")
            .put("enabled", true);

        root.putArray("cards")
            .addObject()
            .put("id", "product-list")
            .put("enabled", true);
        root.withArray("cards")
            .addObject()
            .put("id", "policy-summary")
            .put("enabled", true);
        root.withArray("cards")
            .addObject()
            .put("id", "order-status")
            .put("enabled", true);
        return root;
    }

    private JsonNode readYaml(String configuredLocation) {
        try {
            Resource resource = resolveConfiguredResource(configuredLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return yamlMapper.readTree(inputStream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read rollout bootstrap config from " + configuredLocation + ": " + ex.getMessage(), ex);
        }
    }

    private Resource resolveConfiguredResource(String configuredPath) {
        if (configuredPath.startsWith("classpath:") || configuredPath.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(configuredPath);
            if (resource.exists()) {
                return resource;
            }
            throw new IllegalStateException("Rollout config resource not found: " + configuredPath);
        }

        Path raw = Path.of(configuredPath);
        if (raw.isAbsolute() && Files.exists(raw)) {
            return resourceLoader.getResource(raw.normalize().toUri().toString());
        }

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cwdResolved = cwd.resolve(configuredPath).normalize();
        if (Files.exists(cwdResolved)) {
            return resourceLoader.getResource(cwdResolved.toUri().toString());
        }

        Path repoRoot = findRepoRoot(cwd);
        if (repoRoot != null) {
            Path repoResolved = repoRoot.resolve(configuredPath).normalize();
            if (Files.exists(repoResolved)) {
                return resourceLoader.getResource(repoResolved.toUri().toString());
            }
        }

        throw new IllegalStateException("Rollout config resource not found: " + configuredPath);
    }

    private Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git"))
                || Files.isDirectory(current.resolve("Real_Apps"))
                || Files.isDirectory(current.resolve("Platfrom"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String summarizeIssues(List<DraftValidationIssue> issues) {
        return issues.stream()
            .limit(5)
            .map(issue -> issue.code() + ": " + issue.message())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Unknown validation issue.");
    }

    private ObjectNode ensureObject(JsonNode node) {
        return node != null && node.isObject()
            ? (ObjectNode) node.deepCopy()
            : objectMapper.createObjectNode();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String verificationWeaviateHost() {
        String override = suiteProperties == null ? null : suiteProperties.weaviateHost();
        if (!hasText(override)) {
            override = System.getenv("PLATFORM_VERIFICATION_WEAVIATE_HOST");
        }
        if (!hasText(override)) {
            override = System.getenv("WEAVIATE_HOST");
        }
        if (hasText(override)) {
            return override.trim();
        }
        throw new ResponseStatusException(
            BAD_REQUEST,
            "PLATFORM_VERIFICATION_WEAVIATE_HOST is required before recreating the canonical Weaviate verification rollout."
        );
    }

    private static PlatformVerificationSuiteProperties defaultSuiteProperties() {
        return new PlatformVerificationSuiteProperties(
            Duration.ofMinutes(180),
            Duration.ofMinutes(12),
            Duration.ofMinutes(20),
            Duration.ofMinutes(75),
            Duration.ofHours(12),
            Duration.ofSeconds(3),
            20,
            12_000,
            80_000,
            null,
            TEST_WEAVIATE_HOST,
            null,
            null,
            null,
            null,
            null
        );
    }

    private String firstNonBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String ecommerceUpstreamBaseUrl() {
        return firstNonBlank(ecommerceUpstreamBaseUrl, ECOMMERCE_UPSTREAM_BASE_URL);
    }

    private String normalizeBaseUrl(String value) {
        if (!hasText(value)) {
            return ECOMMERCE_UPSTREAM_BASE_URL;
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private boolean baseUrlsEqual(String expected, String actual) {
        return normalizeBaseUrl(expected).equals(normalizeBaseUrl(actual));
    }

    private boolean hasConcreteValue(String value) {
        return hasText(value) && !isPlaceholderExpression(value);
    }

    private boolean runnerRegistrationReady(DeploymentVectorizationVerificationSummary summary) {
        return VectorizationRunnerReadinessSupport.isExecutionReady(summary.runner(), Instant.now());
    }

    private boolean runnerServiceProvisioned(DeploymentReleaseEntity latestRelease) {
        if (latestRelease == null || !hasText(latestRelease.getProvisioningDetailsJson())) {
            return false;
        }
        try {
            JsonNode details = objectMapper.readTree(latestRelease.getProvisioningDetailsJson());
            return runnerServiceProvisioned(details.path("railway").path("services").path("vectorizationRunner"))
                || runnerServiceProvisioned(details.path("coolify").path("services").path("vectorizationRunner"));
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean runnerServiceProvisioned(JsonNode runnerService) {
        return runnerService.isObject()
            && (hasText(runnerService.path("serviceId").asText("")) || hasText(runnerService.path("serviceName").asText("")))
            && hasText(runnerService.path("deploymentStatus").asText(""));
    }

    private boolean isPlaceholderExpression(String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private abstract static class VerificationRolloutDefinition {
        private final String key;
        private final String displayName;
        private final String description;
        private final String templateId;
        private final String vectorProvisioningMode;
        private final String verificationProfile;
        private final boolean writeVerificationSupported;

        private VerificationRolloutDefinition(String key,
                                             String displayName,
                                             String description,
                                             String templateId,
                                             String vectorProvisioningMode,
                                             String verificationProfile,
                                             boolean writeVerificationSupported) {
            this.key = key;
            this.displayName = displayName;
            this.description = description;
            this.templateId = templateId;
            this.vectorProvisioningMode = vectorProvisioningMode;
            this.verificationProfile = verificationProfile;
            this.writeVerificationSupported = writeVerificationSupported;
        }

        String key() {
            return key;
        }

        String displayName() {
            return displayName;
        }

        String description() {
            return description;
        }

        String templateId() {
            return templateId;
        }

        String vectorProvisioningMode() {
            return vectorProvisioningMode;
        }

        String verificationProfile() {
            return verificationProfile;
        }

        boolean writeVerificationSupported() {
            return writeVerificationSupported;
        }

        String deploymentName() {
            return displayName;
        }

        List<String> requiredSecrets() {
            return switch (key) {
                case "qdrant" -> List.of("QDRANT_CLOUD_MANAGEMENT_API_KEY");
                case "pinecone" -> List.of("PINECONE_API_KEY");
                case "milvus" -> List.of("ZILLIZ_CLOUD_API_KEY");
                case "weaviate" -> List.of("WEAVIATE_API_KEY");
                default -> List.of();
            };
        }

        abstract UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft);
    }

    private record RolloutReadiness(boolean ready, String message, boolean repairRecommended, List<String> repairReasons) {
        RolloutReadiness(boolean ready, String message) {
            this(ready, message, false, List.of());
        }

        static RolloutReadiness repairable(String message, List<String> repairReasons) {
            return new RolloutReadiness(false, message, true, repairReasons == null ? List.of() : List.copyOf(repairReasons));
        }
    }

    private record RolloutExecutionFailure(String displayName, String message) {
    }
}
