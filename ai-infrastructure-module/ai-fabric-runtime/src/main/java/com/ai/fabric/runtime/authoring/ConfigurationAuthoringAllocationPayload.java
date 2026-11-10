package com.ai.fabric.runtime.authoring;

import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.runtime.budget.RuntimeBudgetAllocation;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Pure operator serialization of existing contracts; no Spring boot, approvals, ledger or HTTP. */
public final class ConfigurationAuthoringAllocationPayload {
    private ConfigurationAuthoringAllocationPayload() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: initialize|activate; read existing approval or initialize receipt JSON from stdin.");
        writePayload(args[0], System.in, System.out);
    }

    public static void writePayload(String operation, InputStream input, OutputStream output) throws IOException {
        var mapper = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        byte[] bytes = input.readNBytes(65_537);
        if (bytes.length > 65_536) throw new IllegalArgumentException("Existing allocation payload exceeds 65536 bytes.");
        Object payload;
        switch (operation) {
            case "initialize" -> {
                Approval approval = mapper.readValue(bytes, Approval.class);
                payload = new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval));
            }
            case "activate" -> {
                var receipt = mapper.readValue(bytes, RuntimeBudgetBindingService.StoreReceipt.class);
                payload = new RuntimeBudgetBindingService.Activation(receipt.approvalId(), receipt.storeUuid(), receipt.runtimeFingerprint());
            }
            default -> throw new IllegalArgumentException("Operation must be initialize or activate.");
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output, payload);
    }
}
