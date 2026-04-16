package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplacePublisherRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplacePublisherSubmissionRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceImpactSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallResolutionSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceCategorySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginDetailSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherDetailSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherSubmissionSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.model.ReviewMarketplacePublisherSubmissionRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdatePlatformManagedInferenceServiceScaleRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceEntitlementRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateMarketplacePublisherVerificationRequest;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedInferenceProvisioningService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplacePublisherService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplacePublishingService;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class MarketplaceController {

    private final MarketplaceCatalogService marketplaceCatalogService;
    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;
    private final MarketplaceTemplateBootstrapService marketplaceTemplateBootstrapService;
    private final MarketplacePublisherService marketplacePublisherService;
    private final MarketplacePublishingService marketplacePublishingService;
    private final PlatformManagedInferenceServiceService platformManagedInferenceServiceService;
    private final PlatformManagedInferenceProvisioningService platformManagedInferenceProvisioningService;

    public MarketplaceController(MarketplaceCatalogService marketplaceCatalogService,
                                 DeploymentMarketplaceInstallService deploymentMarketplaceInstallService,
                                 MarketplaceTemplateBootstrapService marketplaceTemplateBootstrapService,
                                 MarketplacePublisherService marketplacePublisherService,
                                 MarketplacePublishingService marketplacePublishingService,
                                 PlatformManagedInferenceServiceService platformManagedInferenceServiceService,
                                 PlatformManagedInferenceProvisioningService platformManagedInferenceProvisioningService) {
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
        this.marketplaceTemplateBootstrapService = marketplaceTemplateBootstrapService;
        this.marketplacePublisherService = marketplacePublisherService;
        this.marketplacePublishingService = marketplacePublishingService;
        this.platformManagedInferenceServiceService = platformManagedInferenceServiceService;
        this.platformManagedInferenceProvisioningService = platformManagedInferenceProvisioningService;
    }

    @GetMapping("/marketplace/plugins")
    public List<MarketplacePluginSummary> listPlugins() {
        return marketplaceCatalogService.listPlugins();
    }

    @GetMapping("/marketplace/plugins/{pluginId}")
    public MarketplacePluginDetailSummary getPlugin(@PathVariable String pluginId) {
        return marketplaceCatalogService.getPlugin(pluginId);
    }

    @GetMapping("/marketplace/plugins/{pluginId}/versions/{version}")
    public MarketplacePluginVersionSummary getPluginVersion(@PathVariable String pluginId,
                                                            @PathVariable String version) {
        return marketplaceCatalogService.getPluginVersion(pluginId, version);
    }

    @GetMapping("/marketplace/categories")
    public List<MarketplaceCategorySummary> listCategories() {
        return marketplaceCatalogService.listCategories();
    }

    @GetMapping("/marketplace/publishers")
    public List<MarketplacePublisherSummary> listPublishers() {
        return marketplacePublisherService.listPublishers();
    }

    @GetMapping("/marketplace/publishers/{publisherId}")
    public MarketplacePublisherDetailSummary getPublisher(@PathVariable String publisherId) {
        return marketplacePublishingService.getPublisherDetail(publisherId);
    }

    @GetMapping("/marketplace/inference-services")
    public List<PlatformManagedInferenceServiceSummary> listInferenceServices() {
        return platformManagedInferenceServiceService.listServices();
    }

    @GetMapping("/marketplace/inference-services/{serviceRef}")
    public PlatformManagedInferenceServiceSummary getInferenceService(@PathVariable String serviceRef) {
        return platformManagedInferenceServiceService.getService(serviceRef);
    }

    @PostMapping("/marketplace/inference-services/{serviceRef}/reconcile")
    public PlatformManagedInferenceServiceSummary reconcileInferenceService(@PathVariable String serviceRef) {
        return platformManagedInferenceProvisioningService.reconcile(serviceRef);
    }

    @PutMapping("/marketplace/inference-services/{serviceRef}/scale")
    public PlatformManagedInferenceServiceSummary scaleInferenceService(@PathVariable String serviceRef,
                                                                        @Valid @RequestBody UpdatePlatformManagedInferenceServiceScaleRequest request) {
        return platformManagedInferenceProvisioningService.scale(serviceRef, request.desiredReplicas());
    }

    @PostMapping("/marketplace/publishers")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketplacePublisherSummary createPublisher(@Valid @RequestBody CreateMarketplacePublisherRequest request) {
        return marketplacePublisherService.createPublisher(request);
    }

    @PutMapping("/marketplace/publishers/{publisherId}/verification")
    public MarketplacePublisherSummary updatePublisherVerification(@PathVariable String publisherId,
                                                                  @RequestBody UpdateMarketplacePublisherVerificationRequest request) {
        return marketplacePublisherService.updatePublisherVerification(publisherId, request);
    }

    @PostMapping("/marketplace/publishers/{publisherId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketplacePublisherSubmissionSummary createPublisherSubmission(@PathVariable String publisherId,
                                                                           @RequestBody CreateMarketplacePublisherSubmissionRequest request) {
        return marketplacePublishingService.submitPublisherVersion(publisherId, request);
    }

    @PostMapping("/marketplace/submissions/{pluginVersionId}/validate")
    public MarketplacePublisherSubmissionSummary validateSubmission(@PathVariable String pluginVersionId,
                                                                    @RequestBody(required = false) ReviewMarketplacePublisherSubmissionRequest request) {
        return marketplacePublishingService.validateSubmission(
            pluginVersionId,
            request == null ? new ReviewMarketplacePublisherSubmissionRequest(null) : request
        );
    }

    @PostMapping("/marketplace/submissions/{pluginVersionId}/publish")
    public MarketplacePublisherSubmissionSummary publishSubmission(@PathVariable String pluginVersionId,
                                                                   @RequestBody(required = false) ReviewMarketplacePublisherSubmissionRequest request) {
        return marketplacePublishingService.publishSubmission(
            pluginVersionId,
            request == null ? new ReviewMarketplacePublisherSubmissionRequest(null) : request
        );
    }

    @PostMapping("/marketplace/submissions/{pluginVersionId}/reject")
    public MarketplacePublisherSubmissionSummary rejectSubmission(@PathVariable String pluginVersionId,
                                                                  @RequestBody(required = false) ReviewMarketplacePublisherSubmissionRequest request) {
        return marketplacePublishingService.rejectSubmission(
            pluginVersionId,
            request == null ? new ReviewMarketplacePublisherSubmissionRequest(null) : request
        );
    }

    @PostMapping("/marketplace/templates/{pluginId}/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentSummary bootstrapTemplatePlugin(@PathVariable String pluginId,
                                                     @Valid @RequestBody CreateMarketplaceTemplateBootstrapRequest request) {
        return marketplaceTemplateBootstrapService.bootstrap(pluginId, request);
    }

    @GetMapping("/deployments/{deploymentId}/marketplace-installs")
    public List<DeploymentMarketplaceInstallSummary> listDeploymentInstalls(@PathVariable String deploymentId) {
        return deploymentMarketplaceInstallService.listInstalls(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/marketplace-impact")
    public DeploymentMarketplaceImpactSummary getDeploymentImpact(@PathVariable String deploymentId) {
        return deploymentMarketplaceInstallService.getImpact(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/marketplace-installs")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentMarketplaceInstallSummary createDeploymentInstall(@PathVariable String deploymentId,
                                                                       @Valid @RequestBody CreateDeploymentMarketplaceInstallRequest request) {
        return deploymentMarketplaceInstallService.createInstall(deploymentId, request);
    }

    @PutMapping("/deployments/{deploymentId}/marketplace-installs/{installId}")
    public DeploymentMarketplaceInstallSummary updateDeploymentInstall(@PathVariable String deploymentId,
                                                                       @PathVariable String installId,
                                                                       @RequestBody UpdateDeploymentMarketplaceInstallRequest request) {
        return deploymentMarketplaceInstallService.updateInstall(deploymentId, installId, request);
    }

    @PutMapping("/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement")
    public DeploymentMarketplaceInstallSummary updateDeploymentInstallEntitlement(@PathVariable String deploymentId,
                                                                                  @PathVariable String installId,
                                                                                  @RequestBody UpdateDeploymentMarketplaceEntitlementRequest request) {
        return deploymentMarketplaceInstallService.updateEntitlement(deploymentId, installId, request);
    }

    @DeleteMapping("/deployments/{deploymentId}/marketplace-installs/{installId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeploymentInstall(@PathVariable String deploymentId,
                                        @PathVariable String installId) {
        deploymentMarketplaceInstallService.deleteInstall(deploymentId, installId);
    }

    @PostMapping("/deployments/{deploymentId}/marketplace-installs/{installId}/resolve")
    public DeploymentMarketplaceInstallResolutionSummary resolveDeploymentInstall(@PathVariable String deploymentId,
                                                                                  @PathVariable String installId) {
        return deploymentMarketplaceInstallService.resolveInstall(deploymentId, installId);
    }
}
