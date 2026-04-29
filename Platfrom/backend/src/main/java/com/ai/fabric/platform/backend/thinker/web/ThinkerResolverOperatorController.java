package com.ai.fabric.platform.backend.thinker.web;

import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.CreateResolverProposalRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.CreateThinkerIssueSessionRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ExecuteResolverProposalRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverDryRunSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverExecutionSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverIntentProposalSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ResolverPolicyDecisionSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerDeploymentControlSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerEvidenceItemSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionDetail;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerReadinessSummary;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.UpdateThinkerDeploymentControlRequest;
import com.ai.fabric.platform.backend.thinker.service.ThinkerResolverService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operator")
public class ThinkerResolverOperatorController {

    private final ThinkerResolverService service;

    public ThinkerResolverOperatorController(ThinkerResolverService service) {
        this.service = service;
    }

    @GetMapping("/thinker/sessions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public List<ThinkerIssueSessionSummary> listThinkerSessions(@RequestParam(required = false) String shopDomain,
                                                                @RequestParam(required = false) String deploymentId,
                                                                @RequestParam(required = false) String status) {
        return service.listOperatorSessions(shopDomain, deploymentId, status);
    }

    @PostMapping("/thinker/sessions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ThinkerIssueSessionSummary createThinkerSession(@Valid @RequestBody CreateThinkerIssueSessionRequest request) {
        return service.createIssueSession(request);
    }

    @GetMapping("/thinker/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ThinkerIssueSessionDetail getThinkerSession(@PathVariable String sessionId) {
        return service.getOperatorSession(sessionId);
    }

    @GetMapping("/thinker/sessions/{sessionId}/evidence")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public List<ThinkerEvidenceItemSummary> getThinkerEvidence(@PathVariable String sessionId) {
        return service.getOperatorEvidence(sessionId);
    }

    @GetMapping("/thinker/sessions/{sessionId}/export")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ResponseEntity<byte[]> exportThinkerSession(@PathVariable String sessionId) {
        byte[] body = service.exportOperatorSession(sessionId);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_MARKDOWN)
            .header("Content-Disposition", ContentDisposition.attachment()
                .filename("thinker-session-" + sessionId + ".md")
                .build()
                .toString())
            .body(body);
    }

    @GetMapping("/thinker/deployments/{deploymentId}/control")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ThinkerDeploymentControlSummary getControl(@PathVariable String deploymentId) {
        return service.getControl(deploymentId);
    }

    @PutMapping("/thinker/deployments/{deploymentId}/control")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ThinkerDeploymentControlSummary updateControl(@PathVariable String deploymentId,
                                                         @RequestBody(required = false) UpdateThinkerDeploymentControlRequest request) {
        return service.updateControl(deploymentId, request == null
            ? new UpdateThinkerDeploymentControlRequest(null, null, null, null)
            : request);
    }

    @GetMapping("/thinker/readiness")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ThinkerReadinessSummary readiness() {
        return service.readiness();
    }

    @GetMapping("/resolver/proposals")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public List<ResolverIntentProposalSummary> listProposals() {
        return service.listProposals();
    }

    @PostMapping("/resolver/proposals")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResolverIntentProposalSummary createProposal(@Valid @RequestBody CreateResolverProposalRequest request) {
        return service.createProposal(request);
    }

    @GetMapping("/resolver/policy-decisions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public List<ResolverPolicyDecisionSummary> listPolicyDecisions() {
        return service.listPolicyDecisions();
    }

    @PostMapping("/resolver/proposals/{proposalId}/dry-run")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResolverDryRunSummary dryRun(@PathVariable String proposalId) {
        return service.runDryRun(proposalId);
    }

    @PostMapping("/resolver/proposals/{proposalId}/execute")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResolverExecutionSummary execute(@PathVariable String proposalId,
                                            @Valid @RequestBody ExecuteResolverProposalRequest request) {
        return service.executeProposal(proposalId, request);
    }

    @GetMapping("/resolver/executions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public List<ResolverExecutionSummary> listExecutions() {
        return service.listExecutions();
    }
}
