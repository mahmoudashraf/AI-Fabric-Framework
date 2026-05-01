package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Size;

import java.util.List;

public record PartnerMemberUpdateRequest(
    @Size(max = 64) String role,
    @Size(max = 64) String status,
    List<@Size(max = 64) String> privileges
) {
}
