package com.ai.fabric.platform.backend.partner.web;

import com.ai.fabric.platform.backend.partner.model.PartnerCatalogEntrySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerEligibleStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSessionSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSignupCompleteRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreAccessLinkSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationCreateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplyRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportThreadSummary;
import com.ai.fabric.platform.backend.partner.service.PartnerEnablementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/partners")
public class PartnerEnablementController {

    private final PartnerEnablementService service;

    public PartnerEnablementController(PartnerEnablementService service) {
        this.service = service;
    }

    @GetMapping("/session")
    @PreAuthorize("hasAnyRole('PARTNER_AUTHENTICATED','PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerSessionSummary session() {
        return service.session();
    }

    @PostMapping("/signup/complete")
    @PreAuthorize("hasAnyRole('PARTNER_AUTHENTICATED','PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerSessionSummary completeSignup(@Valid @RequestBody PartnerSignupCompleteRequest request) {
        return service.completeSignup(request);
    }

    @GetMapping("/stores")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerStoreSummary> listStores() {
        return service.listStores();
    }

    @GetMapping("/stores/{storeId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerStoreSummary getStore(@PathVariable String storeId) {
        return service.getStore(storeId);
    }

    @GetMapping("/eligible-stores")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public List<PartnerEligibleStoreSummary> listEligibleStores(@RequestParam(name = "query", required = false) String query) {
        return service.listEligibleStores(query);
    }

    @PostMapping("/client-implementations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerClientImplementationSummary createImplementation(@Valid @RequestBody PartnerClientImplementationRequest request) {
        return service.createImplementation(request);
    }

    @GetMapping("/client-implementations")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerClientImplementationSummary> listImplementations() {
        return service.listImplementations();
    }

    @GetMapping("/client-implementations/{requestId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerClientImplementationSummary getImplementation(@PathVariable String requestId) {
        return service.getImplementation(requestId);
    }

    @PostMapping("/client-implementations/{requestId}/store-access-links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER')")
    public PartnerStoreAccessLinkSummary createStoreAccessLink(@PathVariable String requestId) {
        return service.createStoreAccessLink(requestId);
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerCatalogEntrySummary> listCatalog() {
        return service.listCatalog();
    }

    @GetMapping("/support/escalations")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public List<PartnerSupportEscalationSummary> listEscalations() {
        return service.listEscalations();
    }

    @PostMapping("/stores/{storeId}/escalations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public PartnerSupportEscalationSummary createEscalation(@PathVariable String storeId,
                                                            @Valid @RequestBody PartnerSupportEscalationCreateRequest request) {
        return service.createEscalation(storeId, request);
    }

    @GetMapping("/escalations/{escalationId}/thread")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public PartnerSupportThreadSummary getEscalationThread(@PathVariable String escalationId) {
        return service.getEscalationThread(escalationId);
    }

    @PostMapping("/escalations/{escalationId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public PartnerSupportReplySummary addEscalationReply(@PathVariable String escalationId,
                                                         @Valid @RequestBody PartnerSupportReplyRequest request) {
        return service.addEscalationReply(escalationId, request);
    }
}
