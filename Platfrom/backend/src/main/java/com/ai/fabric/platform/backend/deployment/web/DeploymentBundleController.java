package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleExportPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleExportSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleImportExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleImportPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentExportPreviewRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentExportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentImportPreviewRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentImportRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentBundleExportImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentBundleController {

    private final DeploymentBundleExportImportService bundleService;

    public DeploymentBundleController(DeploymentBundleExportImportService bundleService) {
        this.bundleService = bundleService;
    }

    @PostMapping("/deployments/{deploymentId}/export/preview")
    public DeploymentBundleExportPreviewSummary previewDeploymentExport(
        @PathVariable String deploymentId,
        @RequestBody(required = false) DeploymentExportPreviewRequest request
    ) {
        return bundleService.previewExport(deploymentId, request);
    }

    @PostMapping("/deployments/{deploymentId}/exports")
    public DeploymentBundleExportSummary exportDeployment(
        @PathVariable String deploymentId,
        @RequestBody(required = false) DeploymentExportRequest request
    ) {
        return bundleService.exportDeployment(deploymentId, request);
    }

    @PostMapping("/deployment-imports/preview")
    public DeploymentBundleImportPreviewSummary previewDeploymentImport(
        @RequestBody DeploymentImportPreviewRequest request
    ) {
        return bundleService.previewImport(request);
    }

    @PostMapping("/deployment-imports")
    public DeploymentBundleImportExecutionSummary importDeployment(
        @RequestBody DeploymentImportRequest request
    ) {
        return bundleService.importDeployment(request);
    }
}
