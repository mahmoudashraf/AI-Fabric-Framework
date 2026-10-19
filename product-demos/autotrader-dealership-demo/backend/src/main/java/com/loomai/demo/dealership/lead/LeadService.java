package com.loomai.demo.dealership.lead;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String STOCK_SNAPSHOT = "_providerStock";

    private final LeadRepository repository;
    private final ProviderStockClient stockClient;
    private final PiiEncryptionService encryption;
    private final DealershipDemoProperties properties;

    public LeadService(LeadRepository repository,
                       ProviderStockClient stockClient,
                       PiiEncryptionService encryption,
                       DealershipDemoProperties properties) {
        this.repository = repository;
        this.stockClient = stockClient;
        this.encryption = encryption;
        this.properties = properties;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeadReceipt execute(ActionExecutionRequest request) {
        if (request == null || !SUPPORTED_ACTIONS.contains(trim(request.actionId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action is not supported.");
        }
        String idempotencyKey = require(request.idempotencyKey(), "idempotencyKey", 180);
        if (!request.confirmationAccepted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed action evidence is required.");
        }
        validateBoundary(request.authContext());
        String sessionId = require(request.authContext().sessionId(), "sessionId", 160);
        String vehicleReference = ProviderStockClient.canonicalStockId(request.vehicleReference());
        if (CALLBACK_ACTION.equals(request.actionId().trim()) && !request.consent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact consent is required.");
        }
        String name = require(request.name(), "name", 120);
        String email = TEST_DRIVE_ACTION.equals(request.actionId().trim())
            ? require(request.email(), "email", 200)
            : trim(request.email());
        String phone = TEST_DRIVE_ACTION.equals(request.actionId().trim())
            ? require(request.phone(), "phone", 80)
            : trim(request.phone());
        if (CALLBACK_ACTION.equals(request.actionId().trim())
            && !StringUtils.hasText(email) && !StringUtils.hasText(phone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An email address or telephone number is required.");
        }
        if (StringUtils.hasText(email) && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address format is invalid.");
        }

        Map<String, Object> contact = new LinkedHashMap<>();
        put(contact, "name", name);
        put(contact, "email", optionalBounded(email, 200));
        put(contact, "phone", optionalBounded(phone, 80));
        put(contact, "preferredDate", optionalBounded(request.preferredDate(), 40));
        put(contact, "message", optionalBounded(request.message(), 500));

        java.util.Optional<LeadRepository.LeadRecord> existing;
        try {
            existing = repository.findByIdempotencyKey(idempotencyKey);
        } catch (DataAccessException ex) {
            throw storeUnavailable();
        }
        if (existing.isPresent()) {
            return confirmedReceipt(existing.get(), request, vehicleReference, sessionId, contact);
        }

        ProviderStockClient.Stock stock = stockClient.requireAvailableStock(vehicleReference);
        Map<String, Object> storedContact = new LinkedHashMap<>(contact);
        storedContact.put(STOCK_SNAPSHOT, Map.of(
            "stockId", stock.stockId(), "advertiserId", stock.advertiserId(), "displayName", stock.displayName(),
            "providerUpdatedAt", stock.providerUpdatedAt().toString(), "validatedAt", stock.validatedAt().toString()
        ));
        Instant now = Instant.now();
        try {
            LeadRepository.LeadRecord created = repository.insert(new LeadRepository.LeadRecord(
                UUID.randomUUID().toString(), idempotencyKey, request.actionId().trim(), stock.stockId(),
                encryption.encrypt(storedContact), "NEW", request.consent(), sessionId,
                "NFM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), now, now,
                stock.stockId(), stock.advertiserId()
            ));
            return confirmedReceipt(created, request, vehicleReference, sessionId, contact);
        } catch (DataAccessException ex) {
            throw storeUnavailable();
        }
    }

    public List<LeadSummary> latest(int limit) {
        return repository.latest(limit).stream()
            .map(this::summary)
            .toList();
    }

    public LeadDetail detail(String id) {
        LeadRepository.LeadRecord lead = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead request was not found."));
        return new LeadDetail(
            summary(lead), contact(lead)
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
        return summary(repository.findById(id).orElse(existing));
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
            || !matches(expected.getTenantId(), context.tenantId())
            || !matches(expected.getCustomerId(), context.customerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Runtime identity does not own this dealership boundary.");
        }
    }

    private boolean matches(String expected, String actual) {
        return StringUtils.hasText(expected) && expected.trim().equals(trim(actual));
    }

    private LeadReceipt confirmedReceipt(LeadRepository.LeadRecord record, ActionExecutionRequest request,
                                         String vehicleReference, String sessionId, Map<String, Object> expectedContact) {
        if (!request.actionId().trim().equals(record.actionType())
            || !vehicleReference.equals(record.vehicleId())
            || (record.providerStockId() != null && !vehicleReference.equals(record.providerStockId()))
            || (record.providerAdvertiserId() != null
                && !record.providerAdvertiserId().equals(stockClient.configuredAdvertiserId()))
            || !Objects.equals(sessionId, record.sourceSessionId())
            || request.consent() != record.consentRecorded() || !expectedContact.equals(contact(record))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The idempotency key belongs to a different confirmed request.");
        }
        return receipt(record);
    }

    private LeadReceipt receipt(LeadRepository.LeadRecord record) {
        return new LeadReceipt(
            record.receiptCode(), record.actionType(), record.status(), record.createdAt(),
            displayName(record),
            "Your request is in the dealership review inbox. A team member will use the contact details you confirmed."
        );
    }

    private LeadSummary summary(LeadRepository.LeadRecord record) {
        return new LeadSummary(
            record.id(), record.receiptCode(), record.actionType(), record.status(), record.vehicleId(),
            displayName(record), record.createdAt(), record.updatedAt()
        );
    }

    private String displayName(LeadRepository.LeadRecord record) {
        Object snapshot = encryption.decrypt(record.encryptedContact()).get(STOCK_SNAPSHOT);
        if (snapshot instanceof Map<?, ?> fields && record.vehicleId().equals(fields.get("stockId"))
            && fields.get("displayName") instanceof String name && StringUtils.hasText(name)) return name;
        return record.vehicleId();
    }

    private Map<String, Object> contact(LeadRepository.LeadRecord record) {
        Map<String, Object> contact = new LinkedHashMap<>(encryption.decrypt(record.encryptedContact()));
        contact.remove(STOCK_SNAPSHOT);
        return contact;
    }

    private ResponseStatusException storeUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The dealership lead store is temporarily unavailable.");
    }

    private String require(String value, String field, int max) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized) || normalized.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required and must be at most " + max + " characters.");
        }
        return normalized;
    }

    private String optionalBounded(String value, int max) {
        String normalized = trim(value);
        if (normalized != null && normalized.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmed request values must be at most " + max + " characters.");
        }
        return normalized;
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
                                         String vehicleReference, String name, String email, String phone,
                                         String preferredDate, String message, boolean consent,
                                         VerifiedAuthContext authContext) { }

    public record LeadReceipt(String receiptCode, String actionType, String status, Instant createdAt,
                              String vehicle, String message) { }
    public record LeadSummary(String id, String receiptCode, String actionType, String status, String vehicleId,
                              String vehicle, Instant createdAt, Instant updatedAt) { }
    public record LeadDetail(LeadSummary lead, Map<String, Object> contact) { }
}
