package db.migration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.List;
import static db.migration.V231__customer_scoped_document_contract_revisions.*;

/** Keeps the four reviewed resource endpoints and makes fresh specialist setup dependency-aware. */
public class V247__compound_support_diagnostics_and_setup_defaults extends BaseJavaMigration {
    static ObjectNode defaults() throws Exception {
        try (var input=V247__compound_support_diagnostics_and_setup_defaults.class.getResourceAsStream("/platform-templates/support-specialist-defaults.v2.json")) {
            if(input==null) throw new IllegalStateException("Compound Support defaults are missing");
            return (ObjectNode) JSON.readTree(input);
        }
    }
    @Override public void migrate(Context context) throws Exception {
        var c=context.getConnection(); var config=defaults(); var diagnostic=(ObjectNode) config.path("diagnostics");
        var prior=latest(c,diagnostic.path("pluginId").asText());
        if(prior!=null && published(c,prior.plugin()).stream().noneMatch(v->v.version().equals("1.2.0")))
            insert(c,prior.plugin(),"1.2.0",prior.channel(),prior.publisher(),diagnostic,"support247");
        for(String plugin:List.of("mkp-template-ai-support-workspace","mkp-template-loomai-support-workspace")) {
            var source=latest(c,plugin); if(source==null)continue;
            var revised=revise(source.manifest(),config,plugin.equals("mkp-template-loomai-support-workspace"));
            if(!revised.equals(source.manifest()))insert(c,plugin,nextMinor(c,plugin,source.version()),source.channel(),source.publisher(),revised,"support247");
        }
        try(var query=c.prepareStatement("UPDATE platform_marketplace_plugin_versions SET review_notes=? WHERE id LIKE 'mkv-support247-%'")) {
            query.setString(1,"Compound consent-bound resource observations retain existing endpoint/credential fields. Five areas/plans are preconfigured but initially off until their sources/tools are connected. Customer drafts, assignments and previous version payloads are unchanged.");query.executeUpdate();
        }
    }
    static ObjectNode revise(ObjectNode source,ObjectNode defaults,boolean internal) {
        var result=V246__support_template_extension_contract.corrected(source,defaults,internal);
        var behavior=(ObjectNode)result.at("/contributions/template/deploymentBehavior");
        ((ObjectNode)behavior.path("specialistAssistance")).put("enabled",false);
        var extensions=behavior.withArray("defaultExecutionExtensions");
        for(int i=extensions.size()-1;i>=0;i--)if("SPECIALIST_ASSISTANCE".equals(extensions.get(i).asText()))extensions.remove(i);
        if(internal) {
            var refs=((ObjectNode)result.at("/contributions/template")).withArray("requiredPluginRefs");
            for(int i=0;i<refs.size();i++)if(refs.get(i).asText().startsWith("mkp-action-loomai-support-diagnostics@"))
                refs.set(i,JSON.getNodeFactory().textNode("mkp-action-loomai-support-diagnostics@1.2.0"));
        }
        return result;
    }
}
