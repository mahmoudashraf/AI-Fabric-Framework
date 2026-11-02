import ai.fabric.execution.chain.manifest.DefaultSpecialistChainManifestCompiler;
import ai.fabric.execution.chain.manifest.SpecialistChainCompilationContext;
import ai.fabric.execution.chain.SpecialistChainResultProjection;
import ai.fabric.execution.chain.SpecialistChainTarget;
import ai.fabric.execution.chain.SpecialistChainTargetResultProjector;
import ai.fabric.execution.config.AIExecutionProperties;
import ai.fabric.execution.gateway.AIExecutionGateway;
import ai.fabric.execution.gateway.AIExecutionResult;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.specialist.SpecialistDefinitionValidator;
import ai.fabric.execution.specialist.client.DefaultSpecialistClientFactory;
import ai.fabric.execution.specialist.manifest.*;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.ActionAccessMode;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Offline qualification against the published framework on the supplied runtime test classpath.
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
                var validator = new SpecialistDefinitionValidator(actions, Set.of("navigator_deep", "executor", "resolver"), Set.of("product-guide"));
                var schemaValidator = new SpecialistJsonSchemaValidator();
                var canonical = new CanonicalJsonSupport(mapper);
                var bootstrap = new SpecialistRegistryBootstrap(List.of(), resources,
                    new DefaultSpecialistManifestCompiler(), validator,
                    new SpecialistGroundingValidatorRegistry(List.of()),
                    new SpecialistFinalOutputValidatorRegistry(List.of()),
                    new SpecialistDirectOutputProjectorRegistry(List.of()),
                    new SpecialistOutputNormalizerRegistry(List.of()), schemaValidator, canonical, mapper,
                    Set.of("navigator_deep", "executor"), properties.getManifests(), SpecialistManifestMetrics.noop());
                var clients = new DefaultSpecialistClientFactory(bootstrap.registry(), mock(AIExecutionGateway.class), mapper);
                var chains = properties.getSpecialistChains();
                chains.setMaxDuration(Duration.ofSeconds(75));
                chains.setMaxManagerDecisions(4);
                chains.setMaxWorkerInvocations(2);
                chains.setMaxParallelWorkers(2);
                var context = new SpecialistChainCompilationContext(bootstrap.registry(), clients,
                    bootstrap.schemaRegistry(), schemaValidator, canonical, mapper, chains);
                var outputSchema = bootstrap.schemaRegistry().require(SpecialistSchemaId.parse("assist-answer@1"), SpecialistSchemaDirection.OUTPUT);
                var managerSchema = bootstrap.schemaRegistry().require(SpecialistSchemaId.parse("assist-manager-investigate-input@1"), SpecialistSchemaDirection.INPUT);
                for (var chain : resources.chainManifests()) {
                    var registration = new DefaultSpecialistChainManifestCompiler().compile(chain, context);
                    for (var target : registration.definition().targets()) {
                        verifySummaryProjection(mapper, schemaValidator, outputSchema, managerSchema, target);
                    }
                }
                System.out.println("Official loader + specialist compiler + typed chain projection (1..2000/2001, full answer 4000): PASS; parallel=" + parallel);
            } finally {
                try (var files = Files.list(directory)) {
                    for (var file : files.toList()) Files.delete(file);
                }
                Files.delete(directory);
            }
        }
        System.out.println("Execution artifact: " + DefaultSpecialistChainManifestCompiler.class.getProtectionDomain().getCodeSource().getLocation());
    }

    @SuppressWarnings("unchecked")
    private static void verifySummaryProjection(ObjectMapper mapper, SpecialistJsonSchemaValidator validator,
                                                SpecialistSchemaDefinition outputSchema, SpecialistSchemaDefinition managerSchema,
                                                SpecialistChainTarget<?, ?, ?> target) {
        int maximum = outputSchema.spec().schema().at("/properties/summary/maxLength").asInt();
        assertThat(maximum).isEqualTo(SpecialistChainResultProjection.MAX_SUMMARY_CHARACTERS).isEqualTo(2000);
        var projector = (SpecialistChainTargetResultProjector<JsonNode, JsonNode>) target.resultProjector();
        var question = mapper.createObjectNode().put("question", "Investigate the approved setup.");
        String fullAnswer = "a".repeat(4000);
        for (int length = 1; length <= maximum; length++) {
            String summary = "s".repeat(length);
            var output = mapper.createObjectNode().put("summary", summary).put("answer", fullAnswer);
            validator.validate(outputSchema, output);
            var projection = projector.project(question, execution(target, output));
            assertThat(projection.summary()).isEqualTo(summary);
            assertThat(output.path("answer").asText()).isEqualTo(fullAnswer);
            assertThat(projection.facts()).isEmpty();
            assertThat(projection.evidenceReferenceIds()).isEmpty();

            var managerInput = mapper.createObjectNode().put("currentUserMessage", question.path("question").asText());
            managerInput.putArray("applicationContext");
            managerInput.putArray("approvedTargets");
            var completed = managerInput.putArray("completedResults").addObject()
                .put("resultId", "result-1").put("specialist", target.specialistId().toString())
                .put("workerInvocationId", "worker-1").put("summary", projection.summary())
                .put("resultHash", "a".repeat(64)).put("completedAt", Instant.EPOCH.toString());
            completed.set("facts", mapper.valueToTree(projection.facts()));
            completed.set("evidenceReferenceIds", mapper.valueToTree(projection.evidenceReferenceIds()));
            managerInput.putObject("remainingBudget").put("managerDecisions", 1).put("workerInvocations", 1)
                .put("parallelWorkers", 1).put("projectedResultCharacters", 8000).put("durationMillis", 1000);
            managerInput.putNull("previousDirectiveFeedback");
            validator.validate(managerSchema, managerInput);
        }
        var tooLong = mapper.createObjectNode().put("summary", "s".repeat(2001)).put("answer", fullAnswer);
        assertThatThrownBy(() -> validator.validate(outputSchema, tooLong)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> projector.project(question, execution(target, tooLong))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpecialistChainResultProjection.summary("s".repeat(2001)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2000");
        var answerOnly = mapper.createObjectNode().put("answer", fullAnswer);
        assertThatThrownBy(() -> validator.validate(outputSchema, answerOnly)).isInstanceOf(IllegalArgumentException.class);
        var answerTooLong = mapper.createObjectNode().put("summary", "Concise findings.").put("answer", "a".repeat(4001));
        assertThatThrownBy(() -> validator.validate(outputSchema, answerTooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    private static AIExecutionResult<JsonNode> execution(SpecialistChainTarget<?, ?, ?> target, JsonNode output) {
        return new AIExecutionResult<>("worker-1", target.specialistId(), AIExecutionStatus.SUCCEEDED,
            output, List.of(), Map.of(), null, Instant.EPOCH, Instant.EPOCH);
    }
}
