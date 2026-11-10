package com.ai.fabric.runtime.authoring;

import static org.assertj.core.api.Assertions.*;

import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.ai.fabric.runtime.budget.RuntimeBudgetAllocation;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigurationAuthoringAllocationPayloadTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void initializePrintsOnlyTheExistingContractAndExactExistingFingerprint() throws Exception {
        var approval = new Approval("existing-prepaid-approval", new Scope("loomai-internal", "authoring-staging", "authoring-staging",
            "staging", ReviewedOpenAiRateCard.credentialIdentity("offline-fixture-key"), ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE),
            new Limits(20, 1_000_000, 5_000_000_000L), Instant.parse("2099-01-01T00:00:00Z"));
        var input = mapper.writeValueAsString(approval);
        var output = mapper.readTree(print("initialize", input));
        assertThat(mapper.treeToValue(output, RuntimeBudgetAllocation.class))
            .isEqualTo(new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval)));
        assertThat(output.path("approval").path("expiresAt").asText()).isEqualTo("2099-01-01T00:00:00Z");
        assertThat(output.size()).isEqualTo(2);
    }

    @Test void activateUsesOnlyExactReturnedStoreIdentity() throws Exception {
        var receipt = new RuntimeBudgetBindingService.StoreReceipt(RuntimeBudgetBindingService.State.INITIALIZED,
            UUID.randomUUID(), "existing-approval", "a".repeat(64));
        var output = mapper.readTree(print("activate", mapper.writeValueAsString(receipt)));
        assertThat(mapper.treeToValue(output, RuntimeBudgetBindingService.Activation.class))
            .isEqualTo(new RuntimeBudgetBindingService.Activation(receipt.approvalId(), receipt.storeUuid(), receipt.runtimeFingerprint()));
        assertThat(output.size()).isEqualTo(3);
    }

    @Test void neverRepairsInventsOrAcceptsWrappedAndTrailingInput() {
        for (String input : new String[]{"{}", "prose {}", "{} {}", " ".repeat(65_537)}) {
            assertThatThrownBy(() -> print("initialize", input)).isInstanceOf(Exception.class);
        }
        assertThatThrownBy(() -> print("fund", "{}")).isInstanceOf(IllegalArgumentException.class);
    }

    private String print(String operation, String input) throws Exception {
        var output = new ByteArrayOutputStream();
        ConfigurationAuthoringAllocationPayload.writePayload(operation,
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);
        return output.toString(StandardCharsets.UTF_8);
    }
}
