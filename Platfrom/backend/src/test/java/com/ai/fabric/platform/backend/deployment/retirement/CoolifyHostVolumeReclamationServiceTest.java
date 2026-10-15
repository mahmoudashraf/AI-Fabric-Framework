package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoolifyHostVolumeReclamationServiceTest {

    @Test
    void removesDisposableVolumeAndProvesRetainedHostDirectory() {
        Set<String> volumes = new HashSet<>(Set.of("runtime-data"));
        Set<String> directories = Set.of("/srv/loomai/documents");
        List<List<String>> commands = new ArrayList<>();
        var service = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
            commands.add(List.copyOf(command));
            if (command.size() >= 3 && command.subList(0, 3).equals(List.of("docker", "volume", "ls"))) {
                String name = command.getLast().substring("name=^".length(), command.getLast().length() - 1);
                return new ProviderHostCommandRunner.Result(0, volumes.contains(name) ? name : "");
            }
            if (command.size() >= 4 && command.subList(0, 4).equals(List.of("docker", "volume", "rm", "-f"))) {
                String name = command.getLast();
                volumes.remove(name);
                return new ProviderHostCommandRunner.Result(0, name);
            }
            if (command.size() >= 2 && command.subList(0, 2).equals(List.of("test", "-d"))) {
                return new ProviderHostCommandRunner.Result(directories.contains(command.getLast()) ? 0 : 1, "");
            }
            throw new AssertionError(command);
        });

        var receipt = service.reclaimAndVerify(TargetIntent.CUSTOMER_STAGING, "server", "operation",
            List.of(new CoolifyHostVolumeReclamationService.Volume("runtime", "runtime-data", false),
                new CoolifyHostVolumeReclamationService.Volume("documents", null,
                    "/srv/loomai/documents", true)));

        assertThat(receipt.reclaimedUuids()).containsExactly("runtime");
        assertThat(volumes).isEmpty();
        assertThat(commands).contains(List.of("test", "-d", "/srv/loomai/documents"));
    }

    @Test
    void alreadyAbsentDisposableVolumeIsIdempotent() {
        var service = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) ->
            new ProviderHostCommandRunner.Result(0, ""));

        var receipt = service.reclaimAndVerify(TargetIntent.CUSTOMER_PRODUCTION, "server", "retry",
            List.of(new CoolifyHostVolumeReclamationService.Volume("runtime", "runtime-data", false)));

        assertThat(receipt.reclaimedUuids()).containsExactly("runtime");
    }

    @Test
    void ambiguousHostReadbackAndMissingRetainedDirectoryFailClosed() {
        var ambiguous = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) ->
            new ProviderHostCommandRunner.Result(0, "runtime-data\nunexpected"));
        assertThatThrownBy(() -> ambiguous.reclaimAndVerify(TargetIntent.CUSTOMER_STAGING, "server", "operation",
            List.of(new CoolifyHostVolumeReclamationService.Volume("runtime", "runtime-data", false))))
            .hasMessageContaining("PROVIDER_HOST_VOLUME_READBACK_INVALID");

        var missing = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) ->
            new ProviderHostCommandRunner.Result(1, ""));
        assertThatThrownBy(() -> missing.reclaimAndVerify(TargetIntent.CUSTOMER_STAGING, "server", "operation",
            List.of(new CoolifyHostVolumeReclamationService.Volume("documents", null,
                "/srv/loomai/documents", true))))
            .hasMessageContaining("PERSISTENT_VOLUME_RETENTION_UNPROVEN");
    }

    @Test
    void unsafeNamesAndPathsAreRejectedBeforeHostAccess() {
        var service = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
            throw new AssertionError("host access should not occur");
        });

        assertThatThrownBy(() -> service.reclaimAndVerify(TargetIntent.CUSTOMER_STAGING, "server", "operation",
            List.of(new CoolifyHostVolumeReclamationService.Volume("runtime", "bad name", false))))
            .hasMessageContaining("PROVIDER_VOLUME_RECLAMATION_SCOPE_INVALID");
        assertThatThrownBy(() -> service.reclaimAndVerify(TargetIntent.CUSTOMER_STAGING, "server", "operation",
            List.of(new CoolifyHostVolumeReclamationService.Volume("documents", null,
                "../documents", true))))
            .hasMessageContaining("PROVIDER_VOLUME_RECLAMATION_SCOPE_INVALID");
    }

    @Test
    void provesCurrentAndLegacyContainerOwnershipLabelsAreAbsent() {
        List<List<String>> commands = new ArrayList<>();
        var service = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
            commands.add(List.copyOf(command));
            return new ProviderHostCommandRunner.Result(0, "");
        });

        var receipt = service.verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server",
            "handle", "application", "app-uuid");

        assertThat(receipt.reference()).isEqualTo("provider-host-container-absence:handle");
        assertThat(commands).containsExactly(
            List.of("docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationUuid=app-uuid"),
            List.of("docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationId",
                "--filter", "label=com.docker.compose.project=app-uuid"),
            List.of("docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationId",
                "--filter", "name=app-uuid")
        );
    }

    @Test
    void anyOwnedContainerFailsAbsenceProof() {
        var service = new CoolifyHostVolumeReclamationService((intent, serverUuid, command) ->
            new ProviderHostCommandRunner.Result(0, "container-id"));

        assertThatThrownBy(() -> service.verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING,
            "server", "handle", "database", "database-uuid"))
            .hasMessageContaining("PROVIDER_CONTAINER_ABSENCE_UNPROVEN");
    }
}
