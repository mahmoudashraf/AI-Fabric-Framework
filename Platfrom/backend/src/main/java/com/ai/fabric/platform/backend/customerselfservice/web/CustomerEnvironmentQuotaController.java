package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.service.CustomerEnvironmentQuotaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-self-service")
public class CustomerEnvironmentQuotaController {
    private final CustomerOfferEntitlementService access;
    private final CustomerEnvironmentQuotaService quotas;

    public CustomerEnvironmentQuotaController(CustomerOfferEntitlementService access, CustomerEnvironmentQuotaService quotas) {
        this.access = access;
        this.quotas = quotas;
    }

    @GetMapping("/infrastructure-quota")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public CustomerEnvironmentQuotaService.Summary mine() {
        return quotas.summary(access.requireCustomerActor().customerId());
    }

    @GetMapping("/staff/customers/{customerId}/infrastructure-quota")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CustomerEnvironmentQuotaService.Summary staff(@PathVariable String customerId) {
        access.requireStaffAdmin();
        return quotas.summary(customerId);
    }

    @PutMapping("/staff/customers/{customerId}/infrastructure-quota")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CustomerEnvironmentQuotaService.Summary update(@PathVariable String customerId, @RequestBody Update request) {
        access.requireStaffAdmin();
        return quotas.updateLimit(customerId, request.limit(), request.reason());
    }

    public record Update(int limit, String reason) { }
}
