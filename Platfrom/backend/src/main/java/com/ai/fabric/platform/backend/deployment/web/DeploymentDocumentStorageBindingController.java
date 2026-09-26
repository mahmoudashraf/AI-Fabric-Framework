package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/document-storage-bindings")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentDocumentStorageBindingController {

    private final DeploymentDocumentStorageBindingService service;

    public DeploymentDocumentStorageBindingController(DeploymentDocumentStorageBindingService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentStorageBindingSummary> list(@PathVariable String deploymentId) {
        return service.list(deploymentId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CUSTOMER_ADMIN')")
    public DocumentStorageBindingSummary upsert(
        @PathVariable String deploymentId,
        @Valid @RequestBody UpsertDocumentStorageBindingRequest request
    ) {
        return service.upsert(deploymentId, request);
    }

    @DeleteMapping("/{bindingRef}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CUSTOMER_ADMIN')")
    public void remove(@PathVariable String deploymentId, @PathVariable String bindingRef) {
        service.remove(deploymentId, bindingRef);
    }
}
