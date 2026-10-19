package com.ai.fabric.platform.backend.externalintegration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;

@RestController
@RequestMapping("/api/customer-self-service")
public class ExternalIntegrationController {
    private final ExternalIntegrationService service;
    public ExternalIntegrationController(ExternalIntegrationService service) { this.service = service; }

    @PostMapping("/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Summary approve(@PathVariable String offerId, @PathVariable String deploymentId, @Valid @RequestBody ApprovalRequest request) {
        return service.approve(offerId, deploymentId, request);
    }
    @PostMapping("/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public Summary bind(@PathVariable String offerId, @PathVariable String deploymentId, @Valid @RequestBody BindRequest request) {
        return service.bind(offerId, deploymentId, request);
    }
    @PostMapping("/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/revoke")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public Summary revoke(@PathVariable String offerId, @PathVariable String deploymentId, @Valid @RequestBody RevokeRequest request) {
        return service.revoke(offerId, deploymentId, request);
    }
    @GetMapping("/catalogue/{offerId}/deployments/{deploymentId}/external-integrations")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public Summary read(@PathVariable String offerId, @PathVariable String deploymentId, @RequestParam String versionId,
                        @RequestParam String targetProfileId, @RequestParam String bindingSlotId, @RequestParam String selectedProfileId) {
        return service.read(offerId, deploymentId, versionId, targetProfileId, bindingSlotId, selectedProfileId);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> malformedBody() { return Map.of("code", "EXTERNAL_INTEGRATION_INVALID_JSON"); }
}
