package com.ai.fabric.runtime.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RuntimeAuthContextResponse {

    @Schema(description = "Verified subject identity resolved by runtime auth.")
    private String subjectId;

    @Schema(description = "Verified subject type resolved by runtime auth.")
    private String subjectType;

    @Schema(description = "Verified auth mode resolved by runtime auth.")
    private String authMode;

    @Schema(description = "Verified session identity resolved by runtime auth, when present.")
    private String sessionId;

    @Schema(description = "True when runtime served the request through legacy request-identity compatibility instead of verified auth context.")
    private boolean compatibilityIdentity;

    @Schema(description = "Compatibility or conflict warnings produced while resolving runtime auth context.")
    private List<String> warnings;
}
