package com.ai.infrastructure.intent.orchestration;

/**
 * Centralized keys for {@link OrchestrationContext#getMetadata()}.
 *
 * <p>These keys are used internally by the orchestrator and/or by applications when constructing
 * an {@link OrchestrationContext}. Avoid scattering string literals across the codebase.</p>
 */
public final class OrchestrationContextMetadataKeys {

    private OrchestrationContextMetadataKeys() {
    }

    /**
     * When true, Advanced RAG is explicitly enabled for the request (when a provider is present).
     */
    public static final String USE_ADVANCED_RAG = "useAdvancedRAG";

    /**
     * Request-scoped prompt preview overlay used for operator-only prompt testing.
     */
    public static final String PROMPT_PREVIEW = "promptPreview";

    /**
     * Deployment-managed override for the minimum RAG similarity threshold.
     */
    public static final String RAG_SIMILARITY_THRESHOLD = "ragSimilarityThreshold";

    /**
     * Deployment-managed override for smart suggestions enablement.
     */
    public static final String SMART_SUGGESTIONS_ENABLED = "smartSuggestionsEnabled";

    /**
     * Verified subject identifier derived from runtime auth context.
     */
    public static final String SUBJECT_ID = "subjectId";

    /**
     * Verified subject type derived from runtime auth context.
     */
    public static final String SUBJECT_TYPE = "subjectType";

    /**
     * Verified runtime auth mode.
     */
    public static final String AUTH_MODE = "authMode";

    /**
     * Verified caller type for the current request.
     */
    public static final String CALLER_TYPE = "callerType";

    /**
     * Auth context issuer.
     */
    public static final String AUTH_ISSUER = "authIssuer";

    /**
     * Auth context audiences.
     */
    public static final String AUTH_AUDIENCES = "authAudiences";

    /**
     * Auth context expiry timestamp.
     */
    public static final String AUTH_EXPIRES_AT = "authExpiresAt";

    /**
     * Deployment identifier bound to the verified auth context.
     */
    public static final String DEPLOYMENT_ID = "deploymentId";

    /**
     * Customer identifier bound to the verified auth context.
     */
    public static final String CUSTOMER_ID = "customerId";

    /**
     * Tenant identifier bound to the verified auth context.
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * Granted scopes from the verified auth context.
     */
    public static final String GRANTED_SCOPES = "grantedScopes";

    /**
     * Scopes explicitly requested by the current runtime entrypoint.
     */
    public static final String REQUESTED_SCOPES = "requestedScopes";
}
