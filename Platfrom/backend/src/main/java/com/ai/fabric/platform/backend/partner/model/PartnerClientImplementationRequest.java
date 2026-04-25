package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PartnerClientImplementationRequest(
    @NotBlank @Size(max = 255) String clientName,
    @Email @Size(max = 255) String contactEmail,
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$") String shopDomain,
    @Size(max = 64) String vertical,
    @NotBlank @Size(max = 64) String requestedTier,
    List<String> requestedSurfaces,
    List<String> knownIntegrations,
    @Size(max = 4000) String notes
) {
}
