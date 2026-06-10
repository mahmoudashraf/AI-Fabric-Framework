package com.ai.fabric.platform.backend.web;

import com.ai.fabric.platform.backend.model.PlatformCoreServiceActionSummary;
import com.ai.fabric.platform.backend.model.PlatformCoreServiceSummary;
import com.ai.fabric.platform.backend.service.PlatformCoreServiceOperationsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/core-services")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformCoreServiceController {

    private final PlatformCoreServiceOperationsService serviceOperationsService;

    public PlatformCoreServiceController(PlatformCoreServiceOperationsService serviceOperationsService) {
        this.serviceOperationsService = serviceOperationsService;
    }

    @GetMapping
    public List<PlatformCoreServiceSummary> list() {
        return serviceOperationsService.listServices();
    }

    @GetMapping("/{serviceRef}")
    public PlatformCoreServiceSummary get(@PathVariable String serviceRef) {
        return serviceOperationsService.getService(serviceRef);
    }

    @PostMapping("/{serviceRef}/deploy")
    public PlatformCoreServiceActionSummary deploy(@PathVariable String serviceRef) {
        return serviceOperationsService.deploy(serviceRef);
    }

    @PostMapping("/{serviceRef}/restart")
    public PlatformCoreServiceActionSummary restart(@PathVariable String serviceRef) {
        return serviceOperationsService.restart(serviceRef);
    }
}
