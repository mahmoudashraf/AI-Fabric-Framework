package com.ai.fabric.platform.backend.productservice.web;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.productservice.model.CreatePlatformManagedProductServiceRequest;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceDeploymentHistorySummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceHealthSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceOverviewSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceRailwayLogsSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreBillingSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceWebhookSubscriptionSummary;
import com.ai.fabric.platform.backend.productservice.model.RotatePlatformManagedProductServiceSecretRequest;
import com.ai.fabric.platform.backend.productservice.model.UpdatePlatformManagedProductServiceScaleRequest;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductAdminService;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBindingInspectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product-services")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class ProductServiceController {

    private final PlatformManagedProductServiceService serviceService;
    private final PlatformManagedProductAdminService adminService;

    public ProductServiceController(PlatformManagedProductServiceService serviceService,
                                    PlatformManagedProductAdminService adminService) {
        this.serviceService = serviceService;
        this.adminService = adminService;
    }

    @GetMapping
    public List<PlatformManagedProductServiceSummary> listServices() {
        return serviceService.listServices();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformManagedProductServiceSummary createService(@Valid @RequestBody CreatePlatformManagedProductServiceRequest request) {
        return serviceService.createService(request);
    }

    @GetMapping("/{serviceRef}")
    public PlatformManagedProductServiceSummary getService(@PathVariable String serviceRef) {
        return adminService.getServiceSummary(serviceRef);
    }

    @GetMapping("/{serviceRef}/dependents")
    public List<ShopifyStoreConnectionSummary> listDependents(@PathVariable String serviceRef) {
        return adminService.listDependents(serviceRef);
    }

    @GetMapping("/{serviceRef}/stores/{shopDomain}/binding")
    public ShopifyStoreBindingInspectionSummary getStoreBinding(@PathVariable String serviceRef,
                                                                @PathVariable String shopDomain) {
        return adminService.getStoreBinding(serviceRef, shopDomain);
    }

    @GetMapping("/{serviceRef}/activity")
    public List<PlatformAuditEventSummary> listActivity(@PathVariable String serviceRef) {
        return adminService.listActivity(serviceRef);
    }

    @GetMapping("/{serviceRef}/railway/deployments")
    public PlatformManagedProductServiceDeploymentHistorySummary getDeploymentHistory(@PathVariable String serviceRef,
                                                                                      @RequestParam(required = false) Integer limit) {
        return adminService.getDeploymentHistory(serviceRef, limit);
    }

    @GetMapping("/{serviceRef}/railway/logs")
    public PlatformManagedProductServiceRailwayLogsSummary getRailwayLogs(@PathVariable String serviceRef,
                                                                          @RequestParam(required = false) String source,
                                                                          @RequestParam(required = false) String deploymentId,
                                                                          @RequestParam(required = false) Integer limit,
                                                                          @RequestParam(required = false) String filter,
                                                                          @RequestParam(required = false) String startDate,
                                                                          @RequestParam(required = false) String endDate) {
        return adminService.getRailwayLogs(serviceRef, source, deploymentId, limit, filter, startDate, endDate);
    }

    @GetMapping("/{serviceRef}/health")
    public PlatformManagedProductServiceHealthSummary getHealth(@PathVariable String serviceRef) {
        return adminService.getHealth(serviceRef);
    }

    @GetMapping("/{serviceRef}/overview")
    public PlatformManagedProductServiceOverviewSummary getOverview(@PathVariable String serviceRef) {
        return adminService.getOverview(serviceRef);
    }

    @GetMapping("/{serviceRef}/stores/{shopDomain}/webhook-subscriptions")
    public PlatformManagedProductServiceWebhookSubscriptionSummary getStoreWebhookSubscriptions(@PathVariable String serviceRef,
                                                                                                 @PathVariable String shopDomain) {
        return adminService.getStoreWebhookSubscriptions(serviceRef, shopDomain);
    }

    @GetMapping("/{serviceRef}/stores/{shopDomain}/billing-summary")
    public PlatformManagedProductServiceStoreBillingSummary getStoreBillingSummary(@PathVariable String serviceRef,
                                                                                   @PathVariable String shopDomain) {
        return adminService.getStoreBillingSummary(serviceRef, shopDomain);
    }

    @PostMapping("/{serviceRef}/stores/{shopDomain}/run-source-preflight")
    public ShopifyStoreConnectionSummary runStoreSourcePreflight(@PathVariable String serviceRef,
                                                                 @PathVariable String shopDomain) {
        return adminService.runStoreSourcePreflight(serviceRef, shopDomain);
    }

    @PostMapping("/{serviceRef}/reconcile")
    public PlatformManagedProductServiceSummary reconcile(@PathVariable String serviceRef) {
        return adminService.reconcile(serviceRef);
    }

    @PostMapping("/{serviceRef}/restart")
    public PlatformManagedProductServiceSummary restart(@PathVariable String serviceRef) {
        return adminService.restart(serviceRef);
    }

    @PostMapping("/{serviceRef}/force-recreate")
    public PlatformManagedProductServiceSummary forceRecreate(@PathVariable String serviceRef) {
        return adminService.forceRecreate(serviceRef);
    }

    @PostMapping("/{serviceRef}/decommission")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformManagedProductServiceSummary decommission(@PathVariable String serviceRef) {
        return adminService.decommission(serviceRef);
    }

    @PutMapping("/{serviceRef}/scale")
    public PlatformManagedProductServiceSummary scale(@PathVariable String serviceRef,
                                                      @Valid @RequestBody UpdatePlatformManagedProductServiceScaleRequest request) {
        return adminService.scale(serviceRef, request.desiredReplicas());
    }

    @PutMapping("/{serviceRef}/rotate-secret")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformManagedProductServiceSummary rotateSecret(@PathVariable String serviceRef,
                                                             @Valid @RequestBody RotatePlatformManagedProductServiceSecretRequest request) {
        return adminService.rotateSecret(serviceRef, request.value());
    }
}
