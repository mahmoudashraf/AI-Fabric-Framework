package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SshProviderHostCommandRunnerTest {

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

    private static CustomerSelfServiceProperties properties() {
        return new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING,
            new CustomerSelfServiceProperties.TrustedServer("server", "46.224.145.148", "staging.internal")
        ), 60);
    }
}
