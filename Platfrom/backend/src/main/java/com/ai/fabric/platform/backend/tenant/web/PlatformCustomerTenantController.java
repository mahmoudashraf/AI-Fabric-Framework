package com.ai.fabric.platform.backend.tenant.web;

import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerBindingHistorySummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorHandleSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSummary;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesRequest;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
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
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
@RequestMapping("/api/platform/customers")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CUSTOMER_ADMIN')")
public class PlatformCustomerTenantController {

    private final PlatformCustomerTenantService platformCustomerTenantService;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;

    public PlatformCustomerTenantController(PlatformCustomerTenantService platformCustomerTenantService,
                                            PlatformCustomerConsumerService platformCustomerConsumerService) {
        this.platformCustomerTenantService = platformCustomerTenantService;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
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

    @GetMapping("/{customerId}/consumers")
    public List<PlatformConsumerSummary> listConsumers(@PathVariable String customerId) {
        return platformCustomerConsumerService.listConsumers(customerId);
    }

    @PostMapping("/{customerId}/consumers")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformConsumerSummary createConsumer(@PathVariable String customerId,
                                                  @Valid @RequestBody CreatePlatformConsumerRequest request) {
        return platformCustomerConsumerService.createConsumer(customerId, request);
    }

    @PutMapping("/{customerId}/consumers/{consumerId}")
    public PlatformConsumerSummary updateConsumer(@PathVariable String customerId,
                                                  @PathVariable String consumerId,
                                                  @Valid @RequestBody UpdatePlatformConsumerRequest request) {
        return platformCustomerConsumerService.updateConsumer(customerId, consumerId, request);
    }

    @PutMapping("/{customerId}/consumers/{consumerId}/binding")
    public PlatformConsumerSummary updateConsumerBinding(@PathVariable String customerId,
                                                         @PathVariable String consumerId,
                                                         @RequestBody UpdatePlatformConsumerBindingRequest request) {
        return platformCustomerConsumerService.updateBinding(customerId, consumerId, request);
    }

    @DeleteMapping("/{customerId}/consumers/{consumerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConsumer(@PathVariable String customerId, @PathVariable String consumerId) {
        platformCustomerConsumerService.deleteConsumer(customerId, consumerId);
    }

    @GetMapping("/{customerId}/consumers/{consumerId}/history")
    public List<PlatformConsumerBindingHistorySummary> listConsumerBindingHistory(@PathVariable String customerId,
                                                                                  @PathVariable String consumerId) {
        return platformCustomerConsumerService.listBindingHistory(customerId, consumerId);
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
