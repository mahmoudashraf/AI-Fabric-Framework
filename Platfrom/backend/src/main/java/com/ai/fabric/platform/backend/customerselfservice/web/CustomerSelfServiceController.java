package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer-self-service")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerSelfServiceController {
    private final CustomerSelfServiceCatalogueService catalogue;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerOfferCodec codec;
    private final CustomerPlacementProbeService placementProbes;
    private final CustomerDeploymentBootstrapService bootstrap;

    public CustomerSelfServiceController(CustomerSelfServiceCatalogueService catalogue,
                                         CustomerOfferEntitlementService entitlements, CustomerOfferCodec codec,
                                         CustomerPlacementProbeService placementProbes,
                                         CustomerDeploymentBootstrapService bootstrap) {
        this.catalogue = catalogue;
        this.entitlements = entitlements;
        this.codec = codec;
        this.placementProbes = placementProbes;
        this.bootstrap = bootstrap;
    }

    @GetMapping("/catalogue")
    public List<OfferSummary> catalogue() { return catalogue.catalogue(); }

    @GetMapping("/catalogue/{offerId}")
    public OfferSummary offer(@PathVariable String offerId) { return catalogue.offer(offerId); }

    @PostMapping("/catalogue/{offerId}/deployments")
    public com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Result bootstrap(
        @PathVariable String offerId, @RequestBody JsonNode body) {
        return bootstrap.bootstrap(offerId, decode(body,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request.class));
    }

    @PostMapping("/catalogue/{offerId}/targets/{targetProfileId}/placement-evidence")
    public PlacementEvidenceSummary probe(@PathVariable String offerId, @PathVariable String targetProfileId) {
        return placementProbes.probeForOffer(offerId, targetProfileId);
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/staff/targets/{targetProfileId}/placement-evidence")
    public PlacementEvidenceSummary staffProbe(@PathVariable String targetProfileId, @RequestBody JsonNode body) {
        return placementProbes.probeForStaff(targetProfileId, decode(body, PlacementProbeRequest.class));
    }

    @GetMapping("/invitations")
    public List<EntitlementSummary> invitations() { return entitlements.myInvitations(); }

    @PostMapping("/invitations/{entitlementId}/accept")
    public EntitlementSummary accept(@PathVariable String entitlementId, @RequestBody JsonNode body) {
        return entitlements.accept(entitlementId, decode(body, EntitlementCommand.class));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/staff/offers")
    public Map<String, String> publish(@RequestBody JsonNode body) {
        var request = decode(body, PublishOfferRequest.class);
        return Map.of("offerId", request.offerId(), "fingerprint", entitlements.publish(request));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/staff/offers/{offerId}/invitations")
    public EntitlementSummary invite(@PathVariable String offerId, @RequestBody JsonNode body) {
        return entitlements.invite(offerId, decode(body, InviteRequest.class));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/staff/entitlements/{entitlementId}/revoke")
    public EntitlementSummary revoke(@PathVariable String entitlementId, @RequestBody JsonNode body) {
        return entitlements.revoke(entitlementId, decode(body, EntitlementCommand.class));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/staff/customers/{customerId}/entitlements")
    public List<EntitlementSummary> staffEntitlements(@PathVariable String customerId) {
        return entitlements.staffEntitlements(customerId);
    }

    private <T> T decode(JsonNode body, Class<T> type) { return codec.read(codec.write(body), type); }
}
