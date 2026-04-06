package com.ai.fabric.platform.backend.vectorization.web;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationExecutionBundleSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerCheckpointRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerClaimSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerCompletionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerDiscoveryReportRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerHeartbeatRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerHeartbeatSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSessionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSessionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationRunnerService;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vectorization/runner")
public class VectorizationRunnerController {

    private final VectorizationRunnerService vectorizationRunnerService;
    private final VectorizationService vectorizationService;

    public VectorizationRunnerController(VectorizationRunnerService vectorizationRunnerService,
                                         VectorizationService vectorizationService) {
        this.vectorizationRunnerService = vectorizationRunnerService;
        this.vectorizationService = vectorizationService;
    }

    @PostMapping("/session")
    @ResponseStatus(HttpStatus.CREATED)
    public VectorizationRunnerSessionSummary createSession(@Valid @RequestBody VectorizationRunnerSessionRequest request) {
        return vectorizationRunnerService.createSession(request);
    }

    @PostMapping("/claim")
    public VectorizationRunnerClaimSummary claim(@RequestParam String sessionToken) {
        return vectorizationRunnerService.claimNextRun(sessionToken);
    }

    @GetMapping("/runs/{runId}/bundle")
    public VectorizationExecutionBundleSummary bundle(@PathVariable String runId, @RequestParam String sessionToken) {
        return vectorizationRunnerService.fetchExecutionBundle(sessionToken, runId);
    }

    @PostMapping("/heartbeat")
    public VectorizationRunnerHeartbeatSummary heartbeat(@Valid @RequestBody VectorizationRunnerHeartbeatRequest request) {
        return vectorizationRunnerService.heartbeat(request);
    }

    @PostMapping("/checkpoint")
    public VectorizationRunSummary checkpoint(@Valid @RequestBody VectorizationRunnerCheckpointRequest request) {
        return vectorizationRunnerService.reportCheckpoint(request);
    }

    @PostMapping("/complete")
    public VectorizationRunSummary complete(@Valid @RequestBody VectorizationRunnerCompletionRequest request) {
        return vectorizationRunnerService.completeRun(request);
    }

    @PostMapping("/discovery")
    public VectorizationSourceConnectionSummary reportDiscovery(@Valid @RequestBody VectorizationRunnerDiscoveryReportRequest request) {
        VectorizationSourceConnectionEntity entity = vectorizationRunnerService.reportDiscovery(request);
        return vectorizationService.summarizeConnection(entity);
    }
}
