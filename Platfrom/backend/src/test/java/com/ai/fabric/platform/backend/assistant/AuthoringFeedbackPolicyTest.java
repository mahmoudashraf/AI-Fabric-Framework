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

    @Test void unchangedPublicationPrerequisiteDoesNotRepairAnUnrelatedSpecialistEdit() throws Exception {
        var baseline = prerequisite(); var before = mapper.readTree("{\"behaviorConfig\":{\"marketplaceRequirements\":{\"unresolvedRequiredPluginRefs\":[\"documents@1\"]}}}");
        assertThat(AuthoringFeedbackPolicy.requiresCorrection(baseline, baseline, "SPECIALISTS", before, before)).isFalse();
        assertThat(AuthoringFeedbackPolicy.errors(baseline)).isTrue();
        assertThat(baseline.path("publishReady").asBoolean()).isFalse();
        assertThat(AuthoringFeedbackPolicy.requiresCorrection(baseline, baseline, "ACTIONS", before, before)).isTrue();
        assertThat(AuthoringFeedbackPolicy.requiresCorrection(baseline, null, "SPECIALISTS", before, before)).isTrue();
    }

    @Test void newOrChangedPrerequisitesAndUnreportedErrorsStillRequireCorrection() throws Exception {
        var baseline = prerequisite(); var config = mapper.createObjectNode();
        var changed = baseline.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) changed.at("/issues/0")).put("message", "A different plugin now needs configuration.");
        assertThat(AuthoringFeedbackPolicy.requiresCorrection(changed, baseline, "SPECIALISTS", config, config)).isTrue();
        var extra = baseline.deepCopy(); extra.put("errorCount", 2);
        assertThat(AuthoringFeedbackPolicy.requiresCorrection(extra, baseline, "SPECIALISTS", config, config)).isTrue();
        var after = mapper.readTree("{\"behaviorConfig\":{\"marketplaceRequirements\":{\"changed\":true}}}");
        assertThat(AuthoringFeedbackPolicy.requiresCorrection(baseline, baseline, "SPECIALISTS", config, after)).isTrue();
    }

    @Test void authorityAndExecutionErrorsAreNeverExcludedEvenWhenPreExisting() throws Exception {
        for (String code : new String[] {"OWNERSHIP_DENIED", "SPECIALIST_AUTHORITY_INVALID", "CONFIGURATION_CHECK_REJECTED", "REQUIRED_ACTION_INPUT_MISSING"}) {
            var findings = prerequisite(); ((com.fasterxml.jackson.databind.node.ObjectNode) findings.at("/issues/0")).put("code", code);
            var config = mapper.createObjectNode();
            assertThat(AuthoringFeedbackPolicy.requiresCorrection(findings, findings, "SPECIALISTS", config, config)).isTrue();
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode prerequisite() throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("""
            {"publishReady":false,"errorCount":1,"issues":[{"severity":"ERROR","section":"behavior",
              "code":"MARKETPLACE_REQUIRED_PLUGIN_CONFIGURATION_REQUIRED","path":"$.behaviorConfig.marketplaceRequirements",
              "message":"Configure required documents before publication."}]}
            """);
    }
}
