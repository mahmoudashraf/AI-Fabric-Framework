package com.ai.fabric.platform.backend.partner.web;

import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessDecisionRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessDecisionSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessRequestSummary;
import com.ai.fabric.platform.backend.partner.service.PartnerEnablementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchant/partner-access")
public class MerchantPartnerAccessController {

    private final PartnerEnablementService service;

    public MerchantPartnerAccessController(PartnerEnablementService service) {
        this.service = service;
    }

    @PostMapping("/{approvalCode}/approve")
    public MerchantPartnerAccessApprovalSummary approve(@PathVariable String approvalCode,
                                                        @Valid @RequestBody MerchantPartnerAccessApprovalRequest request) {
        return service.approveMerchantAccess(approvalCode, request);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public List<MerchantPartnerAccessRequestSummary> listRequests(@RequestParam String shopDomain) {
        return service.listMerchantAccessRequests(shopDomain);
    }

    @PostMapping("/requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public MerchantPartnerAccessDecisionSummary approveRequest(@PathVariable String requestId,
                                                               @RequestParam String shopDomain,
                                                               @Valid @RequestBody MerchantPartnerAccessDecisionRequest request) {
        return service.approveMerchantAccessRequest(requestId, shopDomain, request);
    }

    @PostMapping("/requests/{requestId}/deny")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public MerchantPartnerAccessDecisionSummary denyRequest(@PathVariable String requestId,
                                                            @RequestParam String shopDomain,
                                                            @Valid @RequestBody MerchantPartnerAccessDecisionRequest request) {
        return service.denyMerchantAccessRequest(requestId, shopDomain, request);
    }

    @PostMapping("/requests/{requestId}/revoke")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public MerchantPartnerAccessDecisionSummary revokeRequest(@PathVariable String requestId,
                                                              @RequestParam String shopDomain,
                                                              @Valid @RequestBody MerchantPartnerAccessDecisionRequest request) {
        return service.revokeMerchantAccessRequest(requestId, shopDomain, request);
    }
}
