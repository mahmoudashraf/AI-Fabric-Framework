package com.ai.fabric.runtime.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.fabric.evidence.AIEvidenceReference;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.gateway.AIExecutionFailure;
import ai.fabric.execution.gateway.AIExecutionResult;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.client.SpecialistClient;
import ai.fabric.execution.specialist.client.SpecialistInvocation;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DeploymentKnowledgeSpecialistServiceTest {

    private SpecialistClient<
        DeploymentKnowledgeQuestion,
        DeploymentKnowledgeAnswer
    > client;
    private SimpleMeterRegistry meterRegistry;
    private DeploymentKnowledgeSpecialistService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(SpecialistClient.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new DeploymentKnowledgeSpecialistService(
            client,
            meterRegistry
        );
    }

    @Test
    void constructsExactServerOwnedContextAndProjectsOnlySafeEvidence() {
        AIEvidenceReference evidence = new AIEvidenceReference(
            "document-42",
            "Raw evidence content must never enter the public response.",
            0.91,
            "deployment-runbook",
            "https://internal.example/private",
            "document",
            Map.of(
                "title",
                "Deployment provider policy",
                "tenantId",
                "tenant-other",
                "unsafe",
                "must-not-leak"
            )
        );
        when(client.execute(any())).thenReturn(success(evidence));

        DeploymentKnowledgeSpecialistService.QueryResult result =
            service.query(
                identity(List.of(
                    DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
                    DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
                )),
                new DeploymentKnowledgeQuestion(
                    "Which vector provider is configured?"
                )
            );

        assertThat(result.status()).isEqualTo(HttpStatus.OK);
        assertThat(result.response().status()).isEqualTo("ANSWERED");
        assertThat(result.response().answer())
            .isEqualTo("The deployment uses its approved vector provider.");
        assertThat(result.response().specialistName())
            .isEqualTo("deployment-knowledge-specialist");
        assertThat(result.response().specialistVersion()).isEqualTo("1");
        assertThat(result.response().correlationId()).startsWith("exec-");
        assertThat(result.response().reasonCode()).isNull();
        assertThat(result.response().evidence()).singleElement().satisfies(
            reference -> {
                assertThat(reference.documentId()).isEqualTo("document-42");
                assertThat(reference.title())
                    .isEqualTo("Deployment provider policy");
                assertThat(reference.relevanceScore()).isEqualTo(0.91);
                assertThat(reference.source())
                    .isEqualTo("deployment-runbook");
                assertThat(reference.vectorSpace()).isEqualTo("document");
            }
        );
        assertThat(result.response().toString())
            .doesNotContain(
                "Raw evidence content",
                "internal.example",
                "tenant-other",
                "must-not-leak"
            );

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<SpecialistInvocation> invocationCaptor =
            ArgumentCaptor.forClass(SpecialistInvocation.class);
        org.mockito.Mockito.verify(client).execute(
            invocationCaptor.capture()
        );
        var trusted = invocationCaptor.getValue()
            .trustedExecutionContext();
        assertThat(trusted.initiator().principalId())
            .isEqualTo("loomai-ai-fabric-runtime");
        assertThat(trusted.initiator().principalType())
            .isEqualTo(ExecutionPrincipalType.SERVICE);
        assertThat(trusted.source()).isEqualTo(ExecutionSource.APPLICATION);
        assertThat(trusted.subject().subjectType()).isEqualTo("deployment");
        assertThat(trusted.subject().subjectId()).isEqualTo("dep-123");
        assertThat(trusted.tenantId()).isEqualTo("ten-123");
        assertThat(trusted.deploymentId()).isEqualTo("dep-123");
        assertThat(trusted.grantedScopes()).containsExactlyInAnyOrder(
            DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
            DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
        );
    }

    @Test
    void exposesProviderFailureWithoutReturningAFalseAnswer() {
        when(client.execute(any())).thenReturn(new AIExecutionResult<>(
            "inv-provider",
            SpecialistId.parse("deployment-knowledge-specialist@1"),
            AIExecutionStatus.FAILED,
            null,
            List.of(),
            Map.of(),
            new AIExecutionFailure(
                "PROVIDER_UNAVAILABLE",
                "The configured generation provider is unavailable.",
                true
            ),
            Instant.now(),
            Instant.now()
        ));

        DeploymentKnowledgeSpecialistService.QueryResult result =
            service.query(
                identity(List.of(
                    DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
                    DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
                )),
                new DeploymentKnowledgeQuestion("What is deployed?")
            );

        assertThat(result.status())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.response().status()).isEqualTo("FAILED");
        assertThat(result.response().reasonCode())
            .isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.response().answer())
            .isEqualTo(
                "The configured generation provider is unavailable."
            );
        assertThat(result.response().evidence()).isEmpty();
    }

    @Test
    void exposesMissingApprovedEvidenceAsGroundingFailure() {
        when(client.execute(any())).thenReturn(new AIExecutionResult<>(
            "inv-grounding",
            SpecialistId.parse("deployment-knowledge-specialist@1"),
            AIExecutionStatus.INVALID,
            null,
            List.of(),
            Map.of(),
            new AIExecutionFailure(
                "GROUNDING_VALIDATION_FAILED",
                "Grounding requires approved deployment evidence.",
                false
            ),
            Instant.now(),
            Instant.now()
        ));

        DeploymentKnowledgeSpecialistService.QueryResult result =
            service.query(
                identity(List.of(
                    DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
                    DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
                )),
                new DeploymentKnowledgeQuestion("What is deployed?")
            );

        assertThat(result.status())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(result.response().status()).isEqualTo("INVALID");
        assertThat(result.response().reasonCode())
            .isEqualTo("GROUNDING_VALIDATION_FAILED");
    }

    @Test
    void deniesContextWithoutBothExactScopes() {
        RuntimeResolvedIdentity identity = identity(List.of(
            "specialist:*",
            DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
        ));

        assertThatThrownBy(() -> service.query(
            identity,
            new DeploymentKnowledgeQuestion("What is deployed?")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(
                ((ResponseStatusException) ex).getStatusCode().value()
            ).isEqualTo(403));
    }

    @Test
    void deniesContextWithoutTrustedTenantAndDeployment() {
        RuntimeAuthContext auth = authContext(List.of(
            DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
            DeploymentKnowledgeSpecialistService.VECTOR_SCOPE
        )).toBuilder()
            .tenantId(null)
            .deploymentId(null)
            .build();

        assertThatThrownBy(() -> service.query(
            RuntimeResolvedIdentity.builder()
                .authContext(auth)
                .warnings(List.of())
                .build(),
            new DeploymentKnowledgeQuestion("What is deployed?")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(
                ((ResponseStatusException) ex).getStatusCode().value()
            ).isEqualTo(403));
    }

    private AIExecutionResult<DeploymentKnowledgeAnswer> success(
        AIEvidenceReference evidence
    ) {
        return new AIExecutionResult<>(
            "inv-success",
            SpecialistId.parse("deployment-knowledge-specialist@1"),
            AIExecutionStatus.SUCCEEDED,
            new DeploymentKnowledgeAnswer(
                DeploymentKnowledgeAnswer.Status.ANSWERED,
                "The deployment uses its approved vector provider."
            ),
            List.of(evidence),
            Map.of(),
            null,
            Instant.now(),
            Instant.now()
        );
    }

    private RuntimeResolvedIdentity identity(List<String> scopes) {
        return RuntimeResolvedIdentity.builder()
            .authContext(authContext(scopes))
            .warnings(List.of())
            .build();
    }

    private RuntimeAuthContext authContext(List<String> scopes) {
        return RuntimeAuthContext.builder()
            .subjectId("platform-operator")
            .subjectType(RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER)
            .authMode(RuntimeAuthMode.PLATFORM_PROXY_SESSION)
            .callerType(RuntimeAuthCallerType.PLATFORM_PROXY)
            .deploymentId("dep-123")
            .customerId("cust-123")
            .tenantId("ten-123")
            .issuer("loomai-platform")
            .audiences(List.of("dep-123"))
            .expiresAt(Instant.now().plusSeconds(300))
            .grantedScopes(scopes)
            .build();
    }
}
