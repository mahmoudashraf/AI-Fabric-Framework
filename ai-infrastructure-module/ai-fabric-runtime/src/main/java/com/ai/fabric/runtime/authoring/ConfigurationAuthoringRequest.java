package com.ai.fabric.runtime.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConfigurationAuthoringRequest(
    @NotNull @Pattern(regexp = "LOOMAI_CONFIGURATION_AUTHORING_V1") String contractVersion,
    @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+") String sessionId,
    @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+") String turnId,
    @NotNull ResourceKind resourceKind,
    @NotNull Task task,
    @NotBlank @Size(max = 8_000) String query,
    @NotNull JsonNode currentConfiguration,
    @NotNull @Size(max = 16) List<@NotNull @Valid Source> sources,
    @NotNull JsonNode catalogue,
    @NotNull @Size(max = 24) List<@NotNull @Valid PriorTurn> priorTurns
) {
    public static final String CONTRACT_VERSION = "LOOMAI_CONFIGURATION_AUTHORING_V1";
    public enum ResourceKind { DEPLOYMENT_DRAFT, PRIVATE_DATA_PLUGIN, ENGAGE_APP }
    public enum Task { ACTIONS, CONNECTIONS, DATA_PLUGIN, KNOWLEDGE, BEHAVIOUR, MODELS, ENGAGE, HUMAN_SUPPORT, SPECIALISTS, KIT }
    public enum Role { USER, ASSISTANT }
    public record Source(@NotBlank @Size(max = 128) String id,
                         @NotBlank @Size(max = 512) String title,
                         @NotBlank @Size(max = 32_000) String content) { }
    public record PriorTurn(@NotNull Role role, @NotBlank @Size(max = 8_000) String content) { }

    public String correlation() { return "authoring:" + sessionId + ":" + turnId; }
}
