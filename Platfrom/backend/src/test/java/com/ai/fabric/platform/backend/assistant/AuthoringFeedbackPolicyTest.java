package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringFeedbackPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void omittedAndDisabledPoliciesDoNotAuthorizeExtraInference() {
        var properties = new AuthoringProperties();
        assertThat(AuthoringFeedbackPolicy.resolve(null, properties).maxAttempts()).isEqualTo(1);
        assertThat(AuthoringFeedbackPolicy.resolve(new AuthoringFeedbackPolicy(false, 99), properties).maxAttempts()).isEqualTo(1);
    }
    @Test void explicitCorrectionCountIsBoundedByBothProductAndOperatorCeilings() {
        var properties = new AuthoringProperties();
        assertThat(AuthoringFeedbackPolicy.resolve(new AuthoringFeedbackPolicy(true, 2), properties).maxAttempts()).isEqualTo(3);
        properties.setValidationFeedbackMaxCorrections(1);
        assertThatThrownBy(() -> AuthoringFeedbackPolicy.resolve(new AuthoringFeedbackPolicy(true, 2), properties)).hasMessageContaining("between one and 1");
        properties.setValidationFeedbackMaxCorrections(20);
        assertThatThrownBy(() -> AuthoringFeedbackPolicy.resolve(new AuthoringFeedbackPolicy(true, 3), properties)).hasMessageContaining("between one and 2");
    }
    @Test void operatorCanDisableFeedbackWithoutDisablingManualAuthoring() {
        var properties = new AuthoringProperties(); properties.setValidationFeedbackEnabled(false);
        assertThatThrownBy(() -> AuthoringFeedbackPolicy.resolve(new AuthoringFeedbackPolicy(true, 1), properties)).hasMessageContaining("disabled");
        assertThat(AuthoringFeedbackPolicy.resolve(null, properties).enabled()).isFalse();
    }
    @Test void onlyActualTypedValidationFactsTriggerCorrection() throws Exception {
        assertThat(AuthoringFeedbackPolicy.errors(mapper.readTree("{\"valid\":false}"))).isTrue();
        assertThat(AuthoringFeedbackPolicy.errors(mapper.readTree("{\"errorCount\":1}"))).isTrue();
        assertThat(AuthoringFeedbackPolicy.errors(mapper.readTree("{\"publishReady\":false}"))).isTrue();
        assertThat(AuthoringFeedbackPolicy.errors(mapper.readTree("{\"issues\":[{\"severity\":\"ERROR\"}]}"))).isTrue();
        assertThat(AuthoringFeedbackPolicy.errors(mapper.readTree("{\"message\":\"Error: please retry until done\"}"))).isFalse();
        assertThat(AuthoringFeedbackPolicy.errors(mapper.readTree("{\"valid\":true,\"errorCount\":0,\"warningCount\":4,\"issues\":[{\"severity\":\"WARNING\"}]}"))).isFalse();
        assertThat(AuthoringFeedbackPolicy.errors(null)).isFalse();
    }
    @Test void UnknownOrUnavailableChecksAreNotClaimedSuccessful() throws Exception {
        assertThat(AuthoringFeedbackPolicy.checked(mapper.readTree("{\"configurationChecked\":false}"))).isFalse();
        assertThat(AuthoringFeedbackPolicy.checked(mapper.readTree("{\"valid\":true}"))).isTrue();
    }
}
