package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.security.service.CustomerStaffGrantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/staff-access")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerStaffAccessController {
    private final CustomerStaffGrantService service;
    public CustomerStaffAccessController(CustomerStaffGrantService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<CustomerStaffGrantService.Summary>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
    }
    @PostMapping(consumes = "application/json")
    public ResponseEntity<CustomerStaffGrantService.Summary> grant(@Valid @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.grant(request.email()));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<CustomerStaffGrantService.Summary> revoke(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.revoke(id));
    }

    public record Request(@Email @NotBlank @Size(max = 255) String email) { }
}
