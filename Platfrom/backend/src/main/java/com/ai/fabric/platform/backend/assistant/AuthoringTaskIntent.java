package com.ai.fabric.platform.backend.assistant;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Registered editor focus is guidance, never additional write authority. */
final class AuthoringTaskIntent {
    private AuthoringTaskIntent() { }

    enum Section {
        ACTIONS("ACTIONS", "Prepare action definitions, parameters and endpoint routing"),
        CONNECTIONS("CONNECTIONS", "Prepare connection and endpoint routing settings"),
        KNOWLEDGE("KNOWLEDGE", "Prepare knowledge-source settings"),
        DOCUMENTS("KNOWLEDGE", "Prepare document knowledge-source settings"),
        LIVE_DATA("KNOWLEDGE", "Prepare live-data knowledge-source settings"),
        STORAGE("KNOWLEDGE", "Prepare document-storage source settings"),
        SEARCH("MODELS", "Prepare embedding, vector-service and search settings"),
        BEHAVIOUR("BEHAVIOUR", "Prepare response style and behaviour settings"),
        INSTRUCTIONS("BEHAVIOUR", "Prepare the Smart Brain's response instructions"),
        MODELS("MODELS", "Prepare response-model settings"),
        HUMAN_SUPPORT("HUMAN_SUPPORT", "Prepare human-support and handover settings"),
        ACTION_REVIEWS("HUMAN_SUPPORT", "Explain the current action-review setup and the next steps"),
        SPECIALISTS("SPECIALISTS", "Prepare supported specialist-team settings"),
        KIT("KIT", "Explain the next setup steps for this Kit and its existing resource editors"),
        DATA_PLUGIN("DATA_PLUGIN", "Prepare supported data-source mappings and schema for this plugin"),
        ENGAGE("ENGAGE", "Prepare the branded AI workspace's supported app settings");

        final String task;
        final String intent;
        Section(String task, String intent) { this.task = task; this.intent = intent; }
    }

    record EditorContext(String section, List<String> selectedDefinitionIds) { }
    record Resolved(String query, String command, EditorContext editorContext) { }

    static Resolved resolve(String task, String query, String command, EditorContext context) {
        if (command != null && !"PREPARE".equals(command)) throw bad("Unsupported assistant command.");
        boolean prepare = "PREPARE".equals(command);
        if (query == null) query = "";
        if (query.length() > (prepare ? 6_000 : 8_000) || !prepare && query.isBlank())
            throw bad(prepare ? "Keep the optional focus hint to 6,000 characters." : "Describe the configuration change in at most 8,000 characters.");
        Section section;
        try { section = Section.valueOf(context == null ? task : context.section()); }
        catch (IllegalArgumentException | NullPointerException invalid) { throw bad("Unsupported assistant editor section."); }
        // This is an execution identity check, not validation of the model's semantic decision.
        if (!section.task.equals(task)) throw bad("The assistant editor section does not match this task.");
        var ids = context == null || context.selectedDefinitionIds() == null ? List.<String>of() : context.selectedDefinitionIds();
        if (ids.size() > 16 || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 256))
            throw bad("Select at most sixteen editor definitions with bounded identifiers.");
        String resolved = prepare ? section.intent + " for this resource. Use the selected documentation, current configuration and available contracts. "
            + "Preserve unrelated settings. Ask only for essential missing facts or genuinely ambiguous scope; do not ask the user to write configuration syntax."
            + (query.isBlank() ? "" : "\n\nOptional focus from the user:\n" + query) : query;
        return new Resolved(resolved, command, new EditorContext(section.name(), List.copyOf(ids)));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
