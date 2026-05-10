package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record MerchantPartnerAccessInviteRequest(
    @Email @Size(max = 255) String recipientEmail
) {
}
