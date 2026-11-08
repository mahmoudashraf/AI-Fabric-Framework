package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.model.DeploymentEnvironmentPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitTestFixture.*;
import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExperienceKitDeveloperEntryTest {
    private final ExperienceKitTestFixture f = new ExperienceKitTestFixture();

    @Test void absentEntryPointKeepsBusinessKitsUnrestricted() {
        var result = f.service.create(f.request("standalone", false));
        assertThat(f.request("standalone", false).entryPoint()).isEqualTo(EntryPoint.CUSTOMER);
        assertThat(f.deployment.getEnvironmentPolicy()).isEqualTo(DeploymentEnvironmentPolicy.UNRESTRICTED);
        assertThat(result.deploymentPolicy().allowedEnvironments()).containsExactly("staging", "production");
        assertThat(result.deploymentPolicy().tags()).isEmpty();
        verify(f.deployments, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"ai-customer-experience", "ai-support-experience"})
    void developerEntryPersistsCanonicalPolicyAndProjectsItInKit(String code) {
        configure(code);
        var result = f.service.create(request(code, EntryPoint.DEVELOPER));
        assertThat(f.deployment.getEnvironmentPolicy()).isEqualTo(DeploymentEnvironmentPolicy.STAGING_ONLY);
        assertThat(result.deploymentPolicy().allowedEnvironments()).containsExactly("staging");
        assertThat(result.deploymentPolicy().tags()).containsExactly("staging-only");
        verify(f.deployments).saveAndFlush(f.deployment);
        var order = inOrder(f.bootstrap, f.deployments, f.staging);
        order.verify(f.bootstrap).bootstrap(eq(OFFER), any());
        order.verify(f.deployments).findById("kit-deployment");
        order.verify(f.deployments).saveAndFlush(f.deployment);
        order.verify(f.staging).configure(eq(OFFER), eq("kit-deployment"), any());
        assertThat(f.service.create(request(code, EntryPoint.DEVELOPER)).deploymentPolicy()).isEqualTo(result.deploymentPolicy());
        assertThatThrownBy(() -> f.service.create(request(code, EntryPoint.CUSTOMER)))
            .hasMessageContaining("operation key");
    }

    @Test void developerEntryCannotUseUnapprovedKitOrCreateAnyResources() {
        assertThatThrownBy(() -> f.service.create(request(DEALERSHIP, EntryPoint.DEVELOPER)))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(f.bootstrap, f.consumers, f.staging, f.workspaces);
    }

    @Test void developerCannotSubmitAProductionTargetByCallingTheApiDirectly() {
        configure(GENERIC);
        when(f.catalogue.offer(OFFER)).thenReturn(f.offer(OFFER, "template-version", f.profile("generic-workspace"), TARGET,
            TargetIntent.CUSTOMER_PRODUCTION));
        assertThatThrownBy(() -> f.service.create(request(GENERIC, EntryPoint.DEVELOPER)))
            .hasMessageContaining("customer-staging");
        verifyNoInteractions(f.bootstrap, f.consumers, f.staging, f.workspaces);
    }

    @Test void invalidEntryPointIsNotSilentlyConvertedToCustomer() {
        assertThatThrownBy(() -> f.mapper.readValue("{\"entryPoint\":\"OTHER\"}", CreateRequest.class))
            .hasMessageContaining("EntryPoint");
    }

    private void configure(String code) {
        String pack = GENERIC.equals(code) ? "generic-workspace" : "support";
        when(f.definitions.findById(code)).thenReturn(Optional.of(f.definition(code, pack, List.of("developer-template"))));
        f.version("template-version", "developer-template");
        when(f.catalogue.offer(OFFER)).thenReturn(f.offer(OFFER, "template-version", f.profile(pack), TARGET,
            TargetIntent.CUSTOMER_STAGING));
    }

    private CreateRequest request(String code, EntryPoint entry) {
        var r = f.request("standalone", false);
        return new CreateRequest(code, r.name(), r.offerId(), r.targetProfileId(), r.workspaceProfileId(),
            r.aiStateStorageMode(), r.workspaceName(), r.brandColor(), r.deliveryMode(), r.appSlug(), r.websiteOrigin(),
            r.humanSupportEnabled(), r.operationKey(), r.ticketingEnabled(), r.ticketProvider(), entry);
    }
}
