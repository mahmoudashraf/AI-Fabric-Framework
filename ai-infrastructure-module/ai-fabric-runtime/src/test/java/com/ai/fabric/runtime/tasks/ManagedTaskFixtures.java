package com.ai.fabric.runtime.tasks;

import com.ai.fabric.runtime.auth.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

final class ManagedTaskFixtures {
    static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    static ObjectNode fixture() {
        return fixture("compiler-generated-managed-tasks.json");
    }
    static ObjectNode fixture(String name) {
        try (var input = new ClassPathResource("tasks/" + name).getInputStream()) {
            return (ObjectNode) MAPPER.readTree(input);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static ObjectNode writeFixture() { return fixture("managed-tasks-write-fixture.json"); }
    static ManagedTasksBundle bundle(JsonNode content) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(content);
            return new ManagedTasksBundle(MAPPER, Base64.getEncoder().encodeToString(bytes), hash(bytes));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static String hash(byte[] bytes) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static JsonNode resource(ObjectNode content, String kind) {
        for (var resource : content.path("resources")) if (kind.equals(resource.path("kind").asText())) return resource;
        throw new IllegalArgumentException(kind);
    }
    static ObjectNode textFixture() {
        var content = fixture();
        ((ObjectNode) content.at("/tasks/0")).put("outputMode", "TEXT");
        ((ObjectNode) resource(content, "Specialist").at("/spec/output")).put("conversationTextPointer", "/answer");
        return content;
    }
    static RuntimeResolvedIdentity identity(String... scopes) {
        return RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .subjectId("client-service-42").subjectType(RuntimeAuthSubjectType.TRUSTED_BACKEND)
            .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED).callerType(RuntimeAuthCallerType.TRUSTED_BACKEND)
            .deploymentId("dep-123").tenantId("tenant-123").customerId("customer-123")
            .issuer("backend-test").audiences(List.of("dep-123")).expiresAt(Instant.now().plusSeconds(180))
            .grantedScopes(List.of(scopes)).build()).build();
    }
    static <T> ObjectProvider<T> provider(Class<T> type, T value) {
        var factory = new StaticListableBeanFactory();
        if (value != null) factory.addBean("instance", value);
        return factory.getBeanProvider(type);
    }
}
