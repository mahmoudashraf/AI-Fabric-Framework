package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.desk.DeskBindingRepository;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;

@Service
public class SupportDeskAuthorization {
    public static final String SERVICE_HEADER = "X-LoomAI-Desk-Service-Key";
    public static final String BINDING_HEADER = "X-LoomAI-Desk-Binding-Id";
    private final SupportSettings settings;
    private final PlatformSecretService secrets;
    private final CustomerSupabaseIdentityService identities;
    private final CustomerSupabaseAuthProperties identityPolicy;
    private final DeskBindingRepository bindings;
    private final DeploymentTargetProfileRepository targets;
    private final DeskServiceClient desk;

    public SupportDeskAuthorization(SupportSettings settings, PlatformSecretService secrets,
        CustomerSupabaseIdentityService identities, CustomerSupabaseAuthProperties identityPolicy,
        DeskBindingRepository bindings, DeploymentTargetProfileRepository targets, DeskServiceClient desk) {
        this.settings = settings; this.secrets = secrets; this.identities = identities; this.identityPolicy = identityPolicy;
        this.bindings = bindings; this.targets = targets; this.desk = desk;
    }

    public VerifiedAgent callback(HttpHeaders headers, Request request) {
        if (request == null || request.scope() == null || request.actor() == null) throw SupportPolicy.bad("SUPPORT_DESK_REQUEST_REQUIRED");
        var expectedName = settings.deskContextSecretName();
        if (expectedName.equals(settings.diagnosticServiceKeySecretName())) throw SupportPolicy.unavailable();
        var expected = secrets.resolveSecret(expectedName);
        var diagnostic = secrets.resolveSecret(settings.diagnosticServiceKeySecretName());
        if (expected == null || expected.length() < 32 || expected.length() > 4096
            || diagnostic != null && equal(expected, diagnostic)) throw SupportPolicy.unavailable();
        if (!equal(expected, single(headers, SERVICE_HEADER))) throw SupportPolicy.denied();
        if (!settings.deskBindingId().equals(single(headers, BINDING_HEADER))) throw SupportPolicy.denied();
        var fixed = settings.binding(); var scope = request.scope();
        if (!settings.deskBindingId().equals(scope.bindingId()) || !fixed.customerId().equals(scope.customerId())
            || !fixed.tenantId().equals(scope.tenantId()) || !fixed.deploymentId().equals(scope.deploymentId())
            || !fixed.targetProfileId().equals(scope.targetProfileId())) throw SupportPolicy.denied();
        var agent = staff(headers, request.actor());
        var target = targets.findById(fixed.targetProfileId()).orElseThrow(SupportPolicy::unavailable);
        if (equal(expected, desk.connection(target.getEnvironmentName()).serviceKey())) throw SupportPolicy.unavailable();
        return agent;
    }

    /** A registered Desk agent need not have a Console customer membership. */
    public VerifiedAgent staff(HttpHeaders headers, Actor asserted) {
        var fixed = settings.binding();
        var binding = bindings.findById(settings.deskBindingId()).orElseThrow(SupportPolicy::unavailable);
        if (!Set.of("ENABLED", "APPLYING").contains(binding.getStatus() == null ? "" : binding.getStatus())
            || !fixed.customerId().equals(binding.getCustomerId()) || !fixed.tenantId().equals(binding.getTenantId())
            || !fixed.deploymentId().equals(binding.getDeploymentId()) || !fixed.targetProfileId().equals(binding.getTargetProfileId())) {
            throw SupportPolicy.unavailable();
        }
        var target = targets.findById(fixed.targetProfileId()).filter(t -> t.isActive()).orElseThrow(SupportPolicy::unavailable);
        String bearer = single(headers, HttpHeaders.AUTHORIZATION);
        if (!bearer.startsWith("Bearer ") || bearer.length() <= 7 || bearer.length() > 16384) throw SupportPolicy.denied();
        var jwt = identities.verifyIdentity(bearer.substring(7));
        if (jwt.getIssuer() == null || !Objects.equals(identityPolicy.issuer(), jwt.getIssuer().toString())
            || !jwt.getAudience().contains(identityPolicy.audience()) || jwt.getExpiresAt() == null
            || !jwt.getExpiresAt().isAfter(Instant.now()) || jwt.getSubject() == null || jwt.getSubject().isBlank()) throw SupportPolicy.denied();
        String issuer = jwt.getIssuer().toString(); String subject = jwt.getSubject();
        SupportPolicy.required(issuer, 512, "DESK_ISSUER"); SupportPolicy.required(subject, 255, "DESK_SUBJECT");
        if (asserted != null && (!issuer.equals(asserted.issuer()) || !subject.equals(asserted.subject()))) throw SupportPolicy.denied();
        if (asserted != null) SupportPolicy.required(asserted.agentId(), 128, "DESK_AGENT");
        var proof = desk.get(desk.connection(target.getEnvironmentName()), "/api/desk/admin/v1/bindings/"
            + encode(binding.getId()) + "/authorizations?issuer=" + encode(issuer) + "&subject=" + encode(subject) + "&permission=SUPPORT");
        if (!proof.path("authorized").isBoolean() || !proof.path("authorized").booleanValue()) throw SupportPolicy.denied();
        return new VerifiedAgent(issuer, subject, asserted == null ? null : asserted.agentId());
    }

    private static String single(HttpHeaders headers, String name) {
        var values = headers.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()
            || values.getFirst().length() > 16384) throw SupportPolicy.denied();
        return values.getFirst();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
