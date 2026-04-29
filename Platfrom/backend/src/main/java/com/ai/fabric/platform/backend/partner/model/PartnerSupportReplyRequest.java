package com.ai.fabric.platform.backend.partner.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PartnerSupportReplyRequest(
    @NotBlank @Size(max = 8000) String bodyMarkdown,
    List<String> evidenceBundleIds
) {
}
