package com.ai.fabric.platform.backend.deployment.provider;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CoolifyHostVolumeReclamationService {
    private static final java.util.regex.Pattern SAFE_NAME =
        java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final java.util.regex.Pattern SAFE_ABSOLUTE_PATH =
        java.util.regex.Pattern.compile("/[A-Za-z0-9._/-]+");

    private final ProviderHostCommandRunner runner;

    public CoolifyHostVolumeReclamationService(ProviderHostCommandRunner runner) {
        this.runner = runner;
    }

    public record Volume(String uuid, String name, String hostPath, boolean preserved) {
        public Volume(String uuid, String name, boolean preserved) {
            this(uuid, name, null, preserved);
        }
    }
    public record Receipt(String reference, Instant observedAt, Set<String> reclaimedUuids) { }
    public record ContainerReceipt(String reference, Instant observedAt) { }

    /** Cold-stop proof deliberately allows stopped containers and makes no drain/volume-deletion claim. */
    public ContainerReceipt verifyNoRunningContainers(TargetIntent intent, String serverUuid,
        String operationReference, String resourceType, String providerResourceUuid) {
        if (intent != TargetIntent.CUSTOMER_STAGING || blank(serverUuid) || blank(operationReference)
            || !Set.of("application", "database").contains(resourceType)
            || blank(providerResourceUuid) || !SAFE_NAME.matcher(providerResourceUuid).matches())
            throw new ProviderResourceVerificationFailure("PROVIDER_CONTAINER_VERIFICATION_SCOPE_INVALID");
        assertNoContainers(intent, serverUuid, List.of("docker", "ps", "--quiet", "--filter",
            "label=coolify." + resourceType + "Uuid=" + providerResourceUuid));
        assertNoContainers(intent, serverUuid, List.of("docker", "ps", "--quiet", "--filter",
            "label=coolify." + resourceType + "Id", "--filter", "label=com.docker.compose.project=" + providerResourceUuid));
        if ("application".equals(resourceType)) assertNoContainers(intent, serverUuid,
            List.of("docker", "ps", "--quiet", "--filter", "label=coolify.applicationId", "--filter", "name=" + providerResourceUuid));
        return new ContainerReceipt("provider-host-no-running-containers:" + operationReference, Instant.now());
    }

    /**
     * Proves that no current or legacy Coolify container remains for an exact frozen resource UUID.
     * The label fallbacks mirror Coolify's own container-owner resolution contract.
     */
    public ContainerReceipt verifyContainersAbsent(TargetIntent intent, String serverUuid,
                                                    String operationReference, String resourceType,
                                                    String providerResourceUuid) {
        if (intent == null || blank(serverUuid) || blank(operationReference)
            || !Set.of("application", "database").contains(resourceType)
            || blank(providerResourceUuid) || !SAFE_NAME.matcher(providerResourceUuid).matches()) {
            throw new ProviderResourceVerificationFailure("PROVIDER_CONTAINER_VERIFICATION_SCOPE_INVALID");
        }
        assertNoContainers(intent, serverUuid, List.of(
            "docker", "ps", "-a", "--quiet", "--filter",
            "label=coolify." + resourceType + "Uuid=" + providerResourceUuid));
        assertNoContainers(intent, serverUuid, List.of(
            "docker", "ps", "-a", "--quiet", "--filter",
            "label=coolify." + resourceType + "Id", "--filter",
            "label=com.docker.compose.project=" + providerResourceUuid));
        if ("application".equals(resourceType)) {
            assertNoContainers(intent, serverUuid, List.of(
                "docker", "ps", "-a", "--quiet", "--filter",
                "label=coolify.applicationId", "--filter", "name=" + providerResourceUuid));
        }
        return new ContainerReceipt("provider-host-container-absence:" + operationReference, Instant.now());
    }

    /**
     * Reconciles exact frozen Docker volume names after the owning provider resource is absent.
     * Disposable volumes are removed and proved absent; retained volumes are proved present.
     */
    public Receipt reclaimAndVerify(TargetIntent intent, String serverUuid, String operationReference,
                                    List<Volume> volumes) {
        if (intent == null || blank(serverUuid) || blank(operationReference) || volumes == null) {
            throw new ProviderResourceVerificationFailure("PROVIDER_VOLUME_RECLAMATION_SCOPE_INVALID");
        }
        var ordered = volumes.stream().sorted(Comparator.comparing(Volume::uuid)).toList();
        Set<String> uuids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (var volume : ordered) {
            boolean identityInvalid = blank(volume.uuid()) || !uuids.add(volume.uuid());
            boolean retainedInvalid = volume.preserved() && !safeAbsolutePath(volume.hostPath());
            boolean disposableInvalid = !volume.preserved()
                && (blank(volume.name()) || !SAFE_NAME.matcher(volume.name()).matches() || !names.add(volume.name()));
            if (identityInvalid || retainedInvalid || disposableInvalid) {
                throw new ProviderResourceVerificationFailure("PROVIDER_VOLUME_RECLAMATION_SCOPE_INVALID");
            }
        }
        Set<String> reclaimed = new HashSet<>();
        for (var volume : ordered) {
            if (volume.preserved()) {
                if (!directoryExists(intent, serverUuid, volume.hostPath())) {
                    throw new ProviderResourceVerificationFailure("PERSISTENT_VOLUME_RETENTION_UNPROVEN");
                }
                continue;
            }
            boolean present = volumeExists(intent, serverUuid, volume.name());
            if (present) remove(intent, serverUuid, volume.name());
            if (volumeExists(intent, serverUuid, volume.name())) {
                throw new ProviderResourceVerificationFailure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
            }
            reclaimed.add(volume.uuid());
        }
        Instant observedAt = Instant.now();
        return new Receipt("provider-host-volume-reclamation:" + operationReference,
            observedAt, Set.copyOf(reclaimed));
    }

    private boolean volumeExists(TargetIntent intent, String serverUuid, String name) {
        var result = runner.run(intent, serverUuid,
            List.of("docker", "volume", "ls", "--quiet", "--filter", "name=^" + name + "$"));
        if (result.exitCode() != 0) throw new ProviderResourceVerificationFailure("PROVIDER_HOST_VOLUME_READBACK_FAILED");
        if (result.output().isBlank()) return false;
        List<String> rows = result.output().lines().map(String::trim).filter(value -> !value.isBlank()).toList();
        if (rows.size() != 1 || !name.equals(rows.getFirst())) {
            throw new ProviderResourceVerificationFailure("PROVIDER_HOST_VOLUME_READBACK_INVALID");
        }
        return true;
    }

    private void assertNoContainers(TargetIntent intent, String serverUuid, List<String> command) {
        var result = runner.run(intent, serverUuid, command);
        if (result.exitCode() != 0) {
            throw new ProviderResourceVerificationFailure("PROVIDER_HOST_CONTAINER_READBACK_FAILED");
        }
        if (!result.output().isBlank()) {
            throw new ProviderResourceVerificationFailure("PROVIDER_CONTAINER_ABSENCE_UNPROVEN");
        }
    }

    private boolean directoryExists(TargetIntent intent, String serverUuid, String hostPath) {
        var result = runner.run(intent, serverUuid, List.of("test", "-d", hostPath));
        if (result.exitCode() == 0 && result.output().isBlank()) return true;
        if (result.exitCode() == 1 && result.output().isBlank()) return false;
        throw new ProviderResourceVerificationFailure("PROVIDER_HOST_VOLUME_READBACK_FAILED");
    }

    private void remove(TargetIntent intent, String serverUuid, String name) {
        var result = runner.run(intent, serverUuid, List.of("docker", "volume", "rm", "-f", name));
        if (result.exitCode() != 0 || !name.equals(result.output().trim())) {
            throw new ProviderResourceVerificationFailure("PROVIDER_HOST_VOLUME_RECLAMATION_FAILED");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean safeAbsolutePath(String value) {
        if (blank(value) || value.indexOf('\0') >= 0 || !SAFE_ABSOLUTE_PATH.matcher(value).matches()
            || value.startsWith("//")) return false;
        try {
            var path = java.nio.file.Path.of(value);
            return path.isAbsolute() && path.normalize().toString().equals(value);
        } catch (RuntimeException error) {
            return false;
        }
    }
}
