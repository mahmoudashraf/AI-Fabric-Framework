package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderCredentialRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentTargetProfileMigrationTest {

    @Autowired
    private DeploymentTargetProfileRepository targetProfileRepository;

    @Autowired
    private DeploymentProviderCredentialRepository providerCredentialRepository;

    @Test
    void migrationSeedsRailwayDefaultsAndInactiveCoolifyProfiles() {
        assertThat(targetProfileRepository.findById("dtp-railway-stub-default"))
            .hasValueSatisfying(profile -> {
                assertThat(profile.getProviderType()).isEqualTo(DeploymentProviderType.RAILWAY_STUB);
                assertThat(profile.isActive()).isTrue();
                assertThat(profile.isDefaultForRuntime()).isTrue();
                assertThat(profile.getSourceStrategy()).isEqualTo("GIT_SOURCE");
            });
        assertThat(targetProfileRepository.findById("dtp-railway-api-default"))
            .hasValueSatisfying(profile -> {
                assertThat(profile.getProviderType()).isEqualTo(DeploymentProviderType.RAILWAY_API);
                assertThat(profile.isActive()).isTrue();
                assertThat(profile.isDefaultForRuntime()).isTrue();
                assertThat(profile.getSourceStrategy()).isEqualTo("GIT_SOURCE");
            });
        assertThat(targetProfileRepository.findById("dtp-coolify-staging"))
            .hasValueSatisfying(profile -> {
                assertThat(profile.getProviderType()).isEqualTo(DeploymentProviderType.COOLIFY);
                assertThat(profile.isActive()).isFalse();
                assertThat(profile.isPlatformServicesAllowed()).isFalse();
                assertThat(profile.getSourceStrategy()).isEqualTo("IMAGE_SOURCE");
                assertThat(profile.getProviderConfigJson()).contains("id069t43frp519u5i3dg2jpr");
                assertThat(profile.getProviderConfigJson()).contains("46.224.145.148.sslip.io");
            });
        assertThat(targetProfileRepository.findById("dtp-coolify-production"))
            .hasValueSatisfying(profile -> {
                assertThat(profile.getProviderType()).isEqualTo(DeploymentProviderType.COOLIFY);
                assertThat(profile.isActive()).isFalse();
                assertThat(profile.isPlatformServicesAllowed()).isFalse();
                assertThat(profile.getSourceStrategy()).isEqualTo("IMAGE_SOURCE");
                assertThat(profile.getProviderConfigJson()).contains("t1400k32bg9yd764chyt1slm");
                assertThat(profile.getProviderConfigJson()).contains("46.225.162.106.sslip.io");
            });
        assertThat(providerCredentialRepository.findById("dpc-coolify-staging"))
            .hasValueSatisfying(credential -> {
                assertThat(credential.getProviderType()).isEqualTo(DeploymentProviderType.COOLIFY);
                assertThat(credential.getStatus()).isEqualTo("PENDING_SECRET");
            });
    }
}
