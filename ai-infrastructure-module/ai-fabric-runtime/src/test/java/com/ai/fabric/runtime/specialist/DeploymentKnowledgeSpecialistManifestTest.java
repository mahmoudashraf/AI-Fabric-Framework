package com.ai.fabric.runtime.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.DefaultSpecialistAuthorityResolver;
import ai.fabric.execution.specialist.ExecutionStrategy;
import ai.fabric.execution.specialist.SpecialistDefinition;
import ai.fabric.execution.specialist.SpecialistDefinitionSource;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.specialist.manifest.SpecialistConversationBinding;
import ai.fabric.execution.specialist.manifest.SpecialistInteractionCapability;
import ai.fabric.execution.specialist.manifest.SpecialistManifestRuntimeStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:deployment-knowledge-manifest;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ai.providers.openai.enabled=false",
    "ai.vector-db.lucene.index-path=target/deployment-knowledge-manifest-index",
    "ai.fabric.runtime.authz.mode=DENY_ALL",
    "ai.fabric.runtime.auth.ingress.trusted-backend.api-key-value=test-deployment-knowledge-backend-key",
    "ai.fabric.runtime.auth.ingress.private-assertions.signing-key=test-deployment-knowledge-assertion-signing-key",
    "ai.fabric.runtime.auth.ingress.accepted-issuers[0]=test-platform",
    "ai.fabric.runtime.auth.ingress.accepted-audiences[0]=test-deployment",
    "logging.level.ai.fabric=WARN"
})
class DeploymentKnowledgeSpecialistManifestTest {

    private static final SpecialistId SPECIALIST_ID = SpecialistId.parse(
        "deployment-knowledge-specialist@1"
    );

    @Autowired
    private SpecialistRegistry specialistRegistry;

    @Autowired
    private SpecialistManifestRuntimeStatus manifestStatus;

    @Autowired
    private DeploymentKnowledgeSpecialistHealthIndicator healthIndicator;

    @Autowired
    private AIEntityConfigurationLoader entityConfigurationLoader;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void compilesOneExactReadOnlyDocumentSpecialist() {
        SpecialistDefinition<JsonNode, JsonNode> definition = definition();
        var registered = specialistRegistry.requireRegistered(SPECIALIST_ID);

        assertThat(registered.source())
            .isEqualTo(SpecialistDefinitionSource.MANIFEST);
        assertThat(registered.contentHash()).matches("[a-f0-9]{64}");
        assertThat(definition.executionProfile().mode())
            .isEqualTo("deployment_knowledge");
        assertThat(definition.executionProfile().strategy())
            .isEqualTo(ExecutionStrategy.SINGLE_PASS);
        assertThat(definition.executionProfile().writeEnabled()).isFalse();
        assertThat(definition.executionProfile()
            .requestedCapabilities().requestedVectorSpaces())
            .containsExactly("document");
        assertThat(definition.executionProfile()
            .requestedCapabilities().visibleActions()).isEmpty();
        assertThat(definition.executionProfile()
            .requestedCapabilities().requestableReadActions()).isEmpty();
        assertThat(definition.executionProfile()
            .requestedCapabilities().proposableWriteActions()).isEmpty();
        assertThat(definition.inputAdapter().conversationBinding())
            .isEqualTo(SpecialistConversationBinding.DISABLED);
        assertThat(definition.inputAdapter().recordValidatedTurns()).isFalse();
        assertThat(definition.inputAdapter().interactionCapability())
            .isEqualTo(SpecialistInteractionCapability.NON_INTERACTIVE);

        assertThat(manifestStatus.ready()).isTrue();
        assertThat(manifestStatus.manifestDefinitionCount()).isEqualTo(1);
        assertThat(healthIndicator.health().getStatus())
            .isEqualTo(Status.UP);
    }

    @Test
    void schemaRejectsCallerOwnedIdentityAndUnknownOutputStatus() {
        SpecialistDefinition<JsonNode, JsonNode> definition = definition();
        JsonNode request = objectMapper.createObjectNode()
            .put("question", "Which provider is configured?")
            .put("tenantId", "caller-owned-tenant");
        JsonNode output = objectMapper.createObjectNode()
            .put("status", "INVENTED")
            .put("answer", "Unsupported status");

        assertThatThrownBy(() ->
            definition.inputAdapter().validate(request)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            definition.outputAdapter().validate(output)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void packagedDocumentConfigRequiresTrustedRetrievalBoundaries() {
        var documentConfig = entityConfigurationLoader.getEntityConfig(
            "document"
        );

        assertThat(documentConfig).isNotNull();
        assertThat(documentConfig.getMetadataFields())
            .filteredOn(field ->
                "tenantId".equals(field.getName())
                    || "deploymentId".equals(field.getName())
            )
            .hasSize(2)
            .allSatisfy(field -> {
                assertThat(field.getRequired()).isTrue();
                assertThat(field.getDestinations())
                    .extracting(Enum::name)
                    .contains("VECTOR_METADATA");
            });
    }

    @Test
    void realAuthorityResolverRequiresExactSpecialistAndVectorScopes() {
        DefaultSpecialistAuthorityResolver resolver =
            new DefaultSpecialistAuthorityResolver();
        SpecialistDefinition<JsonNode, JsonNode> definition = definition();

        var authority = resolver.resolve(
            definition,
            trustedContext(Set.of(
                DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
                DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
            ))
        );

        assertThat(authority.allowedActions()).isEmpty();
        assertThat(authority.allowedVectorSpaces())
            .containsExactly("document");
        assertThatThrownBy(() -> resolver.resolve(
            definition,
            trustedContext(Set.of(
                "specialist:*",
                DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
            ))
        )).isInstanceOf(
            DefaultSpecialistAuthorityResolver.AuthorityDeniedException.class
        );
    }

    @SuppressWarnings("unchecked")
    private SpecialistDefinition<JsonNode, JsonNode> definition() {
        return (SpecialistDefinition<JsonNode, JsonNode>)
            (SpecialistDefinition<?, ?>)
                specialistRegistry.require(SPECIALIST_ID);
    }

    private TrustedExecutionContext trustedContext(Set<String> scopes) {
        return new TrustedExecutionContext(
            new ExecutionPrincipal(
                "loomai-runtime",
                ExecutionPrincipalType.SERVICE
            ),
            new ExecutionSubjectRef("deployment", "dep-123"),
            ExecutionSource.APPLICATION,
            "ten-123",
            "dep-123",
            scopes,
            "test-correlation",
            Instant.now()
        );
    }
}
