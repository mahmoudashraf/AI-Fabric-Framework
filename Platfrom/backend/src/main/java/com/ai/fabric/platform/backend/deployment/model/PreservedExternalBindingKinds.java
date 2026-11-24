package com.ai.fabric.platform.backend.deployment.model;

/** Exact external resource bindings not owned by deployment deletion or capacity operations. */
public final class PreservedExternalBindingKinds {
    public static final String DOCUMENT_STORAGE = "EXTERNAL_DOCUMENT_STORAGE_BINDING";
    public static final String VECTOR_STORAGE = "EXTERNAL_VECTOR_STORAGE_BINDING";
    public static final String AI_STATE_STORAGE = "EXTERNAL_AI_STATE_STORAGE_BINDING";
    public static final String MANAGED_ASSISTANT = "EXISTING_ASSISTANT_SERVICE_BINDING";

    private PreservedExternalBindingKinds() { }

    public static boolean isPreserved(String resourceKind) {
        return DOCUMENT_STORAGE.equals(resourceKind) || VECTOR_STORAGE.equals(resourceKind)
            || AI_STATE_STORAGE.equals(resourceKind) || MANAGED_ASSISTANT.equals(resourceKind);
    }
}
