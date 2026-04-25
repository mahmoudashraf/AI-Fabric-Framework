package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Size;

public record PartnerMemberUpdateRequest(
    @Size(max = 64) String role,
    @Size(max = 64) String status
) {
}
