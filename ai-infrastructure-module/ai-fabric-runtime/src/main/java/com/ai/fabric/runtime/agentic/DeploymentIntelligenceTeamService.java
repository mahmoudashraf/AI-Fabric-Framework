package com.ai.fabric.runtime.agentic;

import ai.fabric.execution.chain.SpecialistChainExecutionRequest;
import ai.fabric.execution.chain.SpecialistChainExecutionResult;
import ai.fabric.execution.chain.SpecialistChainExecutionSnapshot;
import ai.fabric.execution.chain.SpecialistChainGateway;
import ai.fabric.execution.chain.SpecialistChainId;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(
    name = "ai.execution.specialist-chains.enabled",
    havingValue = "true"
)
public class DeploymentIntelligenceTeamService {

    public static final SpecialistChainId CHAIN_ID = SpecialistChainId.of(
        "deployment-intelligence-team",
        "1"
    );

    private static final Set<String> INTERNAL_CHAIN_SCOPES = Set.of(
        "specialist:deployment-intelligence-manager@1",
        "specialist:deployment-knowledge-specialist@1",
        "specialist:deployment-runtime-state-specialist@1",
        "vector:document"
    );

    private final SpecialistChainGateway gateway;
    private final DeploymentRuntimeStateSnapshotService runtimeStateService;
    private final ObjectMapper objectMapper;

    public DeploymentIntelligenceTeamService(
        SpecialistChainGateway gateway,
        DeploymentRuntimeStateSnapshotService runtimeStateService,
        ObjectMapper objectMapper
    ) {
        this.gateway = gateway;
        this.runtimeStateService = runtimeStateService;
        this.objectMapper = objectMapper;
    }

    public DeploymentIntelligenceExecutionView execute(
        RuntimeResolvedIdentity identity,
        DeploymentIntelligenceRequest input,
        String idempotencyKey
    ) {
        SpecialistChainExecutionResult result = gateway.execute(
            request(identity, input, idempotencyKey)
        );
        return DeploymentIntelligenceExecutionView.from(result);
    }

    public DeploymentIntelligenceExecutionView submit(
        RuntimeResolvedIdentity identity,
        DeploymentIntelligenceRequest input,
        String idempotencyKey
    ) {
        SpecialistChainExecutionRequest<JsonNode> request = request(
            identity,
            input,
            idempotencyKey
        );
        var handle = gateway.submit(request);
        if (!handle.replayed() || !handle.status().terminal()) {
            return DeploymentIntelligenceExecutionView.from(handle);
        }
        SpecialistChainExecutionSnapshot snapshot = gateway.find(
            handle.executionId(),
            request.trustedExecutionContext()
        ).orElseThrow(() -> new IllegalStateException(
            "The replayed agentic execution state is unavailable."
        ));
        SpecialistChainExecutionResult result = gateway.findResult(
            handle.executionId(),
            request.trustedExecutionContext()
        ).orElseThrow(() -> new IllegalStateException(
            "The replayed agentic execution result is unavailable."
        ));
        return DeploymentIntelligenceExecutionView.from(
            snapshot,
            result.asReplayed()
        );
    }

    public DeploymentIntelligenceExecutionView status(
        RuntimeResolvedIdentity identity,
        String executionId
    ) {
        TrustedExecutionContext context = trustedContext(identity);
        SpecialistChainExecutionSnapshot snapshot = gateway.find(
            requiredExecutionId(executionId),
            context
        ).orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Agentic execution was not found."
        ));
        SpecialistChainExecutionResult result = snapshot.status().terminal()
            ? gateway.findResult(executionId, context).orElseThrow(() ->
                new IllegalStateException(
                    "The terminal agentic execution result is unavailable."
                )
            )
            : null;
        return DeploymentIntelligenceExecutionView.from(snapshot, result);
    }

    public DeploymentIntelligenceExecutionView cancel(
        RuntimeResolvedIdentity identity,
        String executionId
    ) {
        TrustedExecutionContext context = trustedContext(identity);
        String requiredId = requiredExecutionId(executionId);
        if (!gateway.cancel(requiredId, context)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Agentic execution is missing or already terminal."
            );
        }
        return status(identity, requiredId);
    }

    public DeploymentIntelligenceExecutionView replay(
        RuntimeResolvedIdentity identity,
        String executionId,
        DeploymentIntelligenceRequest input,
        String idempotencyKey
    ) {
        String requiredId = requiredExecutionId(executionId);
        DeploymentIntelligenceExecutionView replayed = submit(
            identity,
            input,
            idempotencyKey
        );
        if (!requiredId.equals(replayed.executionId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The supplied request does not replay this execution."
            );
        }
        return replayed;
    }

    private SpecialistChainExecutionRequest<JsonNode> request(
        RuntimeResolvedIdentity identity,
        DeploymentIntelligenceRequest input,
        String idempotencyKey
    ) {
        String question = input != null && StringUtils.hasText(input.question())
            ? input.question().trim()
            : null;
        if (!StringUtils.hasText(question)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "question is required."
            );
        }
        ObjectNode chainInput = objectMapper.createObjectNode();
        chainInput.put("question", question);
        chainInput.put("knowledgeQuestion", question);
        chainInput.put("runtimeStateQuestion", question);
        chainInput.set("runtimeState", runtimeStateService.snapshot());
        return new SpecialistChainExecutionRequest<>(
            CHAIN_ID,
            chainInput,
            trustedContext(identity),
            null,
            null,
            requiredIdempotencyKey(idempotencyKey)
        );
    }

    private TrustedExecutionContext trustedContext(
        RuntimeResolvedIdentity identity
    ) {
        RuntimeAuthContext auth = identity != null
            ? identity.getAuthContext()
            : null;
        if (auth == null
            || !StringUtils.hasText(auth.getTenantId())
            || !StringUtils.hasText(auth.getDeploymentId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Verified runtime tenant and deployment context are required."
            );
        }

        boolean interactive = auth.getCallerType()
            == RuntimeAuthCallerType.PUBLIC_BROWSER;
        String principalId = StringUtils.hasText(auth.getSubjectId())
            ? auth.getSubjectId().trim()
            : interactive ? "runtime-user" : "loomai-runtime-service";
        return new TrustedExecutionContext(
            new ExecutionPrincipal(
                principalId,
                interactive
                    ? ExecutionPrincipalType.END_USER
                    : ExecutionPrincipalType.SERVICE
            ),
            new ExecutionSubjectRef("deployment", auth.getDeploymentId()),
            interactive
                ? ExecutionSource.INTERACTIVE
                : ExecutionSource.APPLICATION,
            auth.getTenantId(),
            auth.getDeploymentId(),
            INTERNAL_CHAIN_SCOPES,
            null,
            Instant.now()
        );
    }

    private String requiredIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Idempotency-Key is required."
            );
        }
        String normalized = value.trim();
        if (normalized.length() > 160) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Idempotency-Key must not exceed 160 characters."
            );
        }
        return normalized;
    }

    private String requiredExecutionId(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A valid execution ID is required."
            );
        }
        return value.trim();
    }
}
