package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationReleaseGateSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteRunSummary;
import com.ai.fabric.platform.backend.deployment.service.PlatformVerificationSuiteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/verification-suites")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformVerificationSuiteController {

    private final PlatformVerificationSuiteService platformVerificationSuiteService;

    public PlatformVerificationSuiteController(PlatformVerificationSuiteService platformVerificationSuiteService) {
        this.platformVerificationSuiteService = platformVerificationSuiteService;
    }

    @GetMapping
    public List<PlatformVerificationSuiteDefinitionSummary> listDefinitions() {
        return platformVerificationSuiteService.listDefinitions();
    }

    @GetMapping("/runs")
    public List<PlatformVerificationSuiteRunSummary> listRuns() {
        return platformVerificationSuiteService.listRuns();
    }

    @GetMapping("/runs/{runId}")
    public PlatformVerificationSuiteRunSummary getRun(@PathVariable String runId) {
        return platformVerificationSuiteService.getRun(runId);
    }

    @PostMapping("/runs/{runId}/cancel")
    public PlatformVerificationSuiteRunSummary cancelRun(@PathVariable String runId) {
        return platformVerificationSuiteService.cancelRun(runId);
    }

    @GetMapping("/release-gate")
    public PlatformVerificationReleaseGateSummary releaseGate() {
        return platformVerificationSuiteService.getReleaseGate();
    }

    @PostMapping("/{suiteKey}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformVerificationSuiteDispatchSummary dispatch(@PathVariable String suiteKey,
                                                             @RequestBody(required = false) PlatformVerificationSuiteDispatchRequest request) {
        return platformVerificationSuiteService.dispatch(suiteKey, request);
    }
}
