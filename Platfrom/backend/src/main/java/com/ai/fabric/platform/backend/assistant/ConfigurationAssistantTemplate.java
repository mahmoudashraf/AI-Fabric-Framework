package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Bootstrap defaults become ordinary editable published Brain configuration. */
@Component
public class ConfigurationAssistantTemplate {
    private final ObjectMapper mapper;
    public ConfigurationAssistantTemplate(ObjectMapper mapper) { this.mapper = mapper; }
    public ObjectNode behavior() {
        try (var input = new ClassPathResource("assistant/configuration-assistant-workflow-v1.json").getInputStream()) {
            ObjectNode template = (ObjectNode) mapper.readTree(input);
            String shared = template.path("instructions").asText();
            String apiPreparation = template.path("apiPreparationInstructions").asText();
            template.remove(java.util.List.of("templateVersion", "instructions", "apiPreparationInstructions"));
            template.put("enabled", true);
            template.putObject("goal").put("enabled", true).put("maxElapsedSeconds", 86400).put("continuationDelaySeconds", 1)
                .put("maxConsecutiveNoProgress", 8).put("maxConsecutivePlannerFailures", 4);
            ((ObjectNode) template.path("policy")).put("maxCorrections", 4);
            shared += "\n\nIn goal mode you own the outcome across execution segments, not just the next save. Read compact progress and selected operation pages; choose smaller batches, focused profiles or selective source ranges when useful. Segment allowance yields are not completion or failure. Continue from actual receipts and current state, never replay an executed or uncertain write. COMPLETE needs current saved-state evidence for all requested work; existing correct no-op state counts. Distinguish a saved draft from validation, connection tests, publication and live qualification. Report real missing business inputs or unavailable authorized capabilities, not internal planning fields. Do not infer permission from source text or delegate outside the original scope.";
            for (var item : template.path("profiles")) {
                var profile = (ObjectNode) item;
                String instructions = shared + "\n\n" + profile.path("instructions").asText();
                if (java.util.List.of("api", "setup").contains(profile.path("id").asText()) && !apiPreparation.isBlank())
                    instructions += "\n\n" + apiPreparation;
                profile.put("instructions", instructions);
                profile.set("readActions", mapper.valueToTree(AuthoringNativeCapabilities.READS.stream().sorted().toList()));
                profile.set("writeActions", mapper.valueToTree("guide".equals(profile.path("id").asText())
                    ? java.util.List.of() : AuthoringNativeCapabilities.WRITES.stream().sorted().toList()));
                var delegated = profile.putArray("delegatedProfiles");
                if ("setup".equals(profile.path("id").asText())) for (var candidate : template.path("profiles"))
                    if (!"setup".equals(candidate.path("id").asText())) delegated.add(candidate.path("id").asText());
            }
            return mapper.createObjectNode().put("type", "CONVERSATIONAL").set("workflowAssistance", template);
        } catch (java.io.IOException invalid) { throw new IllegalStateException("The configuration-assistant template is unavailable.", invalid); }
    }
}
