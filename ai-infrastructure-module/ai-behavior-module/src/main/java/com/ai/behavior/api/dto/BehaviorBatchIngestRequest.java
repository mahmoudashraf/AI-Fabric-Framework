package com.ai.behavior.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BehaviorBatchIngestRequest {

    @NotEmpty
    private List<@Valid BehaviorEventRequest> events;
}
