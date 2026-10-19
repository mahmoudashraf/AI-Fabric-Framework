package com.ai.fabric.platform.backend.deployment.budget;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;

/** Staff review only; no public allocation or runtime initialization route. */
@RestController
@RequestMapping("/api/deployments/provider-usage/grants")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class ProviderUsageGrantController {
    private final ProviderUsageAllowanceService allowances;

    public ProviderUsageGrantController(ProviderUsageAllowanceService allowances) { this.allowances = allowances; }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public GrantQuotaView approve(@RequestBody ProviderUsageGrant grant) { return allowances.approveGrant(grant); }

    @GetMapping("/{grantId}/quota")
    public GrantQuotaView quota(@PathVariable String grantId) { return allowances.readQuota(grantId); }

    @PostMapping("/{grantId}/void-unused")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public VoidUnusedReceipt voidUnused(@PathVariable String grantId, @RequestBody VoidUnusedCommand command) {
        return allowances.voidUnused(grantId, command);
    }
}
