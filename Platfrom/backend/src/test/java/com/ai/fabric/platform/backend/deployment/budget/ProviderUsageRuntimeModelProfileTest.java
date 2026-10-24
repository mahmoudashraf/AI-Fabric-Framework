package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProviderUsageRuntimeModelProfileTest {
    private static final String LEGACY = "openai-gpt4omini-embedding3small-20261018-v1";
    private static final String MINI = "openai-gpt4omini-gpt54mini-embedding3small-20261024-v2";
    private final ObjectMapper json = new ObjectMapper();
    private final ProviderUsageAllowanceCodec codec = new ProviderUsageAllowanceCodec(json);

    @Test void legacyProfileRemainsReviewedWithoutChangingItsModelCoverage() {
        var binding = binding("gpt-4o-mini", "text-embedding-3-small");
        assertThatCode(() -> ProviderUsageRuntimeProfile.require(binding, LEGACY)).doesNotThrowAnyException();
        assertThat(binding.modelIds()).containsExactly("gpt-4o-mini", "text-embedding-3-small");
        assertThatThrownBy(() -> ProviderUsageRuntimeProfile.require(binding("gpt-5.4-mini", "text-embedding-3-small"), LEGACY))
            .hasMessage("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
    }

    @Test void newCardAllowsOnlyReviewedMiniAliasesAndExistingModels() {
        var extended = binding("gpt-4o-mini", "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17", "text-embedding-3-small");
        assertThatCode(() -> ProviderUsageRuntimeProfile.require(extended, MINI)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ProviderUsageRuntimeProfile.require(extended, LEGACY))
            .hasMessage("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        assertThatThrownBy(() -> ProviderUsageRuntimeProfile.require(binding("gpt-4o-mini", "text-embedding-3-small"), MINI))
            .hasMessage("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        for (String model : List.of("gpt-5.4", "gpt-5.4-mini-2099-01-01", "text-embedding-3-large")) {
            assertThatThrownBy(() -> ProviderUsageRuntimeProfile.require(binding(model, "text-embedding-3-small"), MINI))
                .hasMessage("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        }
        assertThatThrownBy(() -> ProviderUsageRuntimeProfile.require(binding("gpt-5.4-mini", "text-embedding-3-small"), "unreviewed"))
            .hasMessage("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
    }

    @Test void allChatPurposesAcceptOnlyExactReviewedModelsAndKeepTheExistingCap() throws Exception {
        var config = json.createObjectNode().put("llmProvider", "openai").put("embeddingProvider", "openai")
            .put("openaiModel", "gpt-5.4-mini").put("openaiMaxTokens", 1000)
            .put("orchestrationModel", "gpt-4o-mini").put("generationModel", "gpt-5.4-mini-2026-03-17");
        assertThat(ProviderUsageRuntimeConfiguration.require(json.writeValueAsString(config), codec, MINI))
            .isEqualTo(new ProviderUsageRuntimeConfiguration.OutputCaps(1000, 1000, 1000));
        assertThatThrownBy(() -> ProviderUsageRuntimeConfiguration.require(codec.write(config), codec))
            .isInstanceOfSatisfying(ProviderUsageAllowanceFailure.class,
                error -> assertThat(error.getReason()).isEqualTo("PROVIDER_USAGE_RUNTIME_MODEL_NOT_REVIEWED"));
        assertThatThrownBy(() -> ProviderUsageRuntimeConfiguration.require(codec.write(config), codec, "unreviewed"))
            .isInstanceOf(ProviderUsageAllowanceFailure.class);
        for (String field : List.of("openaiModel", "orchestrationModel", "generationModel")) {
            var invalid = config.deepCopy().put(field, "gpt-5.4");
            assertThatThrownBy(() -> ProviderUsageRuntimeConfiguration.require(codec.write(invalid), codec, MINI))
                .isInstanceOfSatisfying(ProviderUsageAllowanceFailure.class,
                    error -> assertThat(error.getReason()).isEqualTo("PROVIDER_USAGE_RUNTIME_MODEL_NOT_REVIEWED"));
        }
        config.put("openaiMaxTokens", 16_385);
        assertThatThrownBy(() -> ProviderUsageRuntimeConfiguration.require(codec.write(config), codec, MINI))
            .isInstanceOfSatisfying(ProviderUsageAllowanceFailure.class,
                error -> assertThat(error.getReason()).isEqualTo("PROVIDER_USAGE_RUNTIME_OUTPUT_CAP_INVALID"));
        config.put("openaiMaxTokens", 1000).put("enableFallback", true);
        assertThatThrownBy(() -> ProviderUsageRuntimeConfiguration.require(codec.write(config), codec, MINI))
            .isInstanceOf(ProviderUsageAllowanceFailure.class);
    }

    private static ProviderUsageAllowanceContracts.ProviderBinding binding(String... models) {
        return new ProviderUsageAllowanceContracts.ProviderBinding("openai", List.of(models), "llm", "managed-secret");
    }
}
