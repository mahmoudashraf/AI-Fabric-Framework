package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Size;

public record PartnerProfileUpdateRequest(
    @Size(max = 255) String displayName,
    @Size(max = 2048) String avatarUrl
) {
}
