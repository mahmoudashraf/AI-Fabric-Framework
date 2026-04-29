package com.ai.fabric.platform.backend.shopify.web;

import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileOptionsSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyCompanionPackageProfileStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyCompanionPackageProfileRequest;
import com.ai.fabric.platform.backend.shopify.service.ShopifyCompanionPackageProfileCatalogService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyCompanionPackageProfileOptionsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shopify/package-profiles")
public class ShopifyCompanionPackageProfileController {

    private final ShopifyCompanionPackageProfileCatalogService profileCatalogService;
    private final ShopifyCompanionPackageProfileOptionsService profileOptionsService;

    public ShopifyCompanionPackageProfileController(ShopifyCompanionPackageProfileCatalogService profileCatalogService,
                                                    ShopifyCompanionPackageProfileOptionsService profileOptionsService) {
        this.profileCatalogService = profileCatalogService;
        this.profileOptionsService = profileOptionsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public List<ShopifyCompanionPackageProfileSummary> listProfiles(
        @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly
    ) {
        return profileCatalogService.listProfiles(activeOnly);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ShopifyCompanionPackageProfileOptionsSummary options() {
        return profileOptionsService.options();
    }

    @GetMapping("/{profileKey}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ShopifyCompanionPackageProfileSummary getProfile(@PathVariable String profileKey) {
        return profileCatalogService.getProfile(profileKey);
    }

    @PutMapping("/{profileKey}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyCompanionPackageProfileSummary upsertProfile(
        @PathVariable String profileKey,
        @RequestBody UpsertShopifyCompanionPackageProfileRequest request
    ) {
        return profileCatalogService.upsertProfile(profileKey, request);
    }

    @PostMapping("/{profileKey}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyCompanionPackageProfileSummary updateProfileStatus(
        @PathVariable String profileKey,
        @RequestBody UpdateShopifyCompanionPackageProfileStatusRequest request
    ) {
        return profileCatalogService.updateProfileStatus(profileKey, request);
    }
}
