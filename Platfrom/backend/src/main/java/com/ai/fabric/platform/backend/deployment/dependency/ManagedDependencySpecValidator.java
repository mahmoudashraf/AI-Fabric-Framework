package com.ai.fabric.platform.backend.deployment.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import static org.springframework.http.HttpStatus.CONFLICT;

@Component
public class ManagedDependencySpecValidator {
    private final ObjectMapper mapper;
    public ManagedDependencySpecValidator(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }
    public String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Dependency contract cannot be fingerprinted.", error);
        }
    }
    public void validate(ManagedDependencySpec spec) {
        if (spec == null || !"managed-dependencies/v1".equals(spec.schemaVersion())
            || spec.services() == null || spec.services().isEmpty() || spec.services().size() > 16) fail("SPEC_INVALID");
        Set<String> available = new HashSet<>();
        for (var service : spec.services()) {
            if (service == null || !token(service.slot()) || !available.add(service.slot())
                || service.contract() == null || service.port() < 1 || service.port() > 65535
                || !path(service.healthPath()) || !text(service.sourceArtifactId()) || !text(service.sourceCommit())
                || !text(service.initializationContract()) || service.secretPurposes() == null
                || service.environment() == null || service.volumes() == null || service.dependsOn() == null) fail("SPEC_INVALID");
            immutableImage(service.image());
            capacity(service.capacity(), 1);
            for (String purpose : service.secretPurposes()) if (!token(purpose)) fail("SECRET_PURPOSE_INVALID");
            if (!available.containsAll(service.dependsOn()) || service.dependsOn().contains(service.slot())) fail("DEPENDENCY_ORDER_INVALID");
            if (service.database() != null) {
                immutableImage(service.database().image());
                capacity(service.database().capacity(), 1);
                if (!service.secretPurposes().contains(service.database().passwordPurpose())) fail("DATABASE_SECRET_MISSING");
            }
            Set<String> mounts = new HashSet<>();
            for (var volume : service.volumes()) {
                if (!path(volume.mountPath()) || !mounts.add(volume.mountPath())) fail("VOLUME_INVALID");
                capacity(volume.capacity(), 0);
                if (volume.capacity().diskMiB() == 0) fail("VOLUME_CAPACITY_MISSING");
            }
            for (var entry : service.environment().entrySet()) {
                if (!entry.getKey().matches("[A-Z][A-Z0-9_]{0,127}") || entry.getValue() == null
                    || entry.getValue().kind() == null) fail("ENVIRONMENT_INVALID");
                var value = entry.getValue();
                if (value.kind() != ManagedDependencySpec.ValueKind.SERVICE_SECRET && value.serviceSlot() != null) fail("UNEXPECTED_SERVICE_REFERENCE");
                switch (value.kind()) {
                    case SECRET, SECRET_BCRYPT_BASE64, SECRET_STANDARD_BASE64 -> { if (!service.secretPurposes().contains(value.reference())) fail("SECRET_PURPOSE_MISSING"); }
                    case TARGET_VALUE, TARGET_SECRET -> { if (!token(value.reference())) fail("TARGET_SLOT_REFERENCE_INVALID"); }
                    case SERVICE_SECRET -> {
                        if (!service.dependsOn().contains(value.serviceSlot()) || spec.services().stream()
                            .filter(s -> value.serviceSlot().equals(s.slot())).noneMatch(s -> s.secretPurposes().contains(value.reference()))) fail("SERVICE_SECRET_REFERENCE_INVALID");
                    }
                    case SERVICE_URL -> { if (!service.dependsOn().contains(value.reference())) fail("SERVICE_REFERENCE_INVALID"); }
                    case DATABASE_JDBC, DATABASE_USERNAME -> { if (service.database() == null) fail("DATABASE_MISSING"); }
                    case CALLBACK_URL -> { if (!token(value.reference())) fail("CALLBACK_REFERENCE_INVALID"); }
                    case OPERATORS -> { if (service.contract() != ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1) fail("OPERATOR_CONTRACT_INVALID"); }
                    case LITERAL -> { if (value.reference() == null || value.reference().length() > 4096) fail("LITERAL_INVALID"); }
                    default -> { if (value.reference() != null) fail("UNEXPECTED_VALUE_REFERENCE"); }
                }
            }
            validateLifecycle(service);
            if (service.contract() == ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1) validateInbox(service);
        }
        Set<String> runtimeKeys = new HashSet<>();
        if (spec.runtimeEnvironment() == null || spec.runtimeEnvironment().size() > 128) fail("SPEC_INVALID");
        for (var binding : spec.runtimeEnvironment()) {
            if (binding == null || binding.target() == null || binding.name() == null
                || !binding.name().matches("[A-Z][A-Z0-9_]{0,127}")
                || !runtimeKeys.add(binding.target() + ":" + binding.name()) || binding.value() == null) {
                fail("RUNTIME_BINDING_INVALID");
            }
            var value = binding.value();
            if (value.kind() == ManagedDependencySpec.ValueKind.SERVICE_URL
                || value.kind() == ManagedDependencySpec.ValueKind.SERVICE_HOST) {
                if (!available.contains(value.reference()) || value.serviceSlot() != null) fail("RUNTIME_BINDING_INVALID");
            } else if (value.kind() == ManagedDependencySpec.ValueKind.SERVICE_SECRET) {
                if (!available.contains(value.serviceSlot()) || value.reference() == null
                    || spec.services().stream().filter(service -> value.serviceSlot().equals(service.slot()))
                        .noneMatch(service -> service.secretPurposes().contains(value.reference()))) {
                    fail("RUNTIME_BINDING_INVALID");
                }
            } else if (value.kind() == ManagedDependencySpec.ValueKind.ACCOUNT_ID
                || value.kind() == ManagedDependencySpec.ValueKind.BINDING_ID) {
                if (!available.contains(value.serviceSlot()) || value.reference() != null) fail("RUNTIME_BINDING_INVALID");
            } else {
                fail("RUNTIME_BINDING_INVALID");
            }
        }
        spec.totalCapacity();
    }
    private void validateLifecycle(ManagedDependencySpec.Service service) {
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_KEY", ManagedDependencySpec.ValueKind.SECRET,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_CUSTOMER_ID", ManagedDependencySpec.ValueKind.CUSTOMER_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_TENANT_ID", ManagedDependencySpec.ValueKind.TENANT_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_DEPLOYMENT_ID", ManagedDependencySpec.ValueKind.DEPLOYMENT_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_TARGET_PROFILE_ID", ManagedDependencySpec.ValueKind.TARGET_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_VERSION_ID", ManagedDependencySpec.ValueKind.VERSION_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_SLOT", ManagedDependencySpec.ValueKind.LITERAL,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_SERVICE_REF", ManagedDependencySpec.ValueKind.SERVICE_REF,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_ACCOUNT_ID", ManagedDependencySpec.ValueKind.ACCOUNT_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        requireKind(service, "LOOMAI_MANAGED_DEPENDENCY_BINDING_ID", ManagedDependencySpec.ValueKind.BINDING_ID,
            "MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        if (!service.slot().equals(service.environment().get("LOOMAI_MANAGED_DEPENDENCY_SLOT").reference())) {
            fail("MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
        }
    }
    private void validateInbox(ManagedDependencySpec.Service service) {
        requireKind(service, "BASIC_INBOX_OPERATORS", ManagedDependencySpec.ValueKind.OPERATORS);
        requireKind(service, "BASIC_INBOX_BINDING_ID", ManagedDependencySpec.ValueKind.BINDING_ID);
        requireKind(service, "BASIC_INBOX_TENANT_ID", ManagedDependencySpec.ValueKind.TENANT_ID);
        requireKind(service, "BASIC_INBOX_DEPLOYMENT_ID", ManagedDependencySpec.ValueKind.DEPLOYMENT_ID);
        requireKind(service, "BASIC_INBOX_CONNECTOR_CALLBACK_URL", ManagedDependencySpec.ValueKind.CALLBACK_URL);
        Set<String> purposes = new HashSet<>();
        for (String name : new String[]{"BASIC_INBOX_PROVIDER_API_KEY", "BASIC_INBOX_CALLBACK_SIGNING_SECRET", "BASIC_INBOX_DATA_ENCRYPTION_SECRET"}) {
            requireKind(service, name, ManagedDependencySpec.ValueKind.SECRET);
            if (!purposes.add(service.environment().get(name).reference())) fail("INBOX_SECRETS_MUST_BE_DISTINCT");
        }
        boolean database = service.database() != null;
        if (!database && service.volumes().isEmpty()) fail("INBOX_PERSISTENCE_MISSING");
        if (database) {
            requireKind(service, "BASIC_INBOX_JDBC_URL", ManagedDependencySpec.ValueKind.DATABASE_JDBC);
            requireKind(service, "BASIC_INBOX_JDBC_USERNAME", ManagedDependencySpec.ValueKind.DATABASE_USERNAME);
            requireKind(service, "BASIC_INBOX_JDBC_PASSWORD", ManagedDependencySpec.ValueKind.SECRET);
            if (!service.database().passwordPurpose().equals(service.environment().get("BASIC_INBOX_JDBC_PASSWORD").reference())) fail("DATABASE_SECRET_INVALID");
        }
    }
    private void requireKind(ManagedDependencySpec.Service service, String name, ManagedDependencySpec.ValueKind kind) {
        var value = service.environment().get(name);
        if (value == null || value.kind() != kind) fail("INBOX_CONTRACT_INVALID");
    }
    private void requireKind(ManagedDependencySpec.Service service, String name,
                             ManagedDependencySpec.ValueKind kind, String code) {
        var value = service.environment().get(name);
        if (value == null || value.kind() != kind) fail(code);
    }
    public static void immutableImage(String image) {
        if (image == null || !image.matches("[a-zA-Z0-9][a-zA-Z0-9./:_-]*@sha256:[a-f0-9]{64}")) fail("IMMUTABLE_IMAGE_REQUIRED");
    }
    private void capacity(ManagedDependencySpec.Capacity value, int resources) {
        if (value == null || value.milliCpu() < 0 || value.memoryMiB() < 0 || value.diskMiB() < 0
            || value.resources() != resources || (resources == 1 && (value.milliCpu() == 0 || value.memoryMiB() == 0))) fail("CAPACITY_INVALID");
    }
    private static boolean token(String value) { return value != null && value.matches("[a-z][a-z0-9-]{0,47}"); }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static boolean path(String value) { return value != null && value.startsWith("/") && !value.contains("..") && !value.contains("\n"); }
    public static void fail(String code) { throw new ResponseStatusException(CONFLICT, code); }
}
