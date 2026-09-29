package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class InternalDealershipController {

    private final VehicleRepository vehicles;
    private final LeadService leads;
    private final DealershipDemoProperties properties;

    public InternalDealershipController(VehicleRepository vehicles,
                                        LeadService leads,
                                        DealershipDemoProperties properties) {
        this.vehicles = vehicles;
        this.leads = leads;
        this.properties = properties;
    }

    @PostMapping("/authz/check")
    public Map<String, Object> authorize(@RequestBody JsonNode request) {
        JsonNode auth = request.path("authContext");
        String deploymentId = text(auth, "deploymentId");
        String tenantId = text(auth, "tenantId");
        String subjectId = text(auth, "subjectId");
        String resourceId = text(request, "resourceId");
        String operation = text(request, "operationType");

        boolean boundary = StringUtils.hasText(subjectId)
            && equalsConfigured(properties.getRuntime().getPrivateAccess().getDeploymentId(), deploymentId)
            && equalsConfigured(properties.getRuntime().getPrivateAccess().getTenantId(), tenantId);
        boolean supportedOperation = operation != null && List.of(
            "READ", "RETRIEVE", "SEARCH", "EXECUTE_ACTION", "CREATE_LEAD", "REQUEST_TEST_DRIVE", "REQUEST_CALLBACK"
        ).contains(operation.toUpperCase());
        String vehicleId = normalizeVehicleId(resourceId);
        boolean inventorySearch = "inventory-search".equals(resourceId)
            && operation != null
            && List.of("READ", "RETRIEVE", "SEARCH", "EXECUTE_ACTION").contains(operation.toUpperCase());
        boolean activeTarget = inventorySearch
            || (StringUtils.hasText(vehicleId) && vehicles.findActiveById(vehicleId).isPresent());
        boolean granted = boundary && supportedOperation && activeTarget;

        return Map.of(
            "granted", granted,
            "reason", granted ? "Dealership deployment, tenant and active stock target verified."
                : "The request did not satisfy the dealership boundary.",
            "policyVersion", "dealership-demo-v1"
        );
    }

    @PostMapping("/actions/execute")
    public ResponseEntity<?> execute(@RequestBody ConnectorActionRequest request) {
        Map<String, Object> params = request.params() == null ? Map.of() : request.params();
        LeadService.VerifiedAuthContext auth = request.trace() == null ? null : request.trace().authContext();
        LeadService.LeadReceipt receipt = leads.execute(new LeadService.ActionExecutionRequest(
            request.actionId(), request.idempotencyKey(), booleanValue(params.get("confirmationAccepted")),
            string(params.get("vehicleId")), string(params.get("name")), string(params.get("email")),
            string(params.get("phone")), string(params.get("preferredDate")), string(params.get("message")),
            booleanValue(params.get("consent")), auth
        ));
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", receipt.message(),
            "data", receipt
        ));
    }

    private String normalizeVehicleId(String resourceId) {
        if (!StringUtils.hasText(resourceId)) {
            return null;
        }
        String normalized = resourceId.trim();
        int separator = normalized.lastIndexOf(':');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private boolean equalsConfigured(String expected, String actual) {
        return StringUtils.hasText(expected) && expected.trim().equals(actual);
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())
            ? null : value.asText().trim();
    }

    public record ConnectorActionRequest(String actionId, Map<String, Object> params,
                                         String idempotencyKey, ConnectorTrace trace) { }
    public record ConnectorTrace(String requestId, String conversationId,
                                 LeadService.VerifiedAuthContext authContext) { }
}
