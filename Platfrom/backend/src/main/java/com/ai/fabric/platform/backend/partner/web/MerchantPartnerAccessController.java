package com.ai.fabric.platform.backend.partner.web;

import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalRequest;
import com.ai.fabric.platform.backend.partner.model.MerchantPartnerAccessApprovalSummary;
import com.ai.fabric.platform.backend.partner.service.PartnerEnablementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
