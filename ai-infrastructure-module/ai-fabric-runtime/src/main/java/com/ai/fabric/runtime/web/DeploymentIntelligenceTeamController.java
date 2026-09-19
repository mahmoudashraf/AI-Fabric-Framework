package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.agentic.DeploymentIntelligenceExecutionView;
import com.ai.fabric.runtime.agentic.DeploymentIntelligenceRequest;
import com.ai.fabric.runtime.agentic.DeploymentIntelligenceTeamService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agentic/v1")
@ConditionalOnProperty(
    name = "ai.execution.specialist-chains.enabled",
    havingValue = "true"
)
public class DeploymentIntelligenceTeamController {

    private final RuntimeRequestAuthResolver authResolver;
    private final DeploymentIntelligenceTeamService teamService;

    public DeploymentIntelligenceTeamController(
        RuntimeRequestAuthResolver authResolver,
        DeploymentIntelligenceTeamService teamService
    ) {
        this.authResolver = authResolver;
        this.teamService = teamService;
    }

    @PostMapping("/execute")
    public DeploymentIntelligenceExecutionView execute(
        @Valid @RequestBody DeploymentIntelligenceRequest input,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.AGENTIC_TEAM_EXECUTE,
            "/api/agentic/v1/execute"
        );
        return teamService.execute(identity, input, idempotencyKey);
    }

    @PostMapping("/executions")
    public ResponseEntity<DeploymentIntelligenceExecutionView> submit(
        @Valid @RequestBody DeploymentIntelligenceRequest input,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.AGENTIC_TEAM_EXECUTE,
            "/api/agentic/v1/executions"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            teamService.submit(identity, input, idempotencyKey)
        );
    }

    @GetMapping("/executions/{executionId}")
    public DeploymentIntelligenceExecutionView status(
        @PathVariable String executionId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.AGENTIC_TEAM_READ,
            "/api/agentic/v1/executions/{executionId}"
        );
        return teamService.status(identity, executionId);
    }

    @PostMapping("/executions/{executionId}/cancel")
    public DeploymentIntelligenceExecutionView cancel(
        @PathVariable String executionId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.AGENTIC_TEAM_CANCEL,
            "/api/agentic/v1/executions/{executionId}/cancel"
        );
        return teamService.cancel(identity, executionId);
    }

    @PostMapping("/executions/{executionId}/replay")
    public DeploymentIntelligenceExecutionView replay(
        @PathVariable String executionId,
        @Valid @RequestBody DeploymentIntelligenceRequest input,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.AGENTIC_TEAM_EXECUTE,
            "/api/agentic/v1/executions/{executionId}/replay"
        );
        return teamService.replay(
            identity,
            executionId,
            input,
            idempotencyKey
        );
    }

    private RuntimeResolvedIdentity authorize(
        HttpServletRequest request,
        String scope,
        String surface
    ) {
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedForChat(
            request
        );
        authResolver.requireScope(identity, scope, surface);
        return identity;
    }
}
