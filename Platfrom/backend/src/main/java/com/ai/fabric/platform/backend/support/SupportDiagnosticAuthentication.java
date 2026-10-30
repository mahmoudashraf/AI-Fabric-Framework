package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@Service
public class SupportDiagnosticAuthentication {
    public static final String SERVICE_KEY_HEADER = "X-LOOMAI-SUPPORT-SERVICE-KEY";
    public static final String DIAGNOSTIC_SCOPE = "support:diagnostics:read";
    private final SupportSettings settings;
    private final PlatformSecretService secrets;

    public SupportDiagnosticAuthentication(SupportSettings settings, PlatformSecretService secrets) {
        this.settings = settings; this.secrets = secrets;
    }

    public record VerifiedCaller(String principal, String conversationId, RuntimeBinding binding) { }

    public VerifiedCaller verify(HttpHeaders headers) {
        settings.requireEnabled();
        var expectedKey = secrets.resolveSecret(settings.diagnosticServiceKeySecretName());
        if (expectedKey == null || expectedKey.isBlank()) throw SupportPolicy.unavailable();
        String supplied = single(headers, SERVICE_KEY_HEADER);
        if (supplied.length() > 8192 || !MessageDigest.isEqual(expectedKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw SupportPolicy.denied();
        }
        // These headers are assertions only on this dedicated, service-authenticated connector route.
        var binding = settings.binding();
        equal(headers, "X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
        equal(headers, "X-AIFABRIC-AUTH-MODE", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        equal(headers, "X-AIFABRIC-AUTH-CALLER-TYPE", "TRUSTED_BACKEND");
        equal(headers, "X-AIFABRIC-AUTH-DEPLOYMENT-ID", binding.deploymentId());
        equal(headers, "X-AIFABRIC-AUTH-CUSTOMER-ID", binding.customerId());
        equal(headers, "X-AIFABRIC-AUTH-TENANT-ID", binding.tenantId());
        equal(headers, "X-AIFABRIC-AUTH-ISSUER", binding.issuer());
        if (!values(single(headers, "X-AIFABRIC-AUTH-AUDIENCES")).contains(binding.audience())
            || !values(single(headers, "X-AIFABRIC-AUTH-SCOPES")).contains(DIAGNOSTIC_SCOPE)) throw SupportPolicy.denied();
        try {
            if (!Instant.parse(single(headers, "X-AIFABRIC-AUTH-EXPIRES-AT")).isAfter(Instant.now())) throw SupportPolicy.denied();
        } catch (java.time.format.DateTimeParseException invalid) { throw SupportPolicy.denied(); }
        return new VerifiedCaller(single(headers, "X-AIFABRIC-AUTH-SUBJECT-ID"),
            optionalConversation(headers), binding);
    }

    private static void equal(HttpHeaders headers, String name, String expected) {
        if (!expected.equals(single(headers, name))) throw SupportPolicy.denied();
    }
    private static String single(HttpHeaders headers, String name) {
        var values = headers.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()
            || values.getFirst().length() > 8192) throw SupportPolicy.denied();
        return values.getFirst();
    }
    private static Set<String> values(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).collect(Collectors.toSet());
    }
    private static String optionalConversation(HttpHeaders headers) {
        // Conversation-disabled specialists preserve callerIdentity without a conversation binding.
        if (!headers.containsKey("X-AIFABRIC-CONVERSATION-ID")) return null;
        return single(headers, "X-AIFABRIC-CONVERSATION-ID");
    }
}
