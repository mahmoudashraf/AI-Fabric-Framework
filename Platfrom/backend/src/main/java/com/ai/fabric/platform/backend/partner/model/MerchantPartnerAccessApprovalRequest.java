package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantPartnerAccessApprovalRequest(
    @NotBlank @Size(max = 255) String approverName,
    @Email @Size(max = 255) String approverEmail,
    @Size(max = 64) String approvedScope
) {
}
