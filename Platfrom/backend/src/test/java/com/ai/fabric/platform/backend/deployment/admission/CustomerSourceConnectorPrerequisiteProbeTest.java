package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerSourceConnectorPrerequisiteProbeTest {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final DeploymentRuntimeAuthorityService runtimeAuthority = mock(DeploymentRuntimeAuthorityService.class);
    private final DeploymentRuntimeJsonTransport transport = mock(DeploymentRuntimeJsonTransport.class);
    private CustomerSourceConnectorPrerequisiteProbe probe;
    private DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot;
    private com.fasterxml.jackson.databind.node.ObjectNode response;

    @BeforeEach void setup() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("owner@example.test", PlatformRole.CUSTOMER_ADMIN, "owner@example.test", "SESSION"), null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(PlatformRole.CUSTOMER_ADMIN.authority()))));
        when(runtimeAuthority.issuePlatformProxyHeaders(any(), any(), any(), any(), any(), any()))
            .thenReturn(java.util.Map.of(
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, "backend-key",
                RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER, "Bearer target-bound-assertion"));
        String key = DeploymentDocumentStorageBindingService.secretKey("deployment", "target");
        when(secrets.resolveSecret(key + "_BUCKET")).thenReturn("private-bucket");
        when(secrets.resolveSecret(key + "_PREFIX")).thenReturn("policies/");
        var scope = new CustomerSourcePrerequisiteEvidence.Scope("customer", "tenant", "deployment", "target", "release",
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-hash", 2, "reservation", "generation", "manifest", "binding",
            "version", "config", "profile", "document-binding", 1, "credentials", "runtime");
        var release = new DeploymentReleaseEntity();
        release.setId("release");
        release.setRuntimeAuthorityFingerprint("sha256:authority");
        var runtime = new DeploymentTargetContextService.Context(new DeploymentEntity(), new DeploymentTargetProfileEntity(),
            release, new DeploymentVersionEntity(), "https://exact-runtime.example", "https://connector.example");
        snapshot = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot(scope, runtime,
            null, "S3_COMPATIBLE_OBJECT_STORAGE", "storage-handle", Set.of(Prerequisite.STORAGE), NOW.plusSeconds(60), 60);
        response = mapper.createObjectNode().put("ready", true).put("connectorType", "S3_COMPATIBLE_OBJECT_STORAGE")
            .put("bindingRef", "storage-handle").put("scopeDigest", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("private-bucket|policies/".getBytes(StandardCharsets.UTF_8))))
            .put("checkedAt", NOW.toString());
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), isNull(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, response));
        probe = new CustomerSourceConnectorPrerequisiteProbe(secrets, runtimeAuthority, transport, new AdmissionJson(mapper),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void verifiesTheExactReadOnlyPrivateRuntimeConnectorAndStorageScope() {
        var result = probe.probe(snapshot);
        assertThat(result.checkedAt()).isEqualTo(NOW);
        assertThat(result.digest()).hasSize(64);
        verify(runtimeAuthority).issuePlatformProxyHeaders("deployment", "target", "release", "sha256:authority",
            List.of("documents:read"), java.time.Duration.ofMinutes(1));
        verify(transport).send(eq("https://exact-runtime.example"), eq("GET"),
            eq("/api/documents/source-connector/status"), anyMap(), isNull(), isNull());
    }

    @Test void wrongStorageScopeUnreadyAndStaleResponsesCannotBecomeEvidence() {
        response.put("scopeDigest", "foreign-storage");
        assertThatThrownBy(() -> probe.probe(snapshot)).hasMessageContaining("SOURCE_CONNECTOR_PROBE_FAILED");
        response.put("scopeDigest", HexFormat.of().formatHex(digest("private-bucket|policies/"))).put("ready", false);
        assertThatThrownBy(() -> probe.probe(snapshot)).hasMessageContaining("SOURCE_CONNECTOR_PROBE_FAILED");
        response.put("ready", true).put("checkedAt", NOW.minusSeconds(61).toString());
        assertThatThrownBy(() -> probe.probe(snapshot)).hasMessageContaining("SOURCE_CONNECTOR_PROBE_FAILED");
        response.put("checkedAt", NOW.plusSeconds(1).toString());
        assertThatThrownBy(() -> probe.probe(snapshot)).hasMessageContaining("SOURCE_CONNECTOR_PROBE_FAILED");
    }

    private byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
}
