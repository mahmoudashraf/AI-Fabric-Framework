package com.ai.fabric.platform.backend.deployment.provider;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SshProviderHostCommandRunnerTest {

    @Test void attendedStopAllowsOnlyTheSameOwnedRunningContainerReadbacks() {
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of("docker", "ps", "--quiet", "--filter",
            "label=coolify.applicationUuid=owned-app"))).isTrue();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of("docker", "ps", "--quiet", "--filter",
            "label=coolify.databaseId", "--filter", "label=com.docker.compose.project=owned-db"))).isTrue();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of("docker", "ps", "--quiet", "--filter",
            "label=coolify.applicationId", "--filter", "name=owned-app"))).isTrue();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of("docker", "ps", "--quiet", "--filter", "name=anything"))).isFalse();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of("docker", "ps", "--quiet", "--filter",
            "label=coolify.applicationUuid=owned;id"))).isFalse();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of("docker", "stop", "owned-app"))).isFalse();
    }

    @TempDir
    Path temporaryDirectory;


    @Test
    void disabledVerificationFailsBeforeReadingSecrets() {
        var secrets = mock(PlatformSecretService.class);
        var runner = new SshProviderHostCommandRunner(
            new ProviderHostVolumeReclamationProperties(false, Duration.ofSeconds(1), Map.of()),
            properties(), secrets);

        assertThatThrownBy(() -> runner.run(TargetIntent.CUSTOMER_STAGING, "server",
            List.of("docker", "volume", "ls", "--quiet", "--filter", "name=^runtime-data$")))
            .hasMessageContaining("PROVIDER_HOST_VERIFICATION_DISABLED");
        verifyNoInteractions(secrets);
    }

    @Test
    void missingCredentialMaterialFailsClosedWithoutStartingSsh() {
        var secrets = mock(PlatformSecretService.class);
        when(secrets.resolveSecret("ssh-key")).thenReturn("");
        when(secrets.resolveSecret("known-hosts")).thenReturn("");
        var runner = new SshProviderHostCommandRunner(
            new ProviderHostVolumeReclamationProperties(true, Duration.ofSeconds(1), Map.of(
                TargetIntent.CUSTOMER_STAGING,
                new ProviderHostVolumeReclamationProperties.Host("root", 22, "ssh-key", "known-hosts")
            )), properties(), secrets);

        assertThatThrownBy(() -> runner.run(TargetIntent.CUSTOMER_STAGING, "server",
            List.of("docker", "volume", "ls", "--quiet", "--filter", "name=^runtime-data$")))
            .hasMessageContaining("PROVIDER_HOST_VERIFICATION_CREDENTIAL_MISSING");
    }

    @Test
    void mismatchedServerAuthorityFailsBeforeReadingSecrets() {
        var secrets = mock(PlatformSecretService.class);
        var runner = new SshProviderHostCommandRunner(
            new ProviderHostVolumeReclamationProperties(true, Duration.ofSeconds(1), Map.of(
                TargetIntent.CUSTOMER_STAGING,
                new ProviderHostVolumeReclamationProperties.Host("root", 22, "ssh-key", "known-hosts")
            )), properties(), secrets);

        assertThatThrownBy(() -> runner.run(TargetIntent.CUSTOMER_STAGING, "other-server",
            List.of("docker", "volume", "ls", "--quiet", "--filter", "name=^runtime-data$")))
            .hasMessageContaining("PROVIDER_HOST_VERIFICATION_CONFIGURATION_INVALID");
        verifyNoInteractions(secrets);
    }

    @Test
    void onlyExactContainerOwnershipReadbacksAreAllowed() {
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of(
            "docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationUuid=app-uuid"))).isTrue();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of(
            "docker", "ps", "-a", "--quiet", "--filter", "label=coolify.databaseId",
            "--filter", "label=com.docker.compose.project=database-uuid"))).isTrue();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of(
            "docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationId",
            "--filter", "name=app-uuid"))).isTrue();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of(
            "docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationId",
            "--filter", "name=^app-uuid"))).isFalse();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of(
            "docker", "ps", "-a", "--quiet", "--filter", "name=anything"))).isFalse();
        assertThat(SshProviderHostCommandRunner.safeCommand(List.of(
            "docker", "ps", "-a", "--quiet", "--filter", "label=coolify.applicationUuid=app;id"))).isFalse();
    }

    @Test
    void privateKeyWriterOnlyAppendsMissingFinalLfAndKeepsOwnerOnlyPermissions() throws IOException {
        Path keyFile = temporaryDirectory.resolve("identity");
        for (String material : List.of("synthetic\n  body  \nlast-line",
            "  synthetic\r\n\tbody \r\nlast-line\r", "synthetic \t")) {
            SshProviderHostCommandRunner.writePrivateKey(keyFile, material);

            assertThat(Arrays.equals(Files.readAllBytes(keyFile),
                (material + "\n").getBytes(StandardCharsets.UTF_8)))
                .as("only the required terminal LF is appended").isTrue();
            assertThat(Files.getPosixFilePermissions(keyFile)).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        }
    }

    @Test
    void privateKeyWriterPreservesAlreadyTerminatedMaterialByteForByte() throws IOException {
        Path keyFile = temporaryDirectory.resolve("identity");
        for (String material : List.of("synthetic\nbody\n", "synthetic\r\nbody\r\n", "synthetic\nbody\n\n")) {
            SshProviderHostCommandRunner.writePrivateKey(keyFile, material);

            assertThat(Arrays.equals(Files.readAllBytes(keyFile), material.getBytes(StandardCharsets.UTF_8)))
                .as("existing LF termination and all internal bytes remain unchanged").isTrue();
        }
    }

    @Test
    void privateKeyWriterRejectsNullEmptyAndWhitespaceBeforeTouchingFiles() throws IOException {
        Path missing = temporaryDirectory.resolve("missing-identity");
        Path existing = temporaryDirectory.resolve("existing-identity");
        Files.writeString(existing, "synthetic-sentinel", StandardCharsets.UTF_8);
        for (String material : Arrays.asList(null, "", " ", "\r\n\t ", "\u2003")) {
            for (Path target : List.of(missing, existing)) {
                assertThatThrownBy(() -> SshProviderHostCommandRunner.writePrivateKey(target, material))
                    .isInstanceOfSatisfying(ProviderResourceVerificationFailure.class, error ->
                        assertThat(error.getReason()).isEqualTo("PROVIDER_HOST_VERIFICATION_CREDENTIAL_MISSING"));
            }
            assertThat(Files.exists(missing)).isFalse();
            assertThat(Files.readString(existing, StandardCharsets.UTF_8)).isEqualTo("synthetic-sentinel");
        }
    }

    @Test
    void managedSecretTrimmedSyntheticEd25519KeyParsesWithoutNetworkAccess() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/ssh-keygen")), "local ssh-keygen is required");
        Path generated = temporaryDirectory.resolve("synthetic-generated");
        assertThat(runLocalKeygen("-q", "-t", "ed25519", "-N", "", "-C", "offline-retirement-test",
            "-f", generated.toString())).isZero();
        String managedSecretValue = Files.readString(generated, StandardCharsets.UTF_8).trim();
        assertThat(managedSecretValue.endsWith("\n")).isFalse();
        Path identity = temporaryDirectory.resolve("synthetic-materialized");

        SshProviderHostCommandRunner.writePrivateKey(identity, managedSecretValue);

        assertThat(Arrays.equals(Files.readAllBytes(identity),
            (managedSecretValue + "\n").getBytes(StandardCharsets.UTF_8)))
            .as("materialized synthetic key differs only by its restored final LF").isTrue();
        assertThat(runLocalKeygen("-y", "-P", "", "-f", identity.toString())).isZero();
    }

    private static int runLocalKeygen(String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<>(List.of("/usr/bin/ssh-keygen"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command)
            .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("local key-format check completes").isTrue();
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static CustomerSelfServiceProperties properties() {
        return new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING,
            new CustomerSelfServiceProperties.TrustedServer("server", "46.224.145.148", "staging.internal")
        ), 60);
    }
}
