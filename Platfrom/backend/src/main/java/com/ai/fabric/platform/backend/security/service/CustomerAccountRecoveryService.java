package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class CustomerAccountRecoveryService {
    private static final String NEW_LINK = " Request a new email link before trying again; this link may already have been used."
        + " If a fresh email link still fails, contact your workspace administrator.";
    private final CustomerSupabaseAuthProperties properties;
    private final CustomerSupabaseIdentityService identities;
    private final PlatformUserRepository users;
    private final PlatformCustomerIdentityLinkRepository links;
    private final PlatformAuditService audit;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public CustomerAccountRecoveryService(CustomerSupabaseAuthProperties properties, CustomerSupabaseIdentityService identities,
        PlatformUserRepository users, PlatformCustomerIdentityLinkRepository links, PlatformAuditService audit,
        EntityManager entityManager, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.properties = properties; this.identities = identities; this.users = users; this.links = links;
        this.audit = audit; this.entityManager = entityManager; this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public RecoverySession recover(String tokenHash, String email) {
        if (!properties.enabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Customer account recovery is not configured.");
        String expectedEmail = normalizedEmail(email);
        if (tokenHash == null || tokenHash.isBlank() || tokenHash.length() > 1024 || expectedEmail == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A recovery token and the existing Console email are required.");
        }
        try {
            JsonNode session = exchange(tokenHash);
            String accessToken = token(session, "access_token"), refreshToken = token(session, "refresh_token");
            var identity = identities.verifyRecoveryIdentity(accessToken);
            // Entered email confirms intent; only a consumed provider email token grants mailbox proof.
            if (!expectedEmail.equals(normalizedEmail(identity.email()))) throw new ResponseStatusException(UNAUTHORIZED);
            transaction.executeWithoutResult(status -> connect(identity));
            return new RecoverySession(accessToken, refreshToken, identity.expiresAt().getEpochSecond(), identity.subject());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Account recovery is unavailable." + NEW_LINK);
        } catch (IOException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Account recovery is unavailable." + NEW_LINK);
        } catch (ResponseStatusException ex) {
            // Do not expose provider bodies or identity data, including on a consumed-token failure.
            throw new ResponseStatusException(ex.getStatusCode(), "Account recovery was not completed." + NEW_LINK);
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException ex) {
            throw new ResponseStatusException(CONFLICT, "Account recovery was not completed." + NEW_LINK);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Account recovery was not completed." + NEW_LINK);
        }
    }

    private JsonNode exchange(String tokenHash) throws IOException, InterruptedException {
        var body = mapper.createObjectNode().put("token_hash", tokenHash).put("type", "email");
        var request = HttpRequest.newBuilder(URI.create(properties.projectUrl().replaceAll("/+$", "") + "/auth/v1/verify"))
            .timeout(Duration.ofSeconds(5)).header("apikey", properties.publicKey()).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 429) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        if (response.statusCode() != 200) throw new ResponseStatusException(SERVICE_UNAVAILABLE);
        return mapper.readTree(response.body());
    }

    private String token(JsonNode session, String name) {
        JsonNode value = session == null ? null : session.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 16384) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE);
        }
        return value.asText();
    }

    private void connect(CustomerSupabaseIdentityService.RecoveryIdentity identity) {
        var user = users.findByEmailForRecovery(identity.email()).orElseThrow(this::refused);
        // A cookie filter may have loaded this row before the lock; refresh under the acquired lock.
        entityManager.refresh(user);
        if (!"ACTIVE".equals(user.getStatus()) || !PlatformRole.CUSTOMER_ADMIN.name().equals(user.getRole())
            || !identity.email().equalsIgnoreCase(user.getEmail()) || user.getCustomerId() == null) throw refused();
        var customer = entityManager.find(PlatformCustomerEntity.class, user.getCustomerId(), LockModeType.PESSIMISTIC_READ);
        if (customer == null) throw refused();
        entityManager.refresh(customer);
        if (!"ACTIVE".equals(customer.getStatus())) throw refused();

        var subjectLink = links.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
        var userLink = links.findByPlatformUserId(user.getId()).orElse(null);
        if (subjectLink != null || userLink != null) {
            if (subjectLink != null && userLink != null && subjectLink.getId().equals(userLink.getId())
                && subjectLink.getRevokedAt() == null && user.getId().equals(subjectLink.getPlatformUserId())) return;
            throw new ResponseStatusException(CONFLICT);
        }
        var link = new PlatformCustomerIdentityLinkEntity();
        link.setId("cil-" + UUID.randomUUID()); link.setIssuer(identity.issuer()); link.setSubject(identity.subject());
        link.setPlatformUserId(user.getId()); link.setCreatedAt(Instant.now());
        links.saveAndFlush(link);
        audit.record("CUSTOMER_IDENTITY_RECOVERED", "PLATFORM_USER", user.getId(),
            Map.of("identityLinkId", link.getId(), "customerId", customer.getId(), "proof", "SUPABASE_EMAIL_TOKEN"));
    }

    private String normalizedEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 255 || !email.contains("@")) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException refused() { return new ResponseStatusException(FORBIDDEN); }

    public record RecoverySession(String accessToken, String refreshToken, long expiresAt, String userId) {
        @Override public String toString() { return "RecoverySession[REDACTED]"; }
    }
}
