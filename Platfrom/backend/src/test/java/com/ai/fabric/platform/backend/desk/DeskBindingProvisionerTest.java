package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.entity.*;
import com.ai.fabric.platform.backend.security.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeskBindingProvisionerTest {
    final DeskBindingRepository bindings = mock(DeskBindingRepository.class);
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final PlatformUserRepository users = mock(PlatformUserRepository.class);
    final PlatformCustomerIdentityLinkRepository identities = mock(PlatformCustomerIdentityLinkRepository.class);
    final ObjectMapper mapper = new ObjectMapper();
    final DeskBindingProvisioner provisioner = new DeskBindingProvisioner(bindings, secrets, desk, users, identities,
        new MockEnvironment().withProperty("LOOMAI_DESK_REVIEW_BRIDGE_BASE_URL", "https://console.example"), mapper);
    final Map<String, DeskBindingEntity> rows = new HashMap<>();
    final Map<String, String> storedSecrets = new HashMap<>();

    @BeforeEach void repository() {
        when(bindings.findByDeploymentIdAndTargetProfileId(anyString(), anyString())).thenAnswer(invocation ->
            Optional.ofNullable(rows.get(invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        when(bindings.saveAndFlush(any())).thenAnswer(invocation -> {
            DeskBindingEntity row = invocation.getArgument(0); rows.put(row.getDeploymentId() + ":" + row.getTargetProfileId(), row); return row;
        });
        doAnswer(invocation -> { storedSecrets.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
            .when(secrets).upsertManagedSecret(anyString(), anyString(), anyMap());
        when(secrets.resolveSecret(anyString())).thenAnswer(invocation -> storedSecrets.get(invocation.getArgument(0)));
        for (String environment : List.of("staging", "production")) {
            when(desk.connection(environment)).thenReturn(new DeskServiceClient.Connection("https://desk-" + environment + ".example", "service-key"));
        }
        var owner = new PlatformUserEntity(); owner.setId("owner"); owner.setEmail("owner@example.com"); owner.setDisplayName("Owner");
        when(users.findByCustomerIdAndRoleIgnoreCaseAndStatusIgnoreCase("customer-a", "CUSTOMER_ADMIN", "ACTIVE")).thenReturn(List.of(owner));
        var identity = new PlatformCustomerIdentityLinkEntity(); identity.setIssuer("https://supabase.example/auth/v1"); identity.setSubject("owner-subject");
        when(identities.findByPlatformUserId("owner")).thenReturn(Optional.of(identity));
    }

    @Test void firstPromotionPreparesIndependentProductionBindingWithoutPriorProductionReleaseAndRegistersResolvedCallback() {
        var deployment = deployment(); var version = version();
        provisioner.prepareForApply(deployment, version, release("staging", "rel-stage"), target("staging"));
        provisioner.registerCallback(deployment, target("staging"), "https://connector-stage.example");
        var stage = rows.get("dep-a:staging"); String originalStageKey = storedSecrets.get(stage.getProviderKeyRef());
        var expectedProduction = DeskBindingProvisioner.projection(deployment, "production");
        String immutable = version.getBehaviorConfigJson();

        // This is the normal first production apply; no earlier production binding or release is queried.
        provisioner.prepareForApply(deployment, version, release("production", "rel-first-prod"), target("production"));
        var production = rows.get("dep-a:production");
        assertThat(production.getId()).isEqualTo(expectedProduction.getId()).isNotEqualTo(stage.getId());
        assertThat(production.getProviderKeyRef()).isEqualTo(expectedProduction.getProviderKeyRef()).isNotEqualTo(stage.getProviderKeyRef());
        assertThat(production.getReleaseId()).isEqualTo("rel-first-prod");
        assertThat(production.getSourceReleaseId()).isEqualTo("rel-first-prod");
        assertThat(production.getVersionId()).isEqualTo("ver-desk");
        assertThat(storedSecrets).hasSize(8);
        assertThat(new HashSet<>(storedSecrets.values())).hasSize(8);
        assertThat(storedSecrets.get(stage.getProviderKeyRef())).isEqualTo(originalStageKey);
        assertThat(version.getBehaviorConfigJson()).isEqualTo(immutable);
        provisioner.registerCallback(deployment, target("production"), "https://connector-prod.example/");
        var registration = ArgumentCaptor.forClass(Object.class);
        verify(desk).put(eq(new DeskServiceClient.Connection("https://desk-production.example", "service-key")),
            eq("/api/desk/admin/v1/bindings/" + production.getId()), registration.capture());
        var body = mapper.valueToTree(registration.getValue());
        assertThat(body.path("targetProfileId").asText()).isEqualTo("production");
        assertThat(body.path("callbackUrl").asText()).isEqualTo("https://connector-prod.example/integrations/conversation-channel/events");
        assertThat(body.path("providerApiKey").asText()).isEqualTo(storedSecrets.get(production.getProviderKeyRef())).isNotEqualTo(originalStageKey);
        assertThat(body.path("callbackSigningSecret").asText()).isEqualTo(storedSecrets.get(production.getCallbackKeyRef()));
        assertThat(production.getStatus()).isEqualTo("APPLYING");
    }

    @Test void ordinaryUpgradeReusesTargetIdentityAndSecretsButUpdatesCurrentReleaseAndCallback() {
        var deployment = deployment(); var version = version();
        provisioner.prepareForApply(deployment, version, release("production", "rel-first"), target("production"));
        var first = rows.get("dep-a:production"); var originalSecrets = Map.copyOf(storedSecrets);
        clearInvocations(secrets);
        provisioner.prepareForApply(deployment, version, release("production", "rel-upgrade"), target("production"));
        provisioner.registerCallback(deployment, target("production"), "https://connector-new-prod.example");
        assertThat(rows.get("dep-a:production")).isSameAs(first);
        assertThat(first.getReleaseId()).isEqualTo("rel-upgrade");
        assertThat(first.getSourceReleaseId()).isEqualTo("rel-first");
        assertThat(storedSecrets).containsExactlyInAnyOrderEntriesOf(originalSecrets);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
    }

    @Test void templatesWithoutDeskHaveNoBindingSecretOrProviderEffects() {
        var version = version(); version.setBehaviorConfigJson("{}");
        provisioner.prepareForApply(deployment(), version, release("production", "rel-prod"), target("production"));
        verifyNoInteractions(bindings, secrets, desk, users, identities);
    }

    @Test void releaseTargetMismatchAndExistingOwnershipMismatchStopBeforeCredentialsOrRegistration() {
        assertThatThrownBy(() -> provisioner.prepareForApply(deployment(), version(), release("staging", "rel-stage"), target("production")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(bindings, secrets, desk);
        var row = DeskBindingProvisioner.projection(deployment(), "production"); row.setTenantId("other-tenant");
        rows.put("dep-a:production", row);
        assertThatThrownBy(() -> provisioner.prepareForApply(deployment(), version(), release("production", "rel-prod"), target("production")))
            .isInstanceOf(ResponseStatusException.class);
        verify(secrets, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
        verify(desk, never()).put(any(), anyString(), any());
    }

    private DeploymentEntity deployment() {
        var d = new DeploymentEntity(); d.setId("dep-a"); d.setCustomerId("customer-a"); d.setTenantId("tenant-a"); d.setName("Northfield"); return d;
    }
    private DeploymentVersionEntity version() {
        var v = new DeploymentVersionEntity(); v.setId("ver-desk"); v.setDeploymentId("dep-a");
        v.setBehaviorConfigJson(DeskTargetConfigurationResolverTest.managedBehavior(mapper).toString()); return v;
    }
    private DeploymentTargetProfileEntity target(String id) {
        var target = new DeploymentTargetProfileEntity(); target.setId(id); target.setEnvironmentName(id); target.setActive(true); return target;
    }
    private DeploymentReleaseEntity release(String target, String id) {
        var release = new DeploymentReleaseEntity(); release.setId(id); release.setDeploymentId("dep-a"); release.setDeploymentVersionId("ver-desk");
        release.setTargetProfileId(target); return release;
    }
}
