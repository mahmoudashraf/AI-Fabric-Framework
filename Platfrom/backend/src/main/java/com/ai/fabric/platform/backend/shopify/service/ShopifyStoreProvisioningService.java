package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreProvisioningJobEntity;
import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.CreateShopifyStoreProvisioningJobRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreProvisioningJobSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreProvisioningStatusSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreProvisioningJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreProvisioningService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final List<String> ACTIVE_STATUSES = List.of("QUEUED", "RUNNING");
    private static final List<String> LEASEABLE_STATUSES = List.of("QUEUED");
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);

    private final ShopifyStoreProvisioningJobRepository jobRepository;
    private final ShopifyStoreConnectionRepository storeRepository;
    private final DeploymentRepository deploymentRepository;
    private final ShopifyStoreBootstrapService bootstrapService;
    private final ShopifyStoreVectorizationService vectorizationService;
    private final ShopifyStoreSourcePreflightSupport detailsSupport;
    private final ShopifyCompanionPackageProfileCatalogService profileCatalogService;
    private final DeploymentMarketplaceInstallService marketplaceInstallService;
    private final DeploymentMarketplaceDraftCompilerService draftCompilerService;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final PlatformAuditService platformAuditService;
    private final TransactionTemplate transactionTemplate;

    public ShopifyStoreProvisioningService(ShopifyStoreProvisioningJobRepository jobRepository,
                                           ShopifyStoreConnectionRepository storeRepository,
                                           DeploymentRepository deploymentRepository,
                                           ShopifyStoreBootstrapService bootstrapService,
                                           ShopifyStoreVectorizationService vectorizationService,
                                           ShopifyStoreSourcePreflightSupport detailsSupport,
                                           ShopifyCompanionPackageProfileCatalogService profileCatalogService,
                                           DeploymentMarketplaceInstallService marketplaceInstallService,
                                           DeploymentMarketplaceDraftCompilerService draftCompilerService,
                                           MarketplaceCatalogService marketplaceCatalogService,
                                           PlatformAuditService platformAuditService,
                                           TransactionTemplate transactionTemplate) {
        this.jobRepository = jobRepository;
        this.storeRepository = storeRepository;
        this.deploymentRepository = deploymentRepository;
        this.bootstrapService = bootstrapService;
        this.vectorizationService = vectorizationService;
        this.detailsSupport = detailsSupport;
        this.profileCatalogService = profileCatalogService;
        this.marketplaceInstallService = marketplaceInstallService;
        this.draftCompilerService = draftCompilerService;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.platformAuditService = platformAuditService;
        this.transactionTemplate = transactionTemplate;
    }

    public ShopifyStoreProvisioningJobSummary enqueue(String shopDomain,
                                                      CreateShopifyStoreProvisioningJobRequest request) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        ShopifyStoreProvisioningJobSummary queued = transactionTemplate.execute(status -> {
            ShopifyStoreConnectionEntity store = storeRepository.findByShopDomainIgnoreCase(normalizedShop).orElse(null);
            String jobType = normalizeJobType(request == null ? null : request.jobType(), store);
            ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile = resolveRequestedProfile(store, request);
            ShopifyStorePackageState previousState = readPackageState(store);

            ShopifyStoreProvisioningJobEntity job = jobRepository
                .findFirstByShopDomainIgnoreCaseAndStatusInOrderByCreatedAtDesc(normalizedShop, ACTIVE_STATUSES)
                .orElseGet(() -> newJob(normalizedShop, store, jobType));

            job.setStoreConnectionId(store == null ? null : store.getId());
            job.setJobType(jobType);
            job.setStatus("QUEUED");
            job.setPhase("QUEUED");
            job.setRequestedPackageKey(profile.packageKey());
            job.setRequestedTierKey(profile.tierKey());
            job.setRequestedRuntimeProfileKey(profile.runtimeProfileKey());
            job.setRequestedVectorProfileKey(profile.vectorProfileKey());
            job.setPreviousPackageKey(previousState.packageKey());
            job.setPreviousRuntimeProfileKey(previousState.runtimeProfileKey());
            job.setPreviousVectorProfileKey(previousState.vectorProfileKey());
            job.setRequestedTemplatePluginId(firstText(request == null ? null : request.requestedTemplatePluginId(), profile.templatePluginId()));
            job.setRequestedTemplatePluginVersion(firstText(request == null ? null : request.requestedTemplatePluginVersion(), profile.templatePluginVersion()));
            job.setRequestedPluginIdsJson(writePluginIds(resolveRequestedPluginIds(request, profile)));
            job.setInstallIntentId(blankToNull(request == null ? null : request.installIntentId()));
            job.setProfileChangeStrategy(resolveProfileChangeStrategy(previousState, profile));
            job.setVectorReindexRequired(vectorReindexRequired(previousState, profile));
            job.setLastErrorCode(null);
            job.setLastErrorMessage(null);
            job.setFailedAt(null);
            job.setCancelledAt(null);
            job.setNextAction("Wait for Platform provisioning to complete.");
            job.setSummaryMessage(summaryForQueuedJob(jobType, profile));
            job.setLeaseOwner(null);
            job.setLeaseExpiresAt(null);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
            audit("SHOPIFY_STORE_PROVISIONING_ENQUEUED", job, Map.of("jobType", jobType));
            return toSummary(job);
        });
        if (request != null && Boolean.TRUE.equals(request.processImmediately())) {
            return processJobNow(normalizedShop, queued.id());
        }
        return queued;
    }

    public ShopifyStoreProvisioningStatusSummary getStatus(String shopDomain) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        List<ShopifyStoreProvisioningJobSummary> jobs = jobRepository.findByShopDomainIgnoreCaseOrderByCreatedAtDesc(normalizedShop)
            .stream()
            .limit(10)
            .map(this::toSummary)
            .toList();
        ShopifyStoreProvisioningJobSummary latest = jobs.isEmpty() ? null : jobs.getFirst();
        ShopifyCompanionPackageProfileSummary effectiveProfile = storeRepository.findByShopDomainIgnoreCase(normalizedShop)
            .map(this::readEffectiveProfileSummary)
            .orElse(null);
        String status = latest == null ? "NOT_STARTED" : latest.status();
        String phase = latest == null ? "NOT_STARTED" : latest.phase();
        String nextAction = latest == null ? "Install or reconnect the Shopify app to start provisioning." : latest.nextAction();
        String message = latest == null ? "No Shopify Companion provisioning job has been recorded for this store." : latest.summaryMessage();
        return new ShopifyStoreProvisioningStatusSummary(
            normalizedShop,
            status,
            phase,
            nextAction,
            message,
            effectiveProfile,
            latest,
            jobs
        );
    }

    @Transactional
    public ShopifyStoreProvisioningJobSummary retry(String shopDomain, String jobId, String reason) {
        ShopifyStoreProvisioningJobEntity job = requireJob(shopDomain, jobId);
        if ("RUNNING".equalsIgnoreCase(job.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Provisioning job is already running.");
        }
        job.setStatus("QUEUED");
        job.setPhase("QUEUED");
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setFailedAt(null);
        job.setCancelledAt(null);
        job.setLastErrorCode(null);
        job.setLastErrorMessage(null);
        job.setNextAction("Wait for Platform provisioning retry to complete.");
        job.setSummaryMessage(StringUtils.hasText(reason) ? reason.trim() : "Provisioning retry queued.");
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        audit("SHOPIFY_STORE_PROVISIONING_RETRIED", job, Map.of("reason", blankToEmpty(reason)));
        return toSummary(job);
    }

    @Transactional
    public ShopifyStoreProvisioningJobSummary cancel(String shopDomain, String jobId, String reason) {
        ShopifyStoreProvisioningJobEntity job = requireJob(shopDomain, jobId);
        if ("READY".equalsIgnoreCase(job.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Ready provisioning jobs cannot be cancelled.");
        }
        job.setStatus("CANCELLED");
        job.setPhase("CANCELLED");
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setCancelledAt(Instant.now());
        job.setNextAction("Queue a repair provisioning job if this store still needs work.");
        job.setSummaryMessage(StringUtils.hasText(reason) ? reason.trim() : "Provisioning job cancelled.");
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        audit("SHOPIFY_STORE_PROVISIONING_CANCELLED", job, Map.of("reason", blankToEmpty(reason)));
        return toSummary(job);
    }

    public ShopifyStoreProvisioningJobSummary processNextDueJob() {
        String jobId = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<ShopifyStoreProvisioningJobEntity> candidates =
                jobRepository.findAvailableForLease(LEASEABLE_STATUSES, now, PageRequest.of(0, 1));
            if (candidates.isEmpty()) {
                return null;
            }
            ShopifyStoreProvisioningJobEntity job = candidates.getFirst();
            job.setStatus("RUNNING");
            job.setPhase("LEASED");
            job.setLeaseOwner("shopify-provisioning-" + UUID.randomUUID());
            job.setLeaseExpiresAt(now.plus(DEFAULT_LEASE_DURATION));
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setUpdatedAt(now);
            jobRepository.save(job);
            return job.getId();
        });
        if (!hasText(jobId)) {
            return null;
        }
        return processLeasedJob(jobId);
    }

    public ShopifyStoreProvisioningJobSummary processJobNow(String shopDomain, String jobId) {
        String leasedJobId = transactionTemplate.execute(status -> {
            ShopifyStoreProvisioningJobEntity job = requireJob(shopDomain, jobId);
            if (!"QUEUED".equalsIgnoreCase(job.getStatus()) && !"FAILED".equalsIgnoreCase(job.getStatus())) {
                throw new ResponseStatusException(CONFLICT, "Only queued or failed provisioning jobs can be processed manually.");
            }
            job.setStatus("RUNNING");
            job.setPhase("LEASED");
            job.setLeaseOwner("shopify-provisioning-manual-" + UUID.randomUUID());
            job.setLeaseExpiresAt(Instant.now().plus(DEFAULT_LEASE_DURATION));
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
            return job.getId();
        });
        return processLeasedJob(leasedJobId);
    }

    @Transactional
    public ShopifyStoreProvisioningJobSummary recordProcessingFailure(String shopDomain,
                                                                      String jobId,
                                                                      RuntimeException ex) {
        ShopifyStoreProvisioningJobEntity job = requireJob(shopDomain, jobId);
        if (!"RUNNING".equalsIgnoreCase(job.getStatus())) {
            job.setStatus("RUNNING");
            job.setPhase("FAILED");
            job.setAttemptCount(job.getAttemptCount() + 1);
        }
        failJob(job, ex);
        return toSummary(job);
    }

    private ShopifyStoreProvisioningJobSummary processLeasedJob(String jobId) {
        ShopifyStoreProvisioningJobEntity job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Provisioning job not found: " + jobId));
        try {
            markPhase(job, "PROFILE_RESOLUTION", "Resolving Shopify Companion package profile.");
            ShopifyStoreConnectionEntity store = storeRepository.findByShopDomainIgnoreCase(job.getShopDomain())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + job.getShopDomain()));
            ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile = profileCatalogService.resolve(
                job.getRequestedPackageKey(),
                job.getRequestedTierKey(),
                job.getRequestedRuntimeProfileKey(),
                job.getRequestedVectorProfileKey()
            );
            if (requiresBootstrap(job, store)) {
                markPhase(job, "BOOTSTRAPPING", "Bootstrapping Platform customer, deployment, consumer, marketplace bundle, and connector defaults.");
                BootstrapShopifyStoreRequest request = new BootstrapShopifyStoreRequest(
                    null,
                    null,
                    null,
                    null,
                    profile.templatePluginId(),
                    profile.templatePluginVersion(),
                    readPluginIds(job.getRequestedPluginIdsJson())
                );
                ShopifyStoreBootstrapSummary bootstrap = bootstrapService.bootstrap(store.getShopDomain(), request);
                job.setBootstrapDeploymentId(bootstrap.deploymentId());
                store = storeRepository.findByShopDomainIgnoreCase(job.getShopDomain())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found after bootstrap: " + job.getShopDomain()));
            }

            boolean skipNoOpPackageReconciliation = isNoOpPackageChange(job);
            if (hasText(store.getDeploymentId()) && !skipNoOpPackageReconciliation) {
                markPhase(job, "BUNDLE_SYNC", "Reconciling marketplace inference profile and product package metadata.");
                reconcileMarketplaceInferenceProfile(store, profile, job);
            }
            if (!skipNoOpPackageReconciliation) {
                markPhase(job, "SOURCE_PREFLIGHT", "Reconciling Shopify Companion vectorization state.");
                reconcileVectorizationSafely(store, job);
            }
            persistEffectiveProfile(store, profile, job);

            job.setStatus("READY");
            job.setPhase("READY");
            job.setReadyAt(Instant.now());
            job.setLeaseOwner(null);
            job.setLeaseExpiresAt(null);
            job.setNextAction("Continue launch readiness checks for the selected package profile.");
            job.setSummaryMessage("Shopify Companion provisioning is ready for " + profile.displayName() + ".");
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
            audit("SHOPIFY_STORE_PROVISIONING_READY", job, Map.of("profileKey", profile.profileKey()));
            return toSummary(job);
        } catch (RuntimeException ex) {
            failJob(job, ex);
            return toSummary(job);
        }
    }

    private void reconcileMarketplaceInferenceProfile(ShopifyStoreConnectionEntity store,
                                                      ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile,
                                                      ShopifyStoreProvisioningJobEntity job) {
        DeploymentEntity deployment = deploymentRepository.findById(store.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + store.getDeploymentId()));
        List<DeploymentMarketplaceInstallSummary> installs = marketplaceInstallService.listInstallsForTrustedCaller(deployment);
        List<DeploymentMarketplaceInstallSummary> activeInferenceInstalls = installs.stream()
            .filter(install -> "INFERENCE_PROFILE".equalsIgnoreCase(install.pluginType()))
            .filter(install -> !"DISABLED".equalsIgnoreCase(install.status()))
            .toList();
        for (DeploymentMarketplaceInstallSummary install : activeInferenceInstalls) {
            if (!profile.inferencePluginId().equalsIgnoreCase(install.pluginId())) {
                marketplaceInstallService.updateInstallForTrustedCaller(
                    deployment,
                    install.id(),
                    new UpdateDeploymentMarketplaceInstallRequest(null, "DISABLED", null, null)
                );
                job.setVectorReindexRequired(true);
            }
        }
        boolean desiredActive = marketplaceInstallService.listInstallsForTrustedCaller(deployment).stream()
            .anyMatch(install -> profile.inferencePluginId().equalsIgnoreCase(install.pluginId())
                && !"DISABLED".equalsIgnoreCase(install.status()));
        if (!desiredActive) {
            DeploymentMarketplaceInstallSummary existing = marketplaceInstallService.listInstallsForTrustedCaller(deployment).stream()
                .filter(install -> profile.inferencePluginId().equalsIgnoreCase(install.pluginId()))
                .findFirst()
                .orElse(null);
            if (existing == null) {
                String version = marketplaceCatalogService.resolveLatestPublishedVersionLabel(profile.inferencePluginId());
                marketplaceInstallService.createInstallForTrustedCaller(
                    deployment,
                    new CreateDeploymentMarketplaceInstallRequest(
                        profile.inferencePluginId(),
                        version,
                        JSON.objectNode(),
                        JSON.objectNode()
                    )
                );
            } else {
                marketplaceInstallService.updateInstallForTrustedCaller(
                    deployment,
                    existing.id(),
                    new UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED", null, null)
                );
            }
            job.setVectorReindexRequired(true);
        }
        draftCompilerService.syncDeploymentDraftForTrustedCaller(deployment.getId());
    }

    private void persistEffectiveProfile(ShopifyStoreConnectionEntity store,
                                         ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile,
                                         ShopifyStoreProvisioningJobEntity job) {
        ObjectNode details = detailsSupport.mutableDetails(store.getDetailsJson());
        ObjectNode packageState = details.putObject("packageState");
        packageState.put("packageKey", profile.packageKey());
        packageState.put("tierKey", profile.tierKey());
        packageState.put("runtimeProfileKey", profile.runtimeProfileKey());
        packageState.put("vectorProfileKey", profile.vectorProfileKey());
        packageState.put("profileKey", profile.profileKey());
        packageState.put("displayName", profile.displayName());
        packageState.put("costPosture", profile.costPosture());
        packageState.put("vectorStrategy", profile.vectorStrategy());
        packageState.put("vectorProvisioningMode", profile.vectorProvisioningMode());
        packageState.put("vectorStoragePosture", profile.vectorStoragePosture());
        packageState.put("verificationPackId", profile.verificationPackId());
        packageState.put("lastProvisioningJobId", job.getId());
        packageState.put("lastReconciledAt", Instant.now().toString());
        packageState.put("vectorReindexRequired", job.isVectorReindexRequired());
        if (job.isVectorReindexRequired()) {
            ObjectNode sync = details.with("sync");
            sync.put("status", "STALE");
            sync.put("checkedAt", Instant.now().toString());
            sync.put("mode", "PROFILE_RECONCILIATION");
            sync.put("message", "Runtime or vector profile changed; run knowledge sync before marking launch ready.");
            store.setSyncStatus("STALE");
        }
        store.setDetailsJson(detailsSupport.writeJson(details));
        store.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
        store.setUpdatedAt(Instant.now());
        storeRepository.save(store);
    }

    private ShopifyCompanionPackageProfileSummary readEffectiveProfileSummary(ShopifyStoreConnectionEntity store) {
        ShopifyStorePackageState state = readPackageState(store);
        if (!hasText(state.runtimeProfileKey())) {
            return null;
        }
        return profileCatalogService.toSummary(profileCatalogService.resolve(
            state.packageKey(),
            state.tierKey(),
            state.runtimeProfileKey(),
            state.vectorProfileKey()
        ));
    }

    private boolean requiresBootstrap(ShopifyStoreProvisioningJobEntity job, ShopifyStoreConnectionEntity store) {
        boolean bindingMissing = !hasText(store.getCustomerId()) || !hasText(store.getDeploymentId()) || !hasText(store.getConsumerId());
        if (bindingMissing) {
            return true;
        }
        if (!hasActiveDeployment(store.getDeploymentId())) {
            return true;
        }
        return switch (job.getJobType()) {
            case "INSTALL", "REINSTALL_REPAIR", "MANUAL_REPAIR" -> false;
            case "PACKAGE_CHANGE" -> false;
            default -> false;
        };
    }

    private boolean isNoOpPackageChange(ShopifyStoreProvisioningJobEntity job) {
        return job != null
            && "PACKAGE_CHANGE".equalsIgnoreCase(job.getJobType())
            && "NO_PROFILE_CHANGE".equalsIgnoreCase(blankToEmpty(job.getProfileChangeStrategy()))
            && !job.isVectorReindexRequired();
    }

    private boolean hasActiveDeployment(String deploymentId) {
        if (!hasText(deploymentId)) {
            return false;
        }
        return deploymentRepository.findById(deploymentId.trim())
            .filter(deployment -> deployment.getArchivedAt() == null)
            .isPresent();
    }

    private void reconcileVectorizationSafely(ShopifyStoreConnectionEntity store, ShopifyStoreProvisioningJobEntity job) {
        try {
            vectorizationService.reconcileForTrustedCaller(store.getShopDomain());
        } catch (RuntimeException ex) {
            job.setNextAction("Review vectorization readiness and retry provisioning after the vectorization issue is resolved.");
            throw ex;
        }
    }

    private ShopifyStoreProvisioningJobEntity newJob(String shopDomain,
                                                     ShopifyStoreConnectionEntity store,
                                                     String jobType) {
        Instant now = Instant.now();
        ShopifyStoreProvisioningJobEntity job = new ShopifyStoreProvisioningJobEntity();
        job.setId("spj-" + UUID.randomUUID().toString().substring(0, 8));
        job.setShopDomain(shopDomain);
        job.setStoreConnectionId(store == null ? null : store.getId());
        job.setJobType(jobType);
        job.setStatus("QUEUED");
        job.setPhase("QUEUED");
        job.setRequestedPluginIdsJson("[]");
        job.setAttemptCount(0);
        job.setMaxAttempts(5);
        job.setVectorReindexRequired(false);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }

    private ShopifyStoreProvisioningJobEntity requireJob(String shopDomain, String jobId) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        ShopifyStoreProvisioningJobEntity job = jobRepository.findById(clean(jobId, "jobId"))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Provisioning job not found: " + jobId));
        if (!normalizedShop.equalsIgnoreCase(job.getShopDomain())) {
            throw new ResponseStatusException(NOT_FOUND, "Provisioning job not found: " + jobId);
        }
        return job;
    }

    private void markPhase(ShopifyStoreProvisioningJobEntity job, String phase, String message) {
        job.setStatus("RUNNING");
        job.setPhase(phase);
        job.setSummaryMessage(message);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        audit("SHOPIFY_STORE_PROVISIONING_PHASE", job, Map.of("phase", phase));
    }

    private void failJob(ShopifyStoreProvisioningJobEntity job, RuntimeException ex) {
        job.setStatus(job.getAttemptCount() >= job.getMaxAttempts() ? "FAILED" : "QUEUED");
        job.setPhase("FAILED");
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setFailedAt(Instant.now());
        job.setLastErrorCode(errorCode(ex));
        job.setLastErrorMessage(safeMessage(ex));
        job.setNextAction(job.getAttemptCount() >= job.getMaxAttempts()
            ? "Review the failure and queue an operator repair provisioning job."
            : "Provisioning will retry automatically.");
        job.setSummaryMessage("Shopify Companion provisioning failed: " + safeMessage(ex));
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        audit("SHOPIFY_STORE_PROVISIONING_FAILED", job, Map.of("errorCode", job.getLastErrorCode()));
    }

    private ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile resolveRequestedProfile(ShopifyStoreConnectionEntity store,
                                                                                                       CreateShopifyStoreProvisioningJobRequest request) {
        ShopifyStorePackageState current = readPackageState(store);
        return profileCatalogService.resolve(
            firstText(request == null ? null : request.requestedPackageKey(), current.packageKey()),
            firstText(request == null ? null : request.requestedTierKey(), current.tierKey()),
            firstText(request == null ? null : request.requestedRuntimeProfileKey(), current.runtimeProfileKey()),
            firstText(request == null ? null : request.requestedVectorProfileKey(), current.vectorProfileKey())
        );
    }

    private List<String> resolveRequestedPluginIds(CreateShopifyStoreProvisioningJobRequest request,
                                                  ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile) {
        LinkedHashSet<String> pluginIds = new LinkedHashSet<>();
        if (request != null && request.requestedPluginIds() != null) {
            request.requestedPluginIds().stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(pluginIds::add);
        }
        pluginIds.add(ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID);
        pluginIds.add(ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID);
        pluginIds.add(ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID);
        pluginIds.add(profile.inferencePluginId());
        return List.copyOf(pluginIds);
    }

    private ShopifyStorePackageState readPackageState(ShopifyStoreConnectionEntity store) {
        if (store == null) {
            return new ShopifyStorePackageState(null, null, null, null);
        }
        JsonNode root = detailsSupport.readJsonNode(store.getDetailsJson());
        JsonNode state = root == null ? null : root.path("packageState");
        if (state == null || !state.isObject()) {
            JsonNode billing = root == null ? null : root.path("billingState");
            String tier = billing == null || !billing.isObject() ? null : text(billing, "tierKey");
            return new ShopifyStorePackageState(tier, tier, null, null);
        }
        return new ShopifyStorePackageState(
            text(state, "packageKey"),
            text(state, "tierKey"),
            text(state, "runtimeProfileKey"),
            text(state, "vectorProfileKey")
        );
    }

    private String resolveProfileChangeStrategy(ShopifyStorePackageState previous,
                                                ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile) {
        if (!hasText(previous.runtimeProfileKey())) {
            return "INITIAL";
        }
        if (!profile.runtimeProfileKey().equalsIgnoreCase(previous.runtimeProfileKey())) {
            return "REPLACE_INFERENCE_PROFILE";
        }
        if (!profile.vectorProfileKey().equalsIgnoreCase(blankToEmpty(previous.vectorProfileKey()))) {
            return "RECONCILE_VECTOR_PROFILE";
        }
        return "NO_PROFILE_CHANGE";
    }

    private boolean vectorReindexRequired(ShopifyStorePackageState previous,
                                          ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile) {
        return hasText(previous.runtimeProfileKey())
            && (!profile.runtimeProfileKey().equalsIgnoreCase(previous.runtimeProfileKey())
                || !profile.vectorProfileKey().equalsIgnoreCase(blankToEmpty(previous.vectorProfileKey())));
    }

    private ShopifyStoreProvisioningJobSummary toSummary(ShopifyStoreProvisioningJobEntity job) {
        return new ShopifyStoreProvisioningJobSummary(
            job.getId(),
            job.getShopDomain(),
            job.getStoreConnectionId(),
            job.getJobType(),
            job.getStatus(),
            job.getPhase(),
            job.getRequestedPackageKey(),
            job.getRequestedTierKey(),
            job.getRequestedRuntimeProfileKey(),
            job.getRequestedVectorProfileKey(),
            job.getPreviousPackageKey(),
            job.getPreviousRuntimeProfileKey(),
            job.getPreviousVectorProfileKey(),
            job.getRequestedTemplatePluginId(),
            job.getRequestedTemplatePluginVersion(),
            readPluginIds(job.getRequestedPluginIdsJson()),
            job.getProfileChangeStrategy(),
            job.isVectorReindexRequired(),
            job.getInstallIntentId(),
            job.getAttemptCount(),
            job.getMaxAttempts(),
            job.getLastErrorCode(),
            job.getLastErrorMessage(),
            job.getBootstrapDeploymentId(),
            job.getVerificationRunId(),
            job.getNextAction(),
            job.getSummaryMessage(),
            job.getReadyAt(),
            job.getFailedAt(),
            job.getCancelledAt(),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }

    private String writePluginIds(List<String> pluginIds) {
        try {
            var array = JSON.arrayNode();
            if (pluginIds != null) {
                pluginIds.stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .distinct()
                    .forEach(array::add);
            }
            return array.toString();
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readPluginIds(String value) {
        JsonNode node = detailsSupport.readJsonNode(value);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .map(item -> item == null ? "" : item.asText(""))
            .filter(this::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private String normalizeJobType(String value, ShopifyStoreConnectionEntity store) {
        if (!StringUtils.hasText(value)) {
            return store == null || !hasText(store.getDeploymentId()) ? "INSTALL" : "PACKAGE_CHANGE";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "INSTALL" -> "INSTALL";
            case "REINSTALL", "REINSTALL_REPAIR" -> "REINSTALL_REPAIR";
            case "PACKAGE", "PACKAGE_CHANGE", "PROFILE_CHANGE" -> "PACKAGE_CHANGE";
            case "SOURCE_SETTINGS_CHANGE", "KNOWLEDGE_SOURCE_CHANGE" -> "SOURCE_SETTINGS_CHANGE";
            case "REPAIR", "MANUAL_REPAIR" -> "MANUAL_REPAIR";
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported Shopify provisioning job type: " + value);
        };
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!StringUtils.hasText(shopDomain)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, fieldName + " is required.");
        }
        return value.trim();
    }

    private String summaryForQueuedJob(String jobType,
                                       ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile) {
        return switch (jobType) {
            case "INSTALL" -> "Install provisioning queued for " + profile.displayName() + ".";
            case "REINSTALL_REPAIR" -> "Reinstall repair queued for " + profile.displayName() + ".";
            case "PACKAGE_CHANGE" -> "Package/profile reconciliation queued for " + profile.displayName() + ".";
            case "SOURCE_SETTINGS_CHANGE" -> "Knowledge source reconciliation queued for " + profile.displayName() + ".";
            case "MANUAL_REPAIR" -> "Operator repair queued for " + profile.displayName() + ".";
            default -> "Shopify Companion provisioning queued.";
        };
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return "HTTP_" + responseStatusException.getStatusCode().value();
        }
        return ex.getClass().getSimpleName().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex instanceof ResponseStatusException responseStatusException && hasText(responseStatusException.getReason())
            ? responseStatusException.getReason()
            : ex.getMessage();
        if (!hasText(message)) {
            return "Provisioning failed.";
        }
        return message.replaceAll("(?i)(token|secret|api[_-]?key|password)=\\S+", "$1=<redacted>");
    }

    private void audit(String action, ShopifyStoreProvisioningJobEntity job, Map<String, String> details) {
        platformAuditService.record(
            action,
            "SHOPIFY_STORE_PROVISIONING_JOB",
            job.getId(),
            new java.util.LinkedHashMap<>() {{
                put("shopDomain", job.getShopDomain());
                put("jobType", job.getJobType());
                put("status", job.getStatus());
                put("phase", job.getPhase());
                putAll(details);
            }}
        );
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : blankToNull(fallback);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.path(field).isValueNode()) {
            return null;
        }
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ShopifyStorePackageState(
        String packageKey,
        String tierKey,
        String runtimeProfileKey,
        String vectorProfileKey
    ) {
    }
}
