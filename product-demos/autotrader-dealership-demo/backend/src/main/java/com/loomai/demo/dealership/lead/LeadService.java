package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.inventory.Vehicle;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LeadService {

    private static final String CALLBACK_ACTION = "dealership_request_callback";
    private static final String TEST_DRIVE_ACTION = "dealership_request_test_drive";
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
        CALLBACK_ACTION,
        TEST_DRIVE_ACTION
    );
    private static final Set<String> STATUSES = Set.of("NEW", "CONTACTED", "COMPLETED", "CANCELLED");

    private final LeadRepository repository;
    private final VehicleRepository vehicles;
    private final PiiEncryptionService encryption;
    private final DealershipDemoProperties properties;

    public LeadService(LeadRepository repository,
                       VehicleRepository vehicles,
                       PiiEncryptionService encryption,
                       DealershipDemoProperties properties) {
        this.repository = repository;
        this.vehicles = vehicles;
        this.encryption = encryption;
        this.properties = properties;
    }

    @Transactional
    public LeadReceipt execute(ActionExecutionRequest request) {
        if (request == null || !SUPPORTED_ACTIONS.contains(trim(request.actionId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action is not supported.");
        }
        String idempotencyKey = require(request.idempotencyKey(), "idempotencyKey", 180);
        var existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return receipt(existing.get(), vehicles.findActiveById(existing.get().vehicleId()).orElse(null));
        }
        if (!request.confirmationAccepted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed action evidence is required.");
        }
        validateBoundary(request.authContext());
        String vehicleId = require(request.vehicleId(), "vehicleId", 80);
        Vehicle vehicle = vehicles.findActiveById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The selected vehicle is no longer active."));
        if (CALLBACK_ACTION.equals(request.actionId().trim()) && !request.consent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact consent is required.");
        }
        String email = trim(request.email());
        String phone = trim(request.phone());
        if (!StringUtils.hasText(email) && !StringUtils.hasText(phone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An email address or telephone number is required.");
        }
        if (StringUtils.hasText(email) && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address format is invalid.");
        }

        Map<String, Object> contact = new LinkedHashMap<>();
        put(contact, "name", bounded(request.name(), 120));
        put(contact, "email", bounded(email, 200));
        put(contact, "phone", bounded(phone, 80));
        put(contact, "preferredDate", bounded(request.preferredDate(), 40));
        put(contact, "message", bounded(request.message(), 500));

        Instant now = Instant.now();
        LeadRepository.LeadRecord created = repository.insert(new LeadRepository.LeadRecord(
            UUID.randomUUID().toString(), idempotencyKey, request.actionId().trim(), vehicle.id(),
            encryption.encrypt(contact), "NEW", request.consent(), bounded(request.authContext().sessionId(), 160),
            "NFM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), now, now
        ));
        return receipt(created, vehicle);
    }

    public List<LeadSummary> latest(int limit) {
        return repository.latest(limit).stream()
            .map(record -> summary(record, vehicles.findActiveById(record.vehicleId()).orElse(null)))
            .toList();
    }

    public LeadDetail detail(String id) {
        LeadRepository.LeadRecord lead = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead request was not found."));
        return new LeadDetail(
            summary(lead, vehicles.findActiveById(lead.vehicleId()).orElse(null)),
            encryption.decrypt(lead.encryptedContact())
        );
    }

    public LeadSummary updateStatus(String id, String status) {
        String normalized = trim(status) == null ? "" : trim(status).toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead status is invalid.");
        }
        LeadRepository.LeadRecord existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead request was not found."));
        repository.updateStatus(id, normalized);
        return summary(repository.findById(id).orElse(existing), vehicles.findActiveById(existing.vehicleId()).orElse(null));
    }

    @Scheduled(cron = "${APP_LEAD_RETENTION_CRON:0 20 3 * * *}")
    public void purgeExpired() {
        repository.deleteOlderThan(Instant.now().minus(properties.getPrivacy().getLeadRetention()));
    }

    private void validateBoundary(VerifiedAuthContext context) {
        var expected = properties.getRuntime().getPrivateAccess();
        if (context == null || !StringUtils.hasText(context.subjectId()) || !StringUtils.hasText(context.sessionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verified runtime session context is required.");
        }
        if (!matches(expected.getDeploymentId(), context.deploymentId())
            || !matches(expected.getTenantId(), context.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Runtime identity does not own this dealership boundary.");
        }
    }

    private boolean matches(String expected, String actual) {
        return StringUtils.hasText(expected) && expected.trim().equals(trim(actual));
    }

    private LeadReceipt receipt(LeadRepository.LeadRecord record, Vehicle vehicle) {
        return new LeadReceipt(
            record.receiptCode(), record.actionType(), record.status(), record.createdAt(),
            vehicle == null ? record.vehicleId() : vehicle.displayName(),
            "Your request is in the dealership review inbox. A team member will use the contact details you confirmed."
        );
    }

    private LeadSummary summary(LeadRepository.LeadRecord record, Vehicle vehicle) {
        return new LeadSummary(
            record.id(), record.receiptCode(), record.actionType(), record.status(), record.vehicleId(),
            vehicle == null ? "Unavailable vehicle" : vehicle.displayName(), record.createdAt(), record.updatedAt()
        );
    }

    private String require(String value, String field, int max) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized) || normalized.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required and must be at most " + max + " characters.");
        }
        return normalized;
    }

    private String bounded(String value, int max) {
        String normalized = trim(value);
        return normalized == null ? null : normalized.substring(0, Math.min(max, normalized.length()));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    public record VerifiedAuthContext(String subjectId, String subjectType, String authMode, String callerType,
                                      String sessionId, String deploymentId, String customerId, String tenantId,
                                      String issuer, List<String> grantedScopes) { }

    public record ActionExecutionRequest(String actionId, String idempotencyKey, boolean confirmationAccepted,
                                         String vehicleId, String name, String email, String phone,
                                         String preferredDate, String message, boolean consent,
                                         VerifiedAuthContext authContext) { }

    public record LeadReceipt(String receiptCode, String actionType, String status, Instant createdAt,
                              String vehicle, String message) { }
    public record LeadSummary(String id, String receiptCode, String actionType, String status, String vehicleId,
                              String vehicle, Instant createdAt, Instant updatedAt) { }
    public record LeadDetail(LeadSummary lead, Map<String, Object> contact) { }
}
