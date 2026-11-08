package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.marketplace.model.CustomerPluginLibraryContracts.*;
import com.ai.fabric.platform.backend.marketplace.service.CustomerPluginLibraryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/plugin-library")
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class CustomerPluginLibraryController {
    private final CustomerPluginLibraryService library;
    public CustomerPluginLibraryController(CustomerPluginLibraryService library) { this.library = library; }

    @GetMapping
    public Library list(@RequestParam(required = false) String customerId) { return library.list(customerId); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Summary create(@RequestParam(required = false) String customerId, @RequestBody Create request) { return library.create(customerId, request); }
    @GetMapping("/{id}")
    public Summary get(@PathVariable String id, @RequestParam(required = false) String customerId) { return library.get(id, customerId); }
    @PutMapping("/{id}")
    public Summary update(@PathVariable String id, @RequestParam(required = false) String customerId, @RequestBody Save request) { return library.update(id, customerId, request); }
    @PostMapping("/{id}/check")
    public Check check(@PathVariable String id, @RequestParam(required = false) String customerId, @RequestBody Save request) { return library.check(id, customerId, request); }
    @GetMapping("/{id}/versions")
    public List<Version> versions(@PathVariable String id, @RequestParam(required = false) String customerId) { return library.versions(id, customerId); }
    @GetMapping("/{id}/versions/{version}")
    public Version version(@PathVariable String id, @PathVariable String version, @RequestParam(required = false) String customerId) { return library.version(id, version, customerId); }
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public Summary createVersion(@PathVariable String id, @RequestParam(required = false) String customerId, @RequestBody Save request) { return library.createVersion(id, customerId, request); }
    @PostMapping("/{id}/archive")
    public Summary archive(@PathVariable String id, @RequestParam(required = false) String customerId, @RequestBody Revision request) { return library.archive(id, customerId, request); }
}
