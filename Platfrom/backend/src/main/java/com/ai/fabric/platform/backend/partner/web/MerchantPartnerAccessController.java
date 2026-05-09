package com.ai.fabric.platform.backend.partner.web;

import com.ai.fabric.platform.backend.partner.model.MerchantLaunchWorkspaceSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessDecisionRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessDecisionSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessInviteRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessInviteSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessRequestSummary;
import com.ai.fabric.platform.backend.partner.model.MerchantRollbackRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantRollbackRequestSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerProductionPromotionSummary;
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

    @GetMapping("/{approvalCode}/workspace")
    public MerchantLaunchWorkspaceSummary workspace(@PathVariable String approvalCode) {
        return service.getMerchantWorkspace(approvalCode);
    }

    @PostMapping("/{approvalCode}/approve")
    public MerchantPartnerAccessApprovalSummary approve(@PathVariable String approvalCode,
                                                        @Valid @RequestBody MerchantPartnerAccessApprovalRequest request) {
        return service.approveMerchantAccess(approvalCode, request);
    }

    @PostMapping("/{approvalCode}/deny")
    public MerchantPartnerAccessDecisionSummary deny(@PathVariable String approvalCode,
                                                     @Valid @RequestBody MerchantPartnerAccessDecisionRequest request) {
        return service.denyMerchantAccess(approvalCode, request);
    }

    @PostMapping("/{approvalCode}/revoke")
    public MerchantPartnerAccessDecisionSummary revoke(@PathVariable String approvalCode,
                                                       @Valid @RequestBody MerchantPartnerAccessDecisionRequest request) {
        return service.revokeMerchantAccess(approvalCode, request);
    }

    @PostMapping("/{approvalCode}/production-promotions")
    public PartnerProductionPromotionSummary requestProductionPromotion(@PathVariable String approvalCode) {
        return service.requestMerchantProductionPromotion(approvalCode);
    }

    @PostMapping("/{approvalCode}/rollback-requests")
    public MerchantRollbackRequestSummary requestRollback(@PathVariable String approvalCode,
                                                          @Valid @RequestBody MerchantRollbackRequest request) {
        return service.requestMerchantRollback(approvalCode, request);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public List<MerchantPartnerAccessRequestSummary> listRequests(@RequestParam String shopDomain) {
        return service.listMerchantAccessRequests(shopDomain);
    }

    @PostMapping("/requests/{requestId}/invite")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public MerchantPartnerAccessInviteSummary sendInvite(@PathVariable String requestId,
                                                         @RequestParam String shopDomain,
                                                         @Valid @RequestBody(required = false) MerchantPartnerAccessInviteRequest request) {
        return service.sendMerchantInviteForAccessRequest(requestId, shopDomain, request == null ? new MerchantPartnerAccessInviteRequest(null) : request);
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
