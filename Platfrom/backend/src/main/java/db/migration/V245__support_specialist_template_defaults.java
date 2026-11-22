package db.migration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.List;
import static db.migration.V231__customer_scoped_document_contract_revisions.*;

/** Additive catalogue snapshots only; never edits customer drafts, bindings, offers or release history. */
public class V245__support_specialist_template_defaults extends BaseJavaMigration {
    static ObjectNode defaults() throws Exception {
        try (var input = V245__support_specialist_template_defaults.class.getResourceAsStream("/platform-templates/support-specialist-defaults.json")) {
            if (input == null) throw new IllegalStateException("Support specialist defaults are missing");
            return (ObjectNode) JSON.readTree(input);
        }
    }
    @Override public void migrate(Context context) throws Exception {
        var c = context.getConnection(); var config = defaults();
        var diagnostics = (ObjectNode) config.path("diagnostics");
        if (published(c, diagnostics.path("pluginId").asText()).stream().noneMatch(v -> v.version().equals("1.1.0"))) {
            var previous = latest(c, diagnostics.path("pluginId").asText());
            if (previous != null) insert(c, previous.plugin(), "1.1.0", previous.channel(), previous.publisher(), diagnostics, "support245");
        }
        for (String plugin : List.of("mkp-template-ai-support-workspace", "mkp-template-loomai-support-workspace")) {
            var source = latest(c, plugin); if (source == null) continue;
            var revised = revise(source.manifest(), config, plugin.contains("loomai-support"));
            if (!revised.equals(source.manifest())) insert(c, plugin, nextMinor(c, plugin, source.version()), source.channel(), source.publisher(), revised, "support245");
        }
        try (var query = c.prepareStatement("UPDATE platform_marketplace_plugin_versions SET review_notes=? WHERE id LIKE 'mkv-support245-%'")) {
            query.setString(1, "Support continuity: five configurable read-only specialist areas and bounded adaptive plans. Generic defaults contain no LoomAI diagnostic tools. Historical pins, customer drafts, assignments and release history are unchanged.");
            query.executeUpdate();
        }
    }
    static ObjectNode revise(ObjectNode source, ObjectNode defaults, boolean internal) {
        var result = source.deepCopy(); var template = (ObjectNode) result.at("/contributions/template");
        var behavior = (ObjectNode) template.path("deploymentBehavior");
        behavior.set("specialistAssistance", defaults.path(internal ? "loomai" : "generic").deepCopy());
        var extensions = behavior.withArray("executionExtensions");
        boolean present = false;
        for (var extension : extensions) if ("SPECIALIST_ASSISTANCE".equals(extension.asText())) present = true;
        if (!present) extensions.add("SPECIALIST_ASSISTANCE");
        var prompts = template.with("promptConfig");
        prompts.put("answerGenerationPrompt", defaults.at("/prompts/answerGenerationPrompt").asText());
        String addition = defaults.at("/prompts/systemAddition").asText();
        if (!prompts.path("systemPrompt").asText().contains(addition)) prompts.put("systemPrompt", prompts.path("systemPrompt").asText() + "\n\n" + addition);
        if (internal) {
            var refs = template.withArray("requiredPluginRefs");
            for (int i=0; i<refs.size(); i++) if (refs.get(i).asText().startsWith("mkp-action-loomai-support-diagnostics@"))
                refs.set(i, JSON.getNodeFactory().textNode("mkp-action-loomai-support-diagnostics@1.1.0"));
        }
        return result;
    }
}
