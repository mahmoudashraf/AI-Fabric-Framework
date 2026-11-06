package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.config.CustomerOnboardingProperties;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitContracts.*;

/** Staff acts as staff; customer ownership is explicit and never transferred. */
@RestController
@RequestMapping("/api/staff/customers/{customerId}/experience-kits")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class StaffExperienceKitController {
    private final ExperienceKitService kits;
    private final CustomerOfferEntitlementService access;
    private final CustomerOnboardingProperties onboarding;

    public StaffExperienceKitController(ExperienceKitService kits, CustomerOfferEntitlementService access,
                                       CustomerOnboardingProperties onboarding) {
        this.kits = kits;
        this.access = access;
        this.onboarding = onboarding;
    }

    @PostMapping(value="/prepare", consumes="application/json")
    public void prepare(@PathVariable String customerId) {
        access.enrollPublicTemplatesForCustomer(customerId, onboarding.templateOfferIds());
    }

    @GetMapping("/catalogue")
    public List<CatalogueEntry> catalogue(@PathVariable String customerId) { return kits.customerCatalogueForStaff(customerId); }

    @GetMapping
    public List<Summary> list(@PathVariable String customerId) { return kits.listForStaff(customerId); }

    @GetMapping("/{id}")
    public Summary get(@PathVariable String customerId, @PathVariable String id) { return kits.getForStaff(customerId, id); }

    @PostMapping
    public Summary create(@PathVariable String customerId, @Valid @RequestBody CreateRequest request) {
        return kits.createForStaff(customerId, request);
    }

    @PutMapping("/{id}")
    public Summary update(@PathVariable String customerId, @PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        return kits.updateForStaff(customerId, id, request);
    }
}
