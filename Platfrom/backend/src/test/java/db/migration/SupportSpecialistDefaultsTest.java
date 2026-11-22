package db.migration;

import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import static org.assertj.core.api.Assertions.*;

class SupportSpecialistDefaultsTest {
    final ObjectMapper json = new ObjectMapper();

    @Test void correctedProfilesPassTheActualTemplateParserNotJustTheRuntimeCompiler() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("product-templates/ai-support-workspace"))) root = root.getParent();
        assertThat(root).isNotNull();
        for (String name : Set.of("ai-support-workspace", "loomai-support-workspace")) {
            boolean internal = name.startsWith("loomai-");
            var source = (ObjectNode) json.readTree(Files.readString(root.resolve("product-templates/ai-support-workspace/manifests/"+name+".1.3.0.manifest.json")));
            var defaults = V245__support_specialist_template_defaults.defaults();
            var bad = V245__support_specialist_template_defaults.revise(source,defaults,internal);
            var plugin = new MarketplacePluginEntity(); plugin.setId(source.path("pluginId").asText()); plugin.setPluginType("TEMPLATE");
            var version = new MarketplacePluginVersionEntity(); version.setPluginId(plugin.getId()); version.setVersion(source.path("version").asText());
            version.setManifestJson(json.writeValueAsString(bad));
            var parser = new MarketplaceManifestService(json);
            assertThatThrownBy(() -> parser.parseAndValidate(plugin,version)).hasMessageContaining("must agree with executionExtensions");
            var corrected = V246__support_template_extension_contract.corrected(source,defaults,internal);
            version.setManifestJson(json.writeValueAsString(corrected));
            assertThatCode(() -> parser.parseAndValidate(plugin,version)).doesNotThrowAnyException();
            assertThat(corrected.at("/contributions/template/deploymentBehavior").has("executionExtensions")).isFalse();
            if (!internal) assertThat(corrected.at("/contributions/template/promptConfig/systemPrompt"))
                .isEqualTo(source.at("/contributions/template/promptConfig/systemPrompt"));
            assertThat(V246__support_template_extension_contract.corrected(corrected,defaults,internal)).isEqualTo(corrected);
        }
    }

    @Test void fiveAreasAndAdaptivePlansCompileInsideExistingBudgetsWithoutWritesOrPrivateGenericTools() throws Exception {
        var defaults = V245__support_specialist_template_defaults.defaults();
        for (String profile : Set.of("generic", "loomai")) {
            var config = defaults.path(profile); var behavior = json.createObjectNode();
            behavior.put("type", "CONVERSATIONAL"); behavior.putArray("executionExtensions").add("SPECIALIST_ASSISTANCE");
            behavior.set("specialistAssistance", config);
            var reads = new LinkedHashMap<String, Set<String>>();
            for (var area : config.path("areas")) for (var read : area.path("readActions")) reads.put(read.asText(), Set.of("support:diagnostics:read"));
            if ("generic".equals(profile)) assertThat(reads).isEmpty();
            assertThat(config.path("areas")).hasSize(5); assertThat(config.path("resolutions")).hasSize(5);
            var compiler = new SpecialistAssistanceCompiler(json);
            var result = compiler.compileBehavior(behavior, new SpecialistAssistanceCompiler.Authority(reads, Set.of("document")));
            var compiled = result.at("/specialistAssistance/compiled");
            assertThat(compiled.path("invocations")).hasSize(10);
            for (var resource : compiled.path("resources")) {
                if ("Specialist".equals(resource.path("kind").asText())) {
                    assertThat(resource.at("/spec/execution/writePolicy").asText()).isEqualTo("DISABLED");
                    assertThat(resource.at("/spec/capabilities/actions/proposableWrites")).isEmpty();
                }
                if ("SpecialistChain".equals(resource.path("kind").asText())) {
                    assertThat(resource.at("/spec/limits/maxManagerDecisions").asInt()).isEqualTo(6);
                    assertThat(resource.at("/spec/limits/maxWorkerInvocations").asInt()).isEqualTo(4);
                    assertThat(resource.at("/spec/limits/maxParallelWorkers").asInt()).isEqualTo(2);
                    assertThat(resource.at("/spec/conversationPolicy").asText()).isEqualTo("DISABLED");
                }
            }
            compiler.verifyCompiled(compiled, result.at("/specialistAssistance/contentHash").asText());
        }
    }

    @Test void templateRevisionPreservesModelsDataPinsPromptsOutsideAnswerStyleAndExistingAuthority() throws Exception {
        var source = (ObjectNode) json.readTree("""
            {"contributions":{"template":{"requiredPluginRefs":["mkp-data-ai-support-documents@1.2.0","mkp-action-loomai-support-diagnostics@1.0.3"],
              "deploymentBehavior":{"type":"CONVERSATIONAL","executionExtensions":["CONVERSATION_HANDOVER"],"conversationHandover":{"enabled":true}},
              "providerConfig":{"unknownFutureField":42},"promptConfig":{"systemPrompt":"Original customer policy","custom":42},
              "requiredPluginInstallDefaults":{"owned":{"secretRefs":{"key":"REF_ONLY"}}}}}}
            """);
        var original = source.deepCopy();
        var result = V245__support_specialist_template_defaults.revise(source, V245__support_specialist_template_defaults.defaults(), true);
        assertThat(source).isEqualTo(original);
        for (String key : Set.of("providerConfig", "requiredPluginInstallDefaults")) assertThat(result.at("/contributions/template/"+key)).isEqualTo(source.at("/contributions/template/"+key));
        assertThat(result.at("/contributions/template/requiredPluginRefs/0").asText()).isEqualTo("mkp-data-ai-support-documents@1.2.0");
        assertThat(result.at("/contributions/template/requiredPluginRefs/1").asText()).isEqualTo("mkp-action-loomai-support-diagnostics@1.1.0");
        assertThat(result.at("/contributions/template/deploymentBehavior/conversationHandover")).isEqualTo(source.at("/contributions/template/deploymentBehavior/conversationHandover"));
        assertThat(result.at("/contributions/template/promptConfig/custom").asInt()).isEqualTo(42);
        assertThat(V245__support_specialist_template_defaults.revise(result, V245__support_specialist_template_defaults.defaults(), true)).isEqualTo(result);
    }
}
