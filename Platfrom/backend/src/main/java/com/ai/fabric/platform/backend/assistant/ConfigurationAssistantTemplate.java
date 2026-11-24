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
            template.remove(java.util.List.of("templateVersion", "instructions"));
            template.put("enabled", true);
            for (var item : template.path("profiles")) {
                var profile = (ObjectNode) item;
                profile.put("instructions", shared + "\n\n" + profile.path("instructions").asText());
                profile.set("readActions", mapper.valueToTree(AuthoringNativeCapabilities.READS.stream().sorted().toList()));
                profile.set("writeActions", mapper.valueToTree("guide".equals(profile.path("id").asText())
                    ? java.util.List.of() : AuthoringNativeCapabilities.WRITES.stream().sorted().toList()));
            }
            return mapper.createObjectNode().put("type", "CONVERSATIONAL").set("workflowAssistance", template);
        } catch (java.io.IOException invalid) { throw new IllegalStateException("The configuration-assistant template is unavailable.", invalid); }
    }
}
