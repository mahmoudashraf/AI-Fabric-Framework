package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;

/** Strictness is scoped to this financial command, not the application's shared mapper or parser. */
public final class ProviderUsageVoidUnusedCommandDeserializer extends JsonDeserializer<VoidUnusedCommand> {
    private static final ObjectReader INPUT = new ObjectMapper().readerFor(JsonNode.class)
        .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private static final Set<String> COMMAND_FIELDS = Set.of("operationKey", "expectedGrantFingerprint", "expectedAllocationCount",
        "expectedAllocated", "reason", "ownerApprovalEvidenceRef");
    private static final Set<String> ZERO_FIELDS = Set.of("chatAttempts", "totalTokens", "costNanoUsd");

    @Override public VoidUnusedCommand deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode command = object(INPUT.readValue(parser), COMMAND_FIELDS, context);
        JsonNode allocated = object(command.get("expectedAllocated"), ZERO_FIELDS, context);
        return new VoidUnusedCommand(string(command.get("operationKey"), context), string(command.get("expectedGrantFingerprint"), context),
            zero(command.get("expectedAllocationCount"), context),
            new ZeroAllocatedCredits(zero(allocated.get("chatAttempts"), context), zero(allocated.get("totalTokens"), context),
                zero(allocated.get("costNanoUsd"), context)),
            string(command.get("reason"), context), string(command.get("ownerApprovalEvidenceRef"), context));
    }

    private JsonNode object(JsonNode node, Set<String> fields, DeserializationContext context) throws IOException {
        if (node == null || !node.isObject() || node.size() != fields.size())
            return context.reportInputMismatch(VoidUnusedCommand.class, "All command fields must be explicit; unknown fields are forbidden");
        var names = node.fieldNames();
        while (names.hasNext()) if (!fields.contains(names.next()))
            return context.reportInputMismatch(VoidUnusedCommand.class, "Unknown command field");
        return node;
    }

    private String string(JsonNode node, DeserializationContext context) throws IOException {
        if (!node.isTextual()) return context.reportInputMismatch(String.class, "An explicit string is required");
        return node.textValue();
    }

    private Long zero(JsonNode node, DeserializationContext context) throws IOException {
        try (var value = node.traverse()) {
            value.nextToken();
            return new ExactZero().deserialize(value, context);
        }
    }
}
