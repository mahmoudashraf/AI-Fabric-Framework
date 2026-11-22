package db.migration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Connection;
import java.util.List;
import static db.migration.V231__customer_scoped_document_contract_revisions.*;

/** Retains the two unused invalid seed payloads for audit and publishes corrected additive snapshots. */
public class V246__support_template_extension_contract extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var c = context.getConnection(); var defaults = V245__support_specialist_template_defaults.defaults();
        for (String plugin : List.of("mkp-template-ai-support-workspace", "mkp-template-loomai-support-workspace")) {
            for (var version : published(c, plugin)) {
                if (!version.id().startsWith("mkv-support245-") || !version.manifest().at("/contributions/template/deploymentBehavior").has("executionExtensions")) continue;
                requireUnused(c, version.id());
                try (var query = c.prepareStatement("UPDATE platform_marketplace_plugin_versions SET status='REJECTED', review_notes=? WHERE id=? AND status='PUBLISHED'")) {
                    query.setString(1, "Unused generated snapshot rejected after staging qualification: runtime executionExtensions is not the template defaultExecutionExtensions contract. Payload/hash retained; replaced by an additive corrected revision. No customer assignment was changed.");
                    query.setString(2, version.id()); query.executeUpdate();
                }
            }
            var source = latest(c, plugin); if (source == null) continue;
            var revised = corrected(source.manifest(), defaults, plugin.equals("mkp-template-loomai-support-workspace"));
            if (!revised.equals(source.manifest())) insert(c, plugin, nextMinor(c, plugin, source.version()), source.channel(), source.publisher(), revised, "support246");
        }
        try (var query = c.prepareStatement("UPDATE platform_marketplace_plugin_versions SET review_notes=? WHERE id LIKE 'mkv-support246-%'")) {
            query.setString(1, "Support specialists: corrected template allowed/default execution extensions, five read-only areas and bounded adaptive plans. Historical payloads, customer drafts, offers and assignments retained."); query.executeUpdate();
        }
    }
    static ObjectNode corrected(ObjectNode source, ObjectNode defaults, boolean internal) {
        var result = V245__support_specialist_template_defaults.revise(source, defaults, internal);
        var behavior = (ObjectNode) result.at("/contributions/template/deploymentBehavior");
        behavior.remove("executionExtensions");
        for (String field : List.of("allowedExecutionExtensions", "defaultExecutionExtensions")) {
            var extensions = behavior.withArray(field); boolean present = false;
            for (var extension : extensions) if ("SPECIALIST_ASSISTANCE".equals(extension.asText())) present = true;
            if (!present) extensions.add("SPECIALIST_ASSISTANCE");
        }
        // LoomAI Console navigation/diagnostic guidance is an installation policy, not another company's support UX.
        if (!internal) ((ObjectNode) result.at("/contributions/template/promptConfig"))
            .set("systemPrompt", source.at("/contributions/template/promptConfig/systemPrompt").deepCopy());
        return result;
    }
    private static void requireUnused(Connection c, String versionId) throws Exception {
        try (var query = c.prepareStatement("SELECT count(*) FROM platform_deployment_marketplace_plugin_installs WHERE plugin_version_id=?")) {
            query.setString(1, versionId); try (var rows = query.executeQuery()) {
                rows.next(); if (rows.getInt(1) != 0) throw new IllegalStateException("Invalid Support seed has an installation; explicit operator review required");
            }
        }
        try (var query = c.prepareStatement("SELECT definition_json FROM platform_customer_offers"); var rows = query.executeQuery()) {
            while (rows.next()) {
                var offer = JSON.readTree(rows.getString(1));
                if (versionId.equals(offer.at("/template/versionId").asText())) throw new IllegalStateException("Invalid Support seed has an offer; explicit operator review required");
            }
        }
    }
}
