package com.ai.fabric.runtime.specialist;

import ai.fabric.evidence.AIEvidenceReference;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.AIExecutionFailure;
import ai.fabric.execution.gateway.AIExecutionResult;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.client.SpecialistClient;
import ai.fabric.execution.specialist.client.SpecialistClientFactory;
import ai.fabric.execution.specialist.client.SpecialistInvocation;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.web.dto.DeploymentKnowledgeEvidenceResponse;
import com.ai.fabric.runtime.web.dto.DeploymentKnowledgeQueryResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "app.specialists.deployment-knowledge",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DeploymentKnowledgeSpecialistService {

    public static final String SPECIALIST_NAME =
        "deployment-knowledge-specialist";
    public static final String SPECIALIST_VERSION = "1";
    public static final String SPECIALIST_SCOPE =
        "specialist:deployment-knowledge-specialist@1";
    public static final String VECTOR_SCOPE = "vector:document";

    private static final SpecialistId SPECIALIST_ID = SpecialistId.parse(
        SPECIALIST_NAME + "@" + SPECIALIST_VERSION
    );
    private static final Set<String> EXECUTION_SCOPES = Set.of(
        SPECIALIST_SCOPE,
        VECTOR_SCOPE
    );
    private static final Pattern REASON_CODE = Pattern.compile(
        "[A-Z][A-Z0-9_]{0,79}"
    );

    private final SpecialistClient<
        DeploymentKnowledgeQuestion,
        DeploymentKnowledgeAnswer
    > client;
    private final MeterRegistry meterRegistry;

    @Autowired
    public DeploymentKnowledgeSpecialistService(
        SpecialistClientFactory specialistClientFactory,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.client = specialistClientFactory.bind(
            SPECIALIST_ID,
            DeploymentKnowledgeQuestion.class,
            DeploymentKnowledgeAnswer.class
        );
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    DeploymentKnowledgeSpecialistService(
        SpecialistClient<
            DeploymentKnowledgeQuestion,
            DeploymentKnowledgeAnswer
        > client,
        MeterRegistry meterRegistry
    ) {
        this.client = client;
        this.meterRegistry = meterRegistry;
    }

    public QueryResult query(
        RuntimeResolvedIdentity identity,
        DeploymentKnowledgeQuestion question
    ) {
        RuntimeAuthContext auth = requireTrustedContext(identity);
        TrustedExecutionContext trustedContext = new TrustedExecutionContext(
            new ExecutionPrincipal(
                "loomai-ai-fabric-runtime",
                ExecutionPrincipalType.SERVICE
            ),
            new ExecutionSubjectRef("deployment", auth.getDeploymentId()),
            ExecutionSource.APPLICATION,
            auth.getTenantId(),
            auth.getDeploymentId(),
            EXECUTION_SCOPES,
            null,
            Instant.now()
        );

        long startedNanos = System.nanoTime();
        AIExecutionResult<DeploymentKnowledgeAnswer> execution;
        try {
            execution = client.execute(
                SpecialistInvocation.synchronous(question, trustedContext)
            );
        } catch (RuntimeException ex) {
            recordMetrics(
                "FAILED",
                "EXECUTION_INVOCATION_FAILED",
                startedNanos
            );
            log.error(
                "Deployment knowledge specialist invocation failed "
                    + "correlationId={} specialist={} exceptionType={}",
                trustedContext.correlationId(),
                SPECIALIST_ID,
                ex.getClass().getSimpleName()
            );
            throw new DeploymentKnowledgeInvocationException(
                trustedContext.correlationId(),
                ex
            );
        }

        QueryResult result = map(execution, trustedContext.correlationId());
        recordMetrics(
            result.response().status(),
            result.response().reasonCode(),
            startedNanos
        );
        log.info(
            "Deployment knowledge specialist completed correlationId={} "
                + "specialist={} status={} reason={} evidenceCount={}",
            trustedContext.correlationId(),
            SPECIALIST_ID,
            result.response().status(),
            result.response().reasonCode(),
            result.response().evidence().size()
        );
        return result;
    }

    private RuntimeAuthContext requireTrustedContext(
        RuntimeResolvedIdentity identity
    ) {
        RuntimeAuthContext auth = identity != null
            ? identity.getAuthContext()
            : null;
        if (auth == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Verified runtime auth context is required."
            );
        }
        if (!StringUtils.hasText(auth.getTenantId())
            || !StringUtils.hasText(auth.getDeploymentId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Verified runtime tenant and deployment context are required."
            );
        }
        Set<String> granted = auth.getGrantedScopes() == null
            ? Set.of()
            : auth.getGrantedScopes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!granted.containsAll(EXECUTION_SCOPES)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Verified runtime specialist scopes are required."
            );
        }
        return auth;
    }

    private QueryResult map(
        AIExecutionResult<DeploymentKnowledgeAnswer> execution,
        String correlationId
    ) {
        if (execution.succeeded()) {
            DeploymentKnowledgeAnswer output = execution.output();
            return new QueryResult(
                HttpStatus.OK,
                new DeploymentKnowledgeQueryResponse(
                    output.status().name(),
                    bounded(output.answer(), 4000),
                    SPECIALIST_NAME,
                    SPECIALIST_VERSION,
                    correlationId,
                    safeEvidence(execution.evidence()),
                    null
                )
            );
        }

        AIExecutionFailure failure = execution.failure();
        String reason = boundedReason(
            failure != null ? failure.reason() : null
        );
        return new QueryResult(
            failureStatus(execution.status(), reason, failure),
            new DeploymentKnowledgeQueryResponse(
                execution.status().name(),
                failure != null
                    ? bounded(failure.publicMessage(), 500)
                    : "The specialist execution failed.",
                SPECIALIST_NAME,
                SPECIALIST_VERSION,
                correlationId,
                List.of(),
                reason
            )
        );
    }

    private HttpStatus failureStatus(
        AIExecutionStatus status,
        String reason,
        AIExecutionFailure failure
    ) {
        if (status == AIExecutionStatus.DENIED) {
            return HttpStatus.FORBIDDEN;
        }
        if (status == AIExecutionStatus.DEADLINE_EXCEEDED) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if ("GROUNDING_VALIDATION_FAILED".equals(reason)) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if ("REQUEST_REQUIRED".equals(reason)
            || "INPUT_VALIDATION_FAILED".equals(reason)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (failure != null && failure.retryable()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (status == AIExecutionStatus.INVALID) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private List<DeploymentKnowledgeEvidenceResponse> safeEvidence(
        List<AIEvidenceReference> evidence
    ) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        return evidence.stream()
            .limit(4)
            .map(reference -> new DeploymentKnowledgeEvidenceResponse(
                bounded(reference.evidenceId(), 200),
                safeTitle(reference.safeMetadata()),
                reference.relevanceScore(),
                bounded(reference.source(), 200),
                bounded(reference.vectorSpace(), 100)
            ))
            .toList();
    }

    private String safeTitle(Map<String, Object> safeMetadata) {
        if (safeMetadata == null) {
            return null;
        }
        Object title = safeMetadata.get("title");
        return title instanceof String value ? bounded(value, 240) : null;
    }

    private String boundedReason(String rawReason) {
        if (!StringUtils.hasText(rawReason)) {
            return "EXECUTION_FAILED";
        }
        String normalized = rawReason.trim().toUpperCase(Locale.ROOT);
        return REASON_CODE.matcher(normalized).matches()
            ? normalized
            : "EXECUTION_FAILED";
    }

    private String bounded(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
            ? normalized
            : normalized.substring(0, maxLength);
    }

    private void recordMetrics(
        String outcome,
        String reason,
        long startedNanos
    ) {
        if (meterRegistry == null) {
            return;
        }
        String safeOutcome = boundedReason(outcome);
        String safeReason = reason == null ? "NONE" : boundedReason(reason);
        meterRegistry.counter(
            "loomai.specialist.invocations",
            "specialist",
            SPECIALIST_ID.toString(),
            "outcome",
            safeOutcome,
            "reason",
            safeReason
        ).increment();
        Timer.builder("loomai.specialist.invocation.duration")
            .tag("specialist", SPECIALIST_ID.toString())
            .tag("outcome", safeOutcome)
            .register(meterRegistry)
            .record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    public record QueryResult(
        HttpStatus status,
        DeploymentKnowledgeQueryResponse response
    ) {
    }

    public static final class DeploymentKnowledgeInvocationException
        extends RuntimeException {

        private final String correlationId;

        DeploymentKnowledgeInvocationException(
            String correlationId,
            Throwable cause
        ) {
            super("Deployment knowledge specialist invocation failed.", cause);
            this.correlationId = correlationId;
        }

        public String correlationId() {
            return correlationId;
        }
    }
}
