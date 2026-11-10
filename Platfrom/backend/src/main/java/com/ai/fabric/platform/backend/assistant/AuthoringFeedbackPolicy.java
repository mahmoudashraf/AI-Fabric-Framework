package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Explicit inference-spend bound; validation facts, not model prose, decide whether correction is useful. */
public record AuthoringFeedbackPolicy(boolean enabled, int maxCorrections) {
    public static final int MAX_CORRECTIONS = 2;

    static AuthoringFeedbackPolicy resolve(AuthoringFeedbackPolicy requested, AuthoringProperties properties) {
        if (requested == null || !requested.enabled()) return new AuthoringFeedbackPolicy(false, 0);
        int ceiling = Math.max(0, Math.min(MAX_CORRECTIONS, properties.getValidationFeedbackMaxCorrections()));
        if (!properties.isValidationFeedbackEnabled() || ceiling == 0)
            throw new ResponseStatusException(BAD_REQUEST, "Validation feedback is disabled on this environment.");
        if (requested.maxCorrections() < 1 || requested.maxCorrections() > ceiling)
            throw new ResponseStatusException(BAD_REQUEST, "Choose between one and " + ceiling + " AI corrections.");
        return requested;
    }

    int maxAttempts() { return 1 + (enabled ? maxCorrections : 0); }

    static boolean errors(JsonNode findings) {
        if (findings == null || !findings.isObject()) return false;
        if (findings.path("valid").isBoolean() && !findings.path("valid").asBoolean()) return true;
        if (findings.path("publishReady").isBoolean() && !findings.path("publishReady").asBoolean()) return true;
        if (findings.path("errorCount").isIntegralNumber() && findings.path("errorCount").asInt() > 0) return true;
        for (var issue : findings.path("issues")) if ("ERROR".equals(issue.path("severity").asText())) return true;
        return false;
    }

    static boolean checked(JsonNode findings) {
        return findings != null && (findings.path("valid").isBoolean() || findings.path("errorCount").isIntegralNumber()
            || findings.path("publishReady").isBoolean());
    }
}
