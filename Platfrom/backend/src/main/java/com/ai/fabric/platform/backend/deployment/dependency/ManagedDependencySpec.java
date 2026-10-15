package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Published by the reviewed template, never accepted as a customer-supplied environment overlay. */
public record ManagedDependencySpec(String schemaVersion, List<Service> services,
                                    List<RuntimeEnvironmentBinding> runtimeEnvironment) {
    public ManagedDependencySpec(String schemaVersion, List<Service> services) {
        this(schemaVersion, services, List.of());
    }
    public ManagedDependencySpec {
        services = services == null ? null : List.copyOf(services);
        runtimeEnvironment = runtimeEnvironment == null ? List.of() : List.copyOf(runtimeEnvironment);
    }
    public enum Contract { HTTP_SERVICE, BASIC_INBOX_KEYS_V1 }
    public enum ValueKind {
        LITERAL, SECRET, ACCOUNT_ID, BINDING_ID, CUSTOMER_ID, TENANT_ID, DEPLOYMENT_ID,
        TARGET_ID, VERSION_ID, SERVICE_REF, CALLBACK_URL, CALLBACK_HOSTS, ORIGINS, SERVICE_URL, SERVICE_URL_PATH,
        SERVICE_HOST, PUBLIC_URL,
        DATABASE_JDBC, DATABASE_USERNAME, OPERATORS, TARGET_VALUE, TARGET_SECRET,
        SERVICE_SECRET, SECRET_BCRYPT_BASE64, SECRET_STANDARD_BASE64
    }
    public record Value(ValueKind kind, String reference, String serviceSlot) {
        public Value(ValueKind kind, String reference) { this(kind, reference, null); }
    }
    public record Capacity(long milliCpu, long memoryMiB, long diskMiB, int resources) {
        public Capacity plus(Capacity other) {
            return new Capacity(Math.addExact(milliCpu, other.milliCpu),
                Math.addExact(memoryMiB, other.memoryMiB), Math.addExact(diskMiB, other.diskMiB),
                Math.addExact(resources, other.resources));
        }
    }
    public record Database(String image, String passwordPurpose, Capacity capacity) {}
    public record Volume(String mountPath, Capacity capacity) {}
    public enum RuntimeTarget { RUNTIME, CONNECTOR }
    public record RuntimeEnvironmentBinding(RuntimeTarget target, String name, Value value) {}
    public record Service(String slot, Contract contract, String image, String sourceArtifactId,
                          String sourceCommit, int port, String healthPath, Capacity capacity,
                          Set<String> secretPurposes, Map<String, Value> environment,
                          Database database, List<Volume> volumes, List<String> dependsOn,
                          String initializationContract) {
        public Service {
            if (secretPurposes != null) secretPurposes = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(secretPurposes));
            if (environment != null) environment = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(environment));
            if (volumes != null) volumes = List.copyOf(volumes);
            if (dependsOn != null) dependsOn = List.copyOf(dependsOn);
        }
    }

    public Capacity totalCapacity() {
        Capacity total = new Capacity(0, 0, 0, 0);
        for (Service service : services) {
            total = total.plus(service.capacity());
            if (service.database() != null) total = total.plus(service.database().capacity());
            for (Volume volume : service.volumes()) total = total.plus(volume.capacity());
        }
        return total;
    }
}
