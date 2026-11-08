package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeveloperManagedResourcePolicyTest {
    @Test void managedResourceScopeRejectsProductionBeforeAnyProviderOrOwnershipWrite() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new ManagedResourceOwnershipService(jdbc, new ObjectMapper(), mock(PlatformTransactionManager.class));
        var targets = mock(DeploymentTargetProfileRepository.class);
        ReflectionTestUtils.setField(service, "targetProfiles", targets);
        var deployment = new DeploymentEntity(); deployment.setId("developer"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant"); deployment.restrictToStaging();
        var target = new DeploymentTargetProfileEntity(); target.setEnvironmentName("production");
        when(targets.findById("target")).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.scope(deployment, "target")).hasMessageContaining("DEPLOYMENT_STAGING_ONLY");
        verifyNoInteractions(jdbc);
        target.setEnvironmentName("staging");
        assertThat(service.scope(deployment, "target").targetProfileId()).isEqualTo("target");
    }
}
