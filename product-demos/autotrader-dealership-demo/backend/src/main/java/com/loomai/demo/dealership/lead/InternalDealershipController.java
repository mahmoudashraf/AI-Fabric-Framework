package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class InternalDealershipController {

    private final ProviderStockClient stockClient;
    private final LeadService leads;
    private final DealershipDemoProperties properties;

    public InternalDealershipController(ProviderStockClient stockClient,
                                        LeadService leads,
                                        DealershipDemoProperties properties) {
        this.stockClient = stockClient;
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
        String vehicleReference = normalizeVehicleReference(resourceId);
        boolean inventorySearch = "inventory-search".equals(resourceId)
            && operation != null
            && List.of("READ", "RETRIEVE", "SEARCH", "EXECUTE_ACTION").contains(operation.toUpperCase());
        boolean orchestrationEntry = "rag:intent".equals(resourceId)
            && "READ".equalsIgnoreCase(operation);
        boolean granted = boundary && supportedOperation
            && (orchestrationEntry || inventorySearch || resolvesToOneActiveVehicle(vehicleReference));

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
            string(params.get("vehicleReference")), string(params.get("name")), string(params.get("email")),
            string(params.get("phone")), string(params.get("preferredDate")), string(params.get("message")),
            booleanValue(params.get("consent")), auth
        ));
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", receipt.message(),
            "data", receipt
        ));
    }

    private boolean resolvesToOneActiveVehicle(String reference) {
        try {
            stockClient.requireAvailableStock(reference);
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().equals(HttpStatus.BAD_REQUEST) || ex.getStatusCode().equals(HttpStatus.CONFLICT)) return false;
            throw ex;
        }
    }

    private String normalizeVehicleReference(String resourceId) {
        if (!StringUtils.hasText(resourceId)) {
            return null;
        }
        String normalized = resourceId.trim();
        String prefix = properties.getRuntime().getInventoryVectorSpace() + ":";
        return normalized.startsWith(prefix) ? normalized.substring(prefix.length()) : normalized;
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
