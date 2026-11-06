package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.security.service.StaffCustomerAccessService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/staff/customers/{customerId}/access")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class StaffCustomerAccessController {
    private final StaffCustomerAccessService service;

    public StaffCustomerAccessController(StaffCustomerAccessService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<StaffCustomerAccessService.PreparedAccess> prepare(@PathVariable String customerId,
        @Valid @RequestBody StaffCustomerAccessService.Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.prepare(customerId, request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> concurrentAccount() {
        return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(Map.of("message",
            "An account was just created for this email. Retry to check its workspace. No existing access was changed."));
    }
}
