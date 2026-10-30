package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.desk.*;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportDeskFixture extends SupportFixture {
    static final String ISSUER = "https://identity.example.test/auth/v1";
    static final String SUBJECT = "actual-support-agent";
    static final String CONTEXT_KEY = "dedicated-desk-context-key-32-characters-long";
    final DeskBindingRepository deskBindings = mock(DeskBindingRepository.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final CustomerSupabaseIdentityService verifiedIdentities = mock(CustomerSupabaseIdentityService.class);
    final DeskBindingEntity binding = new DeskBindingEntity();
    final DeskServiceClient.Connection connection = new DeskServiceClient.Connection("https://desk.example.test", "separate-global-administration-key-long-enough");
    final SupportDeskAuthorization deskAuthorization;
    final SupportDeskStore deskStore;
    final SupportDeskContextService contexts;

    SupportDeskFixture() { super(); initialize(); deskAuthorization = authorization(); deskStore = new SupportDeskStore(jdbc, json); contexts = service(); }
    SupportDeskFixture(DataSource source) { super(source); initialize(); deskAuthorization = authorization(); deskStore = new SupportDeskStore(jdbc, json); contexts = service(); }

    private void initialize() {
        env.setProperty("platform.support.desk.binding-id", "support-binding");
        env.setProperty("platform.support.desk.context-service-key-secret-name", "PLATFORM_MANAGED_SUPPORT_DESK_CONTEXT");
        env.setProperty("platform.support.desk.resolve-enabled", "true");
        binding.setId("support-binding"); binding.setCustomerId("internal-owner"); binding.setTenantId("internal-tenant");
        binding.setDeploymentId("support-runtime"); binding.setTargetProfileId("staging-target"); binding.setStatus("ENABLED");
        when(deskBindings.findById("support-binding")).thenReturn(Optional.of(binding));
        var target = new DeploymentTargetProfileEntity(); target.setId("staging-target"); target.setEnvironmentName("staging"); target.setActive(true);
        when(targets.findById(target.getId())).thenReturn(Optional.of(target));
        when(secrets.resolveSecret("PLATFORM_MANAGED_SUPPORT_DESK_CONTEXT")).thenReturn(CONTEXT_KEY);
        when(desk.connection("staging")).thenReturn(connection);
        when(desk.get(eq(connection), anyString())).thenReturn(json.createObjectNode().put("authorized", true));
        when(verifiedIdentities.verifyIdentity("actual-agent-token")).thenReturn(jwt(ISSUER, SUBJECT, "authenticated", Instant.now().plusSeconds(300)));
    }
    private SupportDeskAuthorization authorization() {
        var identityPolicy = new CustomerSupabaseAuthProperties(true, "https://identity.example.test", "public", ISSUER,
            ISSUER + "/.well-known/jwks.json", "authenticated", List.of());
        return new SupportDeskAuthorization(settings, secrets, verifiedIdentities, identityPolicy, deskBindings, targets, desk);
    }
    private SupportDeskContextService service() {
        return transactional(new SupportDeskContextService(deskAuthorization, settings, store, deskStore, requesters, resources, customers));
    }
    HttpHeaders deskHeaders() {
        var headers = new HttpHeaders(); headers.set(HttpHeaders.AUTHORIZATION, "Bearer actual-agent-token");
        headers.set(SupportDeskAuthorization.SERVICE_HEADER, CONTEXT_KEY);
        headers.set(SupportDeskAuthorization.BINDING_HEADER, "support-binding");
        return headers;
    }
    Request lookup(String caseId) {
        var c = service.requireOwnedCase(caseId);
        return new Request(1, Operation.LOOKUP, new Scope("support-binding", c.customerId(), c.tenantId(), c.deploymentId(), c.targetProfileId(),
            "handover-1", c.conversationId(), c.principalId(), "desk-external-1"), new Actor(ISSUER, SUBJECT, "desk-agent-1"), null);
    }
    Request resolve(Request lookup, long version, String key) {
        return new Request(1, Operation.RESOLVE, lookup.scope(), lookup.actor(), new Command(key, version));
    }
    static Jwt jwt(String issuer, String subject, String audience, Instant expires) {
        return Jwt.withTokenValue("verified-token").header("alg", "ES256").issuer(issuer).subject(subject)
            .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(10)).expiresAt(expires).build();
    }
}
