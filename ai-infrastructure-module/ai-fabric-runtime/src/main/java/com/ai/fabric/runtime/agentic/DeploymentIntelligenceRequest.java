package com.ai.fabric.runtime.agentic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeploymentIntelligenceRequest(
    @NotBlank
    @Size(max = 2000)
    String question
) {
}
