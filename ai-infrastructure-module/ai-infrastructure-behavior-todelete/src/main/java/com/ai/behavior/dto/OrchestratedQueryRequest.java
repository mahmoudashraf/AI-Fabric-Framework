package com.ai.behavior.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrchestratedQueryRequest {

    @NotBlank
    private String query;

    @Min(1)
    @Max(500)
    private int limit = 25;

    private boolean includeExplanation;

    private String userId;
}
