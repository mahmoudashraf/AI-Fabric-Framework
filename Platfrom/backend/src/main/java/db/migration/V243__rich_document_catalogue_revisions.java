package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.LinkedHashMap;
import java.util.List;
import static db.migration.V231__customer_scoped_document_contract_revisions.*;

/** Additive format support. Never repins existing offers, installations, drafts or releases. */
public class V243__rich_document_catalogue_revisions extends BaseJavaMigration {
    public static final List<String> EXTENSIONS=List.of(".txt",".json",".pdf",".docx",".doc",".png",".jpg",".jpeg",".tif",".tiff");
    public static final List<String> MEDIA_TYPES=List.of("text/plain","application/json","application/pdf","application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document","image/png","image/jpeg","image/tiff");
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        var documents=new LinkedHashMap<String,Version>();
        for(String plugin:List.of("mkp-data-document-knowledge-s3","mkp-data-dealership-knowledge-v1","mkp-data-ai-support-documents",
            "mkp-data-document-knowledge-mounted-demo","mkp-data-dealership-knowledge-mounted-demo-v1")) {
            var source=latest(connection,plugin); if(source==null) continue;
            ObjectNode manifest=richManifest(source.manifest());
            if(manifest.equals(source.manifest())) { documents.put(plugin,source); continue; }
            documents.put(plugin,insert(connection,plugin,nextMinor(connection,plugin,source.version()),source.channel(),source.publisher(),manifest,"doc243"));
        }
        for(String plugin:List.of("mkp-template-document-knowledge-assistant","mkp-template-simulated-dealership-experience-v1",
            "mkp-template-dealership-external-systems-v1","mkp-template-dealership-external-systems-managed-inbox-v1",
            "mkp-template-ai-support-workspace","mkp-template-loomai-support-workspace")) {
            var source=latest(connection,plugin); if(source==null) continue;
            ObjectNode manifest=reviseTemplate(source.manifest(),documents);
            if(!manifest.equals(source.manifest())) insert(connection,plugin,nextMinor(connection,plugin,source.version()),source.channel(),source.publisher(),manifest,"doc243");
        }
        try(var statement=connection.prepareStatement("UPDATE platform_marketplace_plugin_versions SET review_notes=? WHERE id LIKE 'mkv-doc243-%'")) {
            statement.setString(1,"010.72: bounded PDF/Word/image readers and local OCR. Customer ownership, approved-document filters and explicit indexing remain unchanged. Requires compatible runtime; historical pins are retained.");
            statement.executeUpdate();
        }
    }
    public static ObjectNode richManifest(ObjectNode source) {
        var result=source.deepCopy();
        for(JsonNode entry:result.at("/contributions/datasets")) {
            if(!"EXTERNAL_DOCUMENT_STORAGE".equals(entry.path("ingestionMode").asText()) || !(entry.path("documentPolicy") instanceof ObjectNode policy)) continue;
            policy.set("allowedExtensions",JSON.valueToTree(EXTENSIONS)); policy.set("allowedMediaTypes",JSON.valueToTree(MEDIA_TYPES));
            policy.put("maxSourceBytes",20*1024*1024);
        }
        return result;
    }
}
