package com.ai.infrastructure.config;

import com.ai.infrastructure.intent.orchestration.policy.OrchestrationProfile;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for orchestration behavior.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.orchestration")
public class OrchestrationProperties {

    /**
     * Optimization profile that provides default orchestration behavior.
     */
    private OrchestrationProfile profile = OrchestrationProfile.DEFAULT;

    /**
     * Explicit override for how INFORMATION intents are handled.
     *
     * <p>When unset (null), the effective value is derived from {@link #profile} and/or mode overrides.</p>
     */
    private InformationMode informationMode;

    /**
     * Server-defined orchestration modes (coherent bundles) that can override profile defaults.
     *
     * <p>Clients may request a mode, but the server only accepts allowlisted modes defined here.</p>
     */
    private Map<String, ModeOverrides> modes = new LinkedHashMap<>();

    /**
     * Configuration for RAG request composition.
     */
    private RagProperties rag = new RagProperties();

    /**
     * Server-side routing from a UI position signal to an orchestration mode.
     *
     * <p>Used to allow the client to send a low-spoof-risk position (e.g. "cart") while the server selects the mode.</p>
     */
    private Map<String, String> positionRouting = new LinkedHashMap<>();

    /**
     * If true, unknown positions terminate the request with an error.
     *
     * <p>When false (default), unknown positions are ignored and the profile/mode fallback applies.</p>
     */
    private boolean strictPositionRouting = false;

    /**
     * If true, unknown requested modes terminate the request with an error.
     *
     * <p>When false (default), unknown modes are ignored and the profile fallback applies.</p>
     */
    private boolean strictModeRouting = false;

    /**
     * How INFORMATION intents are handled.
     */
    public enum InformationMode {
        /**
         * Current behavior: intent extraction controls requiresRetrieval/requiresGeneration and vectorSpace routing.
         */
        LLM_DRIVEN,

        /**
         * Deterministic behavior: INFORMATION always performs retrieval (RAG) and generation.
         * Missing vectorSpace triggers fan-out across all known vector spaces (when available).
         */
        DETERMINISTIC_RAG_GENERATE
    }

    /**
     * Mode-level overrides for orchestration behavior.
     */
    @Data
    public static class ModeOverrides {
        /**
         * Optional mode override for information orchestration behavior.
         */
        private InformationMode informationMode;

        /**
         * Optional mode override to force Advanced RAG on/off.
         *
         * <p>When true, Advanced RAG is enabled for the request (when a provider is present).
         * When false, Advanced RAG is disabled even if the LLM suggests it.</p>
         */
        private Boolean useAdvancedRag;

        /**
         * Optional mode override for whether pinned-target hints may be appended to the embedding query.
         *
         * <p>When unset, the global {@code ai.orchestration.rag.target-hint.enabled} value applies.</p>
         */
        private Boolean ragTargetHintEnabled;
    }

    @Data
    public static class RagProperties {
        /**
         * When enabled, the orchestrator may expand the embedding query using pinned target evidence.
         */
        private TargetHintProperties targetHint = new TargetHintProperties();
    }

    @Data
    public static class TargetHintProperties {
        /**
         * Master switch for embedding query target hints.
         */
        private boolean enabled = false;

        /**
         * Max number of targets to include in the hint.
         */
        private int maxTargets = 3;

        /**
         * Max total chars for the appended hint (excluding the base query).
         */
        private int maxChars = 500;

        /**
         * Include the target vectorSpace in the hint when present.
         */
        private boolean includeVectorSpace = true;

        /**
         * Include the target id in the hint when present.
         */
        private boolean includeId = true;

        /**
         * Include a bounded snippet of target contentText when present.
         */
        private boolean includeContentText = true;

        /**
         * Max chars of contentText included per target.
         */
        private int maxContentTextCharsPerTarget = 120;

        /**
         * Allowlisted metadata keys to include in the hint.
         *
         * <p>When empty, arbitrary metadata keys are not appended (safe-by-default).</p>
         */
        private List<String> metadataKeysAllowlist = new ArrayList<>();
    }
}
