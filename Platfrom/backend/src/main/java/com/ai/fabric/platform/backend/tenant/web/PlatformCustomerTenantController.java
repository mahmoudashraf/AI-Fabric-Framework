package com.ai.fabric.platform.backend.tenant.web;

import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorHandleSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSummary;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesRequest;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/platform/customers")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CUSTOMER_ADMIN')")
public class PlatformCustomerTenantController {

    private final PlatformCustomerTenantService platformCustomerTenantService;

    public PlatformCustomerTenantController(PlatformCustomerTenantService platformCustomerTenantService) {
        this.platformCustomerTenantService = platformCustomerTenantService;
    }

    @GetMapping
    public List<PlatformCustomerSummary> listCustomers() {
        return platformCustomerTenantService.listCustomers();
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformCustomerSummary createCustomer(@Valid @RequestBody CreatePlatformCustomerRequest request) {
        return platformCustomerTenantService.createCustomer(request);
    }

    @PutMapping("/{customerId}")
    public PlatformCustomerSummary updateCustomer(@PathVariable String customerId,
                                                  @Valid @RequestBody UpdatePlatformCustomerRequest request) {
        return platformCustomerTenantService.updateCustomer(customerId, request);
    }

    @PostMapping("/{customerId}/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformTenantSummary createTenant(@PathVariable String customerId,
                                              @Valid @RequestBody CreatePlatformTenantRequest request) {
        return platformCustomerTenantService.createTenant(customerId, request);
    }

    @PutMapping("/tenants/{tenantId}")
    public PlatformTenantSummary updateTenant(@PathVariable String tenantId,
                                              @Valid @RequestBody UpdatePlatformTenantRequest request) {
        return platformCustomerTenantService.updateTenant(tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/shared-vector-handles")
    public List<PlatformTenantSharedVectorHandleSummary> listTenantSharedVectorHandles(@PathVariable String tenantId) {
        return platformCustomerTenantService.listTenantSharedVectorHandles(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/shared-vector-handles/purge")
    public PurgePlatformTenantSharedVectorHandlesSummary purgeTenantSharedVectorHandles(
        @PathVariable String tenantId,
        @RequestBody PurgePlatformTenantSharedVectorHandlesRequest request
    ) {
        return platformCustomerTenantService.purgeTenantSharedVectorHandles(tenantId, request);
    }
}
