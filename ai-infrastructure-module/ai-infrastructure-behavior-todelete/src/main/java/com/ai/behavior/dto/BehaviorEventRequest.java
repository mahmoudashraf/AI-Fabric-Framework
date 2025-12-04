package com.ai.behavior.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BehaviorEventRequest {

    @NotNull
    private UUID userId;

    @NotBlank
    private String eventType;

    @NotNull
    private JsonNode eventData;

    private String source;
}
