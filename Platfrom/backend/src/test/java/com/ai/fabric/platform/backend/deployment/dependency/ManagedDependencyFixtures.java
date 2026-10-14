package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.*;

public final class ManagedDependencyFixtures {
    public static final String IMAGE = "registry.example.test/dependency@sha256:" + "a".repeat(64);
    private ManagedDependencyFixtures() {}
    public static Service http(String slot, boolean database) {
        return new Service(slot, Contract.HTTP_SERVICE, IMAGE, "artifact-" + slot, "b".repeat(40),
            8080, "/actuator/health", new Capacity(250, 512, 128, 1), Set.of("api", "db"),
            database ? Map.of("DATABASE_URL", new Value(ValueKind.DATABASE_JDBC, null),
                "DATABASE_PASSWORD", new Value(ValueKind.SECRET, "db")) : Map.of("API_KEY", new Value(ValueKind.SECRET, "api")),
            database ? new Database(IMAGE, "db", new Capacity(250, 512, 2048, 1)) : null,
            database ? List.of() : List.of(new Volume("/app/data", new Capacity(0, 0, 2048, 0))),
            List.of(), "scoped-seed-sync-canary/v1");
    }
    public static ManagedDependencySpec topology() {
        Map<String, Value> env = new TreeMap<>();
        env.put("BASIC_INBOX_BINDING_ID", new Value(ValueKind.BINDING_ID, null));
        env.put("BASIC_INBOX_TENANT_ID", new Value(ValueKind.TENANT_ID, null));
        env.put("BASIC_INBOX_DEPLOYMENT_ID", new Value(ValueKind.DEPLOYMENT_ID, null));
        env.put("BASIC_INBOX_CONNECTOR_CALLBACK_URL", new Value(ValueKind.CALLBACK_URL, "inbox"));
        env.put("BASIC_INBOX_PROVIDER_API_KEY", new Value(ValueKind.SECRET, "api"));
        env.put("BASIC_INBOX_CALLBACK_SIGNING_SECRET", new Value(ValueKind.SECRET, "callback"));
        env.put("BASIC_INBOX_DATA_ENCRYPTION_SECRET", new Value(ValueKind.SECRET, "encryption"));
        env.put("BASIC_INBOX_OPERATORS", new Value(ValueKind.OPERATORS, null));
        var inbox = new Service("inbox", Contract.BASIC_INBOX_KEYS_V1, IMAGE, "artifact-inbox", "b".repeat(40),
            8085, "/actuator/health", new Capacity(250, 512, 128, 1), Set.of("api", "callback", "encryption"),
            env, null, List.of(new Volume("/app/data", new Capacity(0, 0, 2048, 0))),
            List.of(), "inbox-callback-canary/v1");
        return new ManagedDependencySpec("managed-dependencies/v1", List.of(http("simulator", false), http("receiver", true), inbox));
    }
}
