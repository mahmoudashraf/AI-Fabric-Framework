package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

@Component
class CustomerSourceConnectorPrerequisiteProbe {
    private final PlatformSecretService secrets;
    private final RuntimePrivateAssertionSigningService assertions;
    private final DeploymentRuntimeJsonTransport transport;
    private final AdmissionJson json;
    private final Clock clock;

    @Autowired
    CustomerSourceConnectorPrerequisiteProbe(PlatformSecretService secrets,
        RuntimePrivateAssertionSigningService assertions, DeploymentRuntimeJsonTransport transport, AdmissionJson json) {
        this(secrets, assertions, transport, json, Clock.systemUTC());
    }

    CustomerSourceConnectorPrerequisiteProbe(PlatformSecretService secrets,
        RuntimePrivateAssertionSigningService assertions, DeploymentRuntimeJsonTransport transport, AdmissionJson json, Clock clock) {
        this.secrets = secrets;
        this.assertions = assertions;
        this.transport = transport;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NEVER)
    public Proof probe(DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Source connector probes cannot run inside a transaction.");
        }
        var scope = snapshot.scope();
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || !assertions.isConfigured()) throw failure();
        String backendKey = secrets.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME);
        if (backendKey == null || backendKey.isBlank()) throw failure();
        String expectedDigest = expectedScopeDigest(snapshot);
        var claims = new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
            principal.actorId(), "INTERNAL_PLATFORM_USER", "PRIVATE_RUNTIME_BACKEND_MEDIATED", "TRUSTED_BACKEND",
            "source-prerequisite-" + scope.releaseId(), scope.deploymentId(), scope.customerId(), scope.tenantId(),
            RuntimePrivateAccessSupport.ISSUER_PLATFORM_DOCUMENT_OPERATIONS, clock.instant().plusSeconds(60),
            List.of(scope.deploymentId()), List.of("documents:read"));
        var response = transport.send(snapshot.runtime().runtimeBaseUrl(), "GET", "/api/documents/source-connector/status",
            Map.of(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, backendKey.trim(),
                RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER, assertions.toAuthorizationHeaderValue(claims)),
            null, null);
        var body = response.body();
        if (response.statusCode() != 200 || body == null || !body.path("ready").isBoolean() || !body.path("ready").asBoolean()
            || !snapshot.connectorType().equals(body.path("connectorType").asText())
            || !snapshot.bindingRef().equals(body.path("bindingRef").asText())
            || !expectedDigest.equals(body.path("scopeDigest").asText())
            || (body.hasNonNull("errorCode") && !body.path("errorCode").asText().isBlank())) throw failure();
        Instant checkedAt;
        try { checkedAt = Instant.parse(body.path("checkedAt").asText()).truncatedTo(ChronoUnit.MICROS); }
        catch (RuntimeException ex) { throw failure(); }
        Instant now = clock.instant();
        if (checkedAt.isAfter(now) || checkedAt.isBefore(now.minusSeconds(snapshot.maxAgeSeconds()))) throw failure();
        return new Proof(json.fingerprint(body), checkedAt);
    }

    private String expectedScopeDigest(DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot) {
        String key = DeploymentDocumentStorageBindingService.secretKey(snapshot.scope().deploymentId(), snapshot.scope().targetProfileId());
        String value;
        if ("S3_COMPATIBLE_OBJECT_STORAGE".equals(snapshot.connectorType())) {
            String bucket = secrets.resolveSecret(key + "_BUCKET");
            String prefix = secrets.resolveSecret(key + "_PREFIX");
            if (bucket == null || bucket.isBlank()) throw failure();
            value = bucket + "|" + (prefix == null ? "" : prefix);
        } else if ("MOUNTED_FOLDER".equals(snapshot.connectorType())) {
            value = secrets.resolveSecret(key + "_MOUNTED_ROOT");
            if (value == null || value.isBlank()) throw failure();
        } else throw failure();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    record Proof(String digest, Instant checkedAt) { }
    private AdmissionFailure failure() { return new AdmissionFailure(PRECONDITION_FAILED, "SOURCE_CONNECTOR_PROBE_FAILED"); }
}
