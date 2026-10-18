package com.ai.fabric.platform.backend.deployment.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PreservedExternalBindingKindsTest {
    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING"})
    void onlyExplicitExternalBindingKindsArePreserved(String resourceKind) {
        assertThat(PreservedExternalBindingKinds.isPreserved(resourceKind)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"APPLICATION", "EXTERNAL_OTHER_STORAGE_BINDING", "external_vector_storage_binding",
        "EXTERNAL_VECTOR_STORAGE_BINDING ", " EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING_V2"})
    void unknownOrNoncanonicalKindsAreNotPreserved(String resourceKind) {
        assertThat(PreservedExternalBindingKinds.isPreserved(resourceKind)).isFalse();
    }
}
