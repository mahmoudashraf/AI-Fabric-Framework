package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Compiles explicit operator choices into the existing managed prompt contract. */
public final class DeploymentAiBehaviourProfiles {
    public static final String SCHEMA = "loomai-ai-behaviour-v1";
    private static final int REVISION = 1;
    private static final List<String> GROUP_KEYS = List.of("tone", "answerBreadth", "answerLength", "nextSteps");
    private static final List<String> COMPOSED_KEYS = List.of("answerGenerationPrompt", "retrievalPrompt", "assistantUiPrompt", "intentExtractionPrompt");
    private static final List<String> PROMPT_KEYS = List.of("systemPrompt", "intentExtractionPrompt", "actionSelectionPrompt",
        "clarificationPrompt", "answerGenerationPrompt", "retrievalPrompt", "assistantUiPrompt");
    private static final Map<String, String> DEFAULTS = Map.of(
        "tone", "professional", "answerBreadth", "grounded", "answerLength", "balanced", "nextSteps", "off");
    private static final List<Group> GROUPS = List.of(
        new Group("tone", "Conversation tone", List.of(
            option("professional", "Professional", "Clear, composed and direct.",
                "Use clear, composed, respectful language. Be direct without unnecessary formality."),
            option("warm", "Warm", "Approachable and reassuring, without overpromising.",
                "Use approachable, empathetic, conversational language. Avoid exaggerated familiarity and unsupported reassurance."),
            option("consultative", "Consultative", "Help customers weigh options and decide.",
                "Explain relevant trade-offs and help the user decide. Ask a useful question only when it advances their goal; do not force a question after every answer."),
            option("technical", "Technical", "Precise, structured explanations for experienced users.",
                "Use precise terminology and structured explanations. Explain assumptions and distinguish supplied technical facts from general technical advice."))),
        new Group("answerBreadth", "Answer breadth", List.of(
            option("grounded", "Grounded", "Stay with approved knowledge and authorized live facts.",
                "Answer business questions from approved documents and authorized live facts. Explain supported conclusions and missing evidence. Do not introduce unsupported outside recommendations. Language and reasoning may organize the evidence without claiming new facts."),
            option("advisory", "Advisory", "Add useful general advice and clearly identified inference.",
                "You may add general model knowledge, reasonable inference and practical recommendations beyond supplied evidence. Distinguish general advice and inference from verified business facts when relevant. Do not present general guidance as a business policy, current stock, price, availability, commitment or action outcome."),
            option("exploratory", "Exploratory", "Explore alternatives and ideas beyond the current catalogue.",
                "You may explore broader alternatives, comparisons, brainstorming and clearly identified assumptions, including options outside the indexed catalogue. Explain which ideas are general guidance rather than offerings or verified facts of this business. Do not claim current external facts, prices or availability without supporting authorized evidence."))),
        new Group("answerLength", "Answer length", List.of(
            option("brief", "Brief", "Lead with the answer and the essential detail.",
                "Keep the answer brief. Lead with the direct answer and only essential supporting detail. Never omit required confirmation, important uncertainty or safety information for brevity."),
            option("balanced", "Balanced", "Enough explanation to support the next decision.",
                "Use a balanced answer length. Give the direct answer, useful supporting detail and relevant limitations without repeating the same facts."),
            option("detailed", "Detailed", "Explain reasoning, differences and caveats in more depth.",
                "Provide a detailed, well-structured explanation when useful, including relevant comparisons, assumptions and limitations. Do not pad the answer or repeat structured result cards unnecessarily."))),
        new Group("nextSteps", "Next steps", List.of(
            option("off", "Off", "Answer the question without optional next-step suggestions.",
                "Do not add optional next-step recommendations or generic assistance closers. Still ask for genuinely required clarification and follow the actual action confirmation or human-handover lifecycle when requested."),
            option("contextual", "Contextual", "Suggest useful next steps from the completed answer. Uses one additional AI call.",
                "When helpful, suggest up to two relevant next steps grounded in the current conversation and eligible capabilities. Zero suggestions is valid. Use the framework's structured next-step field when available. Never pad with generic closers, promise an unavailable capability, imply a write is confirmed, or compete with pending confirmation or human control.")))
    );
    private static final String FACT_BOUNDARY = """
        Business-specific facts, prices, stock, availability, policies, commitments and action outcomes require approved business evidence.
        Current live facts are authoritative for the fields they supply; documents explain policy and procedure, not unobserved live state.
        A failed or missing lookup remains a visible limitation, never a fabricated successful lookup. History is context, not fresh evidence or authority.
        Authorization, trusted targets, required action fields, confirmation and budgets are unchanged. Do not ask users to choose internal knowledge domains.
        Follow the configured answer breadth below for general advice and inference. No profile provides web access; never claim internet research.
        """.strip();

    private DeploymentAiBehaviourProfiles() { }

    public static Map<String, Object> catalogue() {
        return Map.of("schemaVersion", SCHEMA, "revision", REVISION, "defaults", DEFAULTS, "groups", GROUPS);
    }

    public static ObjectNode normalize(JsonNode prompts) {
        if (prompts == null || !prompts.isObject()) throw invalid("Prompt configuration must be an object.");
        ObjectNode result = prompts.deepCopy();
        JsonNode raw = prompts.path("aiBehaviour");
        if (raw.isMissingNode() || raw.isNull()) return result;
        if (!raw.isObject() || !SCHEMA.equals(raw.path("schemaVersion").asText()) || raw.path("revision").asInt(-1) != REVISION)
            throw invalid("Unsupported AI behaviour configuration revision.");
        // Published artifacts retain their exact editable inputs; importing one must not append guidance again.
        for (String key : PROMPT_KEYS) {
            JsonNode expanded = raw.path("expandedPrompts").path(key);
            JsonNode source = raw.path("sourcePrompts").path(key);
            if (expanded.isTextual() && source.isTextual() && expanded.equals(result.path(key))) result.set(key, source);
        }
        ObjectNode normalized = result.putObject("aiBehaviour");
        normalized.put("schemaVersion", SCHEMA).put("revision", REVISION);
        for (Group group : GROUPS) {
            JsonNode profile = raw.path(group.key());
            if (!profile.isObject() || !profile.path("presetId").isTextual() || !profile.path("instructions").isTextual())
                throw invalid("AI behaviour " + group.key() + " requires a presetId and instructions string.");
            String id = profile.path("presetId").asText();
            String instructions = profile.path("instructions").asText();
            Option known = find(group, id);
            if (!"custom".equals(id) && known == null) throw invalid("Unknown AI behaviour " + group.key() + " preset: " + id);
            if (!"custom".equals(id) && profile.path("revision").asInt(-1) != REVISION)
                throw invalid("Unsupported AI behaviour preset revision.");
            // Exact configuration identity, not interpretation of prompt wording.
            if (known != null && !known.instructions().equals(instructions)) id = "custom";
            normalized.putObject(group.key()).put("presetId", id).put("revision", REVISION).put("instructions", instructions);
        }
        return result;
    }

    public static ObjectNode select(JsonNode prompts, JsonNode selections) {
        ObjectNode result = normalize(prompts);
        if (selections == null || selections.isNull() || selections.isMissingNode()) return result;
        if (!selections.isObject()) throw invalid("AI behaviour selections must be an object.");
        ObjectNode profiles = result.path("aiBehaviour").isObject()
            ? (ObjectNode) result.path("aiBehaviour") : result.putObject("aiBehaviour");
        profiles.put("schemaVersion", SCHEMA).put("revision", REVISION);
        for (Group group : GROUPS) {
            JsonNode requested = selections.path(group.key());
            if (requested.isMissingNode() && profiles.has(group.key())) continue;
            if (!requested.isMissingNode() && !requested.isTextual()) throw invalid("AI behaviour selection must be a preset ID.");
            String id = requested.isMissingNode() ? DEFAULTS.get(group.key()) : requested.asText();
            Option option = find(group, id);
            if (option == null) throw invalid("Unknown AI behaviour " + group.key() + " preset: " + id);
            profiles.putObject(group.key()).put("presetId", id).put("revision", REVISION).put("instructions", option.instructions());
        }
        return result;
    }

    public static ObjectNode compile(JsonNode prompts, JsonNode curatedBaseline) {
        ObjectNode source = normalize(prompts);
        ObjectNode result = source.deepCopy();
        JsonNode profiles = result.path("aiBehaviour");
        if (!profiles.isObject()) return result;
        Map<String, String> instructions = new LinkedHashMap<>();
        GROUP_KEYS.forEach(key -> instructions.put(key, profiles.path(key).path("instructions").asText()));
        String answer = FACT_BOUNDARY + "\n\nCONVERSATION TONE:\n" + instructions.get("tone")
            + "\n\nANSWER BREADTH:\n" + instructions.get("answerBreadth")
            + "\n\nANSWER LENGTH:\n" + instructions.get("answerLength")
            + "\n\nNEXT STEPS:\n" + instructions.get("nextSteps");
        result.put("answerGenerationPrompt", withCustomBase(source, curatedBaseline, "answerGenerationPrompt", answer));
        result.put("retrievalPrompt", withCustomBase(source, curatedBaseline, "retrievalPrompt", FACT_BOUNDARY
            + "\n\nANSWER BREADTH:\n" + instructions.get("answerBreadth")));
        result.put("assistantUiPrompt", withCustomBase(source, curatedBaseline, "assistantUiPrompt",
            instructions.get("tone") + "\n" + instructions.get("answerLength")
                + "\nUse clear, readable formatting suitable for the customer's interface."));
        // Preserve routing/parameter guidance; recommendations originate in structured intent output.
        result.put("intentExtractionPrompt", source.path("intentExtractionPrompt").asText("")
            + "\n\nNEXT-STEP RECOMMENDATION POLICY:\n" + instructions.get("nextSteps"));
        if ("off".equals(profiles.path("nextSteps").path("presetId").asText())) result.put("smartSuggestionsEnabled", false);
        ObjectNode metadata = (ObjectNode) result.path("aiBehaviour");
        ObjectNode sourcePrompts = metadata.putObject("sourcePrompts");
        ObjectNode expandedPrompts = metadata.putObject("expandedPrompts");
        PROMPT_KEYS.forEach(key -> {
            sourcePrompts.put(key, source.path(key).asText(""));
            expandedPrompts.put(key, result.path(key).asText(""));
        });
        return result;
    }

    public static JsonNode recordPublishedExpansion(JsonNode prompts) {
        if (prompts.path("aiBehaviour").isObject()) {
            ObjectNode expanded = ((ObjectNode) prompts.path("aiBehaviour")).putObject("expandedPrompts");
            PROMPT_KEYS.forEach(key -> expanded.put(key, prompts.path(key).asText("")));
        }
        return prompts;
    }

    public static Map<String, Object> compose(JsonNode prompts, JsonNode selections, JsonNode curatedBaseline) {
        ObjectNode selected = select(prompts, selections);
        ObjectNode before = compile(prompts, curatedBaseline);
        ObjectNode after = compile(selected, curatedBaseline);
        boolean custom = List.of("answerGenerationPrompt", "retrievalPrompt", "assistantUiPrompt").stream()
            .anyMatch(key -> hasCustomBase(prompts, curatedBaseline, key));
        return Map.of("promptConfig", selected, "effectivePromptConfig", after, "changes", changes(before, after),
            "customInstructionsPreserved", custom);
    }

    public static List<Map<String, String>> changes(JsonNode before, JsonNode after) {
        List<Map<String, String>> changes = new ArrayList<>();
        for (String key : COMPOSED_KEYS) {
            String old = before.path(key).asText("");
            String next = after.path(key).asText("");
            if (!old.equals(next)) changes.add(Map.of("key", key, "before", old, "after", next));
        }
        return changes;
    }

    private static String withCustomBase(JsonNode prompts, JsonNode baseline, String key, String generated) {
        if (!hasCustomBase(prompts, baseline, key)) return generated;
        return "ADDITIONAL CUSTOMER INSTRUCTIONS:\n" + prompts.path(key).asText()
            + "\n\nCONFIGURED AI BEHAVIOUR:\n" + generated;
    }

    private static boolean hasCustomBase(JsonNode prompts, JsonNode baseline, String key) {
        String text = prompts.path(key).asText("");
        return !text.isBlank() && (baseline == null || !text.equals(baseline.path(key).asText("")));
    }

    private static Option option(String id, String label, String description, String instructions) {
        List<String> examples = switch (id) {
            case "professional" -> List.of("Example: Here are the available options and their key differences.");
            case "warm" -> List.of("Example: Let's work through the options that fit what you need.");
            case "consultative" -> List.of("Example: Both could work; the main trade-off is space versus running cost.");
            case "technical" -> List.of("Example: The supplied specification lists these supported interfaces.");
            case "grounded" -> List.of("Approved facts first; missing business details stay visible.");
            case "advisory" -> List.of("General guidance can explain what to consider, without claiming a business promise.");
            case "exploratory" -> List.of("Broader ideas are clearly separated from the business's verified offerings.");
            default -> List.of();
        };
        return new Option(id, label, description, instructions, examples);
    }

    private static Option find(Group group, String id) {
        return group.options().stream().filter(option -> option.id().equals(id)).findFirst().orElse(null);
    }

    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(BAD_REQUEST, message); }
    public record Group(String key, String label, List<Option> options) { }
    public record Option(String id, String label, String description, String instructions, List<String> examples) { }
}
