import ai.fabric.execution.chain.manifest.DefaultSpecialistChainManifestCompiler;
import ai.fabric.execution.chain.manifest.SpecialistChainCompilationContext;
import ai.fabric.execution.config.AIExecutionProperties;
import ai.fabric.execution.gateway.AIExecutionGateway;
import ai.fabric.execution.specialist.SpecialistDefinitionValidator;
import ai.fabric.execution.specialist.client.DefaultSpecialistClientFactory;
import ai.fabric.execution.specialist.manifest.*;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.ActionAccessMode;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * Offline qualification against the published 0.8.41 runtime test classpath.
 * Kept as a source-launcher fixture so the control plane does not acquire an execution-engine dependency.
 */
class OfficialContractCheck {
    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        for (boolean parallel : List.of(false, true)) {
            var config = SpecialistAssistanceFixtures.behavior(mapper);
            ((ObjectNode) config.at("/specialistAssistance/resolutions/0")).put("allowParallel", parallel);
            var compiled = new SpecialistAssistanceCompiler(mapper)
                .compileBehavior(config, SpecialistAssistanceFixtures.authority()).at("/specialistAssistance/compiled");
            var directory = Files.createTempDirectory("specialist-assistance-official-");
            try {
                List<String> locations = new ArrayList<>();
                int index = 0;
                for (var resource : compiled.path("resources")) {
                    var file = directory.resolve("resource-" + index++ + ".json");
                    Files.write(file, mapper.writeValueAsBytes(resource));
                    locations.add(file.toUri().toString());
                }
                var properties = new AIExecutionProperties();
                properties.getManifests().setEnabled(true);
                properties.getManifests().setLocations(locations);
                var resources = new DefaultSpecialistManifestLoader(mapper).load(properties.getManifests());
                var action = mock(AIActionMetaData.class);
                when(action.getName()).thenReturn("get_account_status");
                when(action.getAccessMode()).thenReturn(ActionAccessMode.READ);
                var actions = mock(AIActionRegistry.class);
                when(actions.getAllMetadata()).thenReturn(List.of(action));
                var validator = new SpecialistDefinitionValidator(actions, Set.of("navigator_deep", "resolver"), Set.of("product-guide"));
                var schemaValidator = new SpecialistJsonSchemaValidator();
                var canonical = new CanonicalJsonSupport(mapper);
                var bootstrap = new SpecialistRegistryBootstrap(List.of(), resources,
                    new DefaultSpecialistManifestCompiler(), validator,
                    new SpecialistGroundingValidatorRegistry(List.of()),
                    new SpecialistFinalOutputValidatorRegistry(List.of()),
                    new SpecialistDirectOutputProjectorRegistry(List.of()),
                    new SpecialistOutputNormalizerRegistry(List.of()), schemaValidator, canonical, mapper,
                    Set.of("navigator_deep"), properties.getManifests(), SpecialistManifestMetrics.noop());
                var clients = new DefaultSpecialistClientFactory(bootstrap.registry(), mock(AIExecutionGateway.class), mapper);
                var chains = properties.getSpecialistChains();
                chains.setMaxDuration(Duration.ofSeconds(75));
                chains.setMaxManagerDecisions(4);
                chains.setMaxWorkerInvocations(2);
                chains.setMaxParallelWorkers(2);
                var context = new SpecialistChainCompilationContext(bootstrap.registry(), clients,
                    bootstrap.schemaRegistry(), schemaValidator, canonical, mapper, chains);
                for (var chain : resources.chainManifests()) {
                    new DefaultSpecialistChainManifestCompiler().compile(chain, context);
                }
                System.out.println("Published 0.8.41 official loader + specialist compiler + typed chain registry: PASS; parallel=" + parallel);
            } finally {
                try (var files = Files.list(directory)) {
                    for (var file : files.toList()) Files.delete(file);
                }
                Files.delete(directory);
            }
        }
    }
}
