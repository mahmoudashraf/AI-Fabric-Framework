package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.*;

public final class ManagedDependencyFixtures {
    public static final String IMAGE = "registry.example.test/dependency@sha256:" + "a".repeat(64);
    private ManagedDependencyFixtures() {}
    public static com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity ownedAiArtifact(Service service) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var manifest = mapper.createObjectNode().put("schemaVersion", ManagedDependencyArtifactPolicy.SCHEMA);
        manifest.putObject("managedDependencyContract")
            .put("schemaVersion", ManagedDependencyArtifactPolicy.CONTRACT_SCHEMA)
            .put("purpose", "AI_OPERATIONAL_SERVICE").put("lifecycleOwnership", "DEPLOYMENT_OWNED")
            .put("persistence", "AI_OPERATIONAL_STATE")
            .put("initializationContract", service.initializationContract())
            .put("serviceContract", service.contract().name());
        var normalized = new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(mapper)
            .normalize(manifest);
        var artifact = new com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity();
        artifact.setId(service.sourceArtifactId()); artifact.setServiceName("test-ai-" + service.slot());
        artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setArtifactRole("MANAGED_DEPENDENCY");
        int separator = service.image().indexOf('@');
        artifact.setImageRepository(service.image().substring(0, separator));
        artifact.setImageDigest(service.image().substring(separator + 1));
        artifact.setGitCommitSha(service.sourceCommit());
        artifact.setCapabilityManifestJson(normalized.manifest().toString());
        artifact.setCapabilityManifestHash(normalized.hash());
        artifact.setPromotionChannel("production"); artifact.setPromotedAt(java.time.Instant.now());
        return artifact;
    }
    public static Service http(String slot, boolean database) {
        Map<String, Value> environment = new TreeMap<>();
        environment.put("LOOMAI_MANAGED_DEPENDENCY_KEY", new Value(ValueKind.SECRET, "management"));
        managedIdentity(environment, slot);
        if (database) {
            environment.put("DATABASE_URL", new Value(ValueKind.DATABASE_JDBC, null));
            environment.put("DATABASE_PASSWORD", new Value(ValueKind.SECRET, "db"));
        } else {
            environment.put("API_KEY", new Value(ValueKind.SECRET, "api"));
        }
        return new Service(slot, Contract.HTTP_SERVICE, IMAGE, "artifact-" + slot, "b".repeat(40),
            8080, "/actuator/health", new Capacity(250, 512, 128, 1), Set.of("api", "db", "management"),
            environment,
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
        env.put("LOOMAI_MANAGED_DEPENDENCY_KEY", new Value(ValueKind.SECRET, "management"));
        managedIdentity(env, "inbox");
        env.put("BASIC_INBOX_OPERATORS", new Value(ValueKind.OPERATORS, null));
        var inbox = new Service("inbox", Contract.BASIC_INBOX_KEYS_V1, IMAGE, "artifact-inbox", "b".repeat(40),
            8085, "/actuator/health", new Capacity(250, 512, 128, 1), Set.of("api", "callback", "encryption", "management"),
            env, null, List.of(new Volume("/app/data", new Capacity(0, 0, 2048, 0))),
            List.of(), "inbox-callback-canary/v1");
        return new ManagedDependencySpec("managed-dependencies/v1",
            List.of(http("simulator", false), http("receiver", true), inbox),
            List.of(
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_SIMULATOR_BASE_URL",
                    new Value(ValueKind.SERVICE_URL, "simulator")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_SIMULATOR_HOST",
                    new Value(ValueKind.SERVICE_HOST, "simulator")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_SIMULATOR_API_KEY",
                    new Value(ValueKind.SERVICE_SECRET, "api", "simulator")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_SIMULATOR_ACCOUNT_ID",
                    new Value(ValueKind.ACCOUNT_ID, null, "simulator")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_RECEIVER_ACTION_URL",
                    new Value(ValueKind.SERVICE_URL_PATH, "/api/internal/actions/execute", "receiver")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_INBOX_BASE_URL",
                    new Value(ValueKind.SERVICE_URL, "inbox")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_INBOX_CALLBACK_SIGNING_SECRET",
                    new Value(ValueKind.TARGET_SECRET, "conversation-handover-callback-signing")),
                new RuntimeEnvironmentBinding(RuntimeTarget.CONNECTOR, "MANAGED_INBOX_API_KEY",
                    new Value(ValueKind.SERVICE_SECRET, "api", "inbox"))
            ));
    }

    private static void managedIdentity(Map<String, Value> env, String slot) {
        env.put("LOOMAI_MANAGED_DEPENDENCY_CUSTOMER_ID", new Value(ValueKind.CUSTOMER_ID, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_TENANT_ID", new Value(ValueKind.TENANT_ID, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_DEPLOYMENT_ID", new Value(ValueKind.DEPLOYMENT_ID, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_TARGET_PROFILE_ID", new Value(ValueKind.TARGET_ID, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_VERSION_ID", new Value(ValueKind.VERSION_ID, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_SLOT", new Value(ValueKind.LITERAL, slot));
        env.put("LOOMAI_MANAGED_DEPENDENCY_SERVICE_REF", new Value(ValueKind.SERVICE_REF, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_ACCOUNT_ID", new Value(ValueKind.ACCOUNT_ID, null));
        env.put("LOOMAI_MANAGED_DEPENDENCY_BINDING_ID", new Value(ValueKind.BINDING_ID, null));
    }
}
