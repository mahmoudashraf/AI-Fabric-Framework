package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Dedicated connector credentials plus verified Runtime identity, never a Console/admin-role bypass. */
@Component
public class AuthoringToolAuthentication {
    public static final String KEY_HEADER = "X-LOOMAI-AUTHORING-SERVICE-KEY";
    public static final String RELEASE_HEADER = "X-LOOMAI-AUTHORING-RELEASE-ID";
    private final AuthoringProperties properties;
    private final PlatformSecretService secrets;
    private final AuthoringBrainBindingService bindings;
    public AuthoringToolAuthentication(AuthoringProperties properties, PlatformSecretService secrets, AuthoringBrainBindingService bindings) {
        this.properties = properties; this.secrets = secrets; this.bindings = bindings;
    }
    public AuthoringDraftOperations.Caller verify(HttpHeaders headers, String sessionId, String turnId) {
        return verify(headers, sessionId, turnId, null);
    }
    public AuthoringDraftOperations.Caller verify(HttpHeaders headers, String sessionId, String turnId, String action) {
        if (!properties.isEnabled() || !properties.isConfiguredBrainEnabled()) throw new ResponseStatusException(NOT_FOUND);
        var key = secrets.resolveSecret(properties.getApiKeySecretRef());
        if (key == null || key.isBlank()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_TOOL_AUTH_UNAVAILABLE");
        if (!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), single(headers, KEY_HEADER).getBytes(StandardCharsets.UTF_8))) throw denied();
        equal(headers, "X-AIFABRIC-AUTH-SUBJECT-ID", "authoring:" + sessionId);
        equal(headers, "X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
        equal(headers, "X-AIFABRIC-AUTH-MODE", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        equal(headers, "X-AIFABRIC-AUTH-CALLER-TYPE", "TRUSTED_BACKEND");
        equal(headers, "X-AIFABRIC-AUTH-DEPLOYMENT-ID", properties.getDeploymentId());
        equal(headers, "X-AIFABRIC-AUTH-CUSTOMER-ID", properties.getCustomerId());
        equal(headers, "X-AIFABRIC-AUTH-TENANT-ID", properties.getTenantId());
        var frozen = bindings.identity(single(headers, RELEASE_HEADER));
        equal(headers, "X-AIFABRIC-AUTH-ISSUER", frozen.privateIssuer());
        if (!member(headers, "X-AIFABRIC-AUTH-AUDIENCES", frozen.privateAudience())
            || !member(headers, "X-AIFABRIC-AUTH-SCOPES", "configuration:author")) throw denied();
        if (action != null && !member(headers, "X-AIFABRIC-AUTH-SCOPES", "action:" + action)) throw denied();
        try { if (!Instant.parse(single(headers, "X-AIFABRIC-AUTH-EXPIRES-AT")).isAfter(Instant.now())) throw denied(); }
        catch (java.time.format.DateTimeParseException invalid) { throw denied(); }
        return new AuthoringDraftOperations.Caller(sessionId, turnId, properties.getDeploymentId(), single(headers, RELEASE_HEADER), action);
    }
    private boolean member(HttpHeaders headers, String name, String value) {
        return Arrays.stream(single(headers, name).split(",")).map(String::trim).anyMatch(value::equals);
    }
    private void equal(HttpHeaders headers, String name, String expected) { if (!expected.equals(single(headers, name))) throw denied(); }
    private String single(HttpHeaders headers, String name) {
        var values = headers.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()
            || values.getFirst().length() > 8192) throw denied();
        return values.getFirst();
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(FORBIDDEN, "AUTHORING_TOOL_ACCESS_DENIED"); }
}
