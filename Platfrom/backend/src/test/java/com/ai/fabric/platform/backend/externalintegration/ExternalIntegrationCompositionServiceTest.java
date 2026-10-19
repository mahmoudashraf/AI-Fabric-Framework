package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationCompositionServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ordinaryDeploymentDoesNotAcquireACustomerOfferOrExternalBusinessDependency() throws Exception {
        var f = new ExternalIntegrationFixture();
        var offers = mock(CustomerOfferRepository.class);
        when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.empty());
        var service = service(f, offers);
        assertThat(service.profiles(f.deployment)).isEmpty();
        assertThat(service.evidence(f.deployment)).isEmpty();
        verifyNoInteractions(offers);
    }

    @Test void immutableReviewedProfileCompilesACopyWithoutLeakingOrPersistingTargetCredentials() throws Exception {
        var f = new ExternalIntegrationFixture();
        var offers = associatedOffer(f);
        var service = service(f, offers);
        var install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("install"); install.setDeploymentId(f.deployment.getId()); install.setPluginId(f.profile.pluginId());
        install.setPluginVersionId(f.profile.pluginVersionId()); install.setStatus("ENABLED");
        install.setConfigJson("{}"); install.setSecretRefsJson("{}");
        var profiles = service.profiles(f.deployment);
        var compiled = service.compileInput(profiles, install);
        assertThat(compiled).isNotSameAs(install);
        assertThat(install.getConfigJson()).isEqualTo("{}");
        assertThat(install.getSecretRefsJson()).isEqualTo("{}");
        assertThat(f.codec.tree(compiled.getConfigJson()).path("endpointUrl").asText())
            .isEqualTo("${" + ExternalIntegrationCompilerResolver.alias(f.profile, "endpointUrl") + "}");
        assertThat(f.codec.tree(compiled.getSecretRefsJson()).path("apiKey").asText())
            .isEqualTo(ExternalIntegrationCompilerResolver.alias(f.profile, "apiKey"));
        assertThat(service.evidence(f.deployment)).containsExactly(f.compiler.compilerEvidence(f.profile));
        assertThat(ExternalIntegrationRequirement.required(f.codec.tree(f.version.getCompositionProvenanceJson()))).isTrue();
        verifyNoInteractions(f.vault);
        assertThat(f.dnsCalls).hasValue(0);
    }

    @Test void foreignAssociationTamperedOfferAndChangedPluginVersionFailClosed() throws Exception {
        var f = new ExternalIntegrationFixture();
        var offers = associatedOffer(f);
        var service = service(f, offers);
        var install = new DeploymentMarketplacePluginInstallEntity(); install.setPluginId(f.profile.pluginId());
        install.setPluginVersionId("new-version");
        assertThatThrownBy(() -> service.compileInput(service.profiles(f.deployment), install)).hasMessageContaining("COMPILER_PROFILE_MISMATCH");
        when(offers.findById("offer-generic")).thenReturn(Optional.of(new CustomerOfferEntity("offer-generic", "{}",
            f.codec.hash(f.definition), "review", "staff", Instant.now())));
        assertThatThrownBy(() -> service.profiles(f.deployment)).hasMessageContaining("OFFER_CHANGED");
        when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            f.deployment.getId(), "foreign-customer", f.deployment.getTenantId(), "offer-generic", f.target.getId(),
            f.codec.hash(f.definition), 3, "bootstrap", Instant.now())));
        assertThatThrownBy(() -> service.profiles(f.deployment)).hasMessageContaining("OFFER_SCOPE_REQUIRED");
    }

    @Test void malformedCompilerMarkerCannotDisableTheBindingGate() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(ExternalIntegrationRequirement.required(mapper.readTree("{}"))).isFalse();
        assertThat(ExternalIntegrationRequirement.required(mapper.readTree("{\"externalIntegrationProfiles\":[]}"))).isFalse();
        for (String value : List.of("null", "{}", "false", "\"ignore\"")) {
            assertThatThrownBy(() -> ExternalIntegrationRequirement.required(mapper.readTree("{\"externalIntegrationProfiles\":" + value + "}")))
                .hasMessageContaining("COMPILER_EVIDENCE_INVALID");
        }
    }

    private CustomerOfferRepository associatedOffer(ExternalIntegrationFixture f) {
        var offers = mock(CustomerOfferRepository.class);
        String hash = f.codec.hash(f.definition);
        when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            f.deployment.getId(), f.deployment.getCustomerId(), f.deployment.getTenantId(), "offer-generic", f.target.getId(), hash,
            3, "bootstrap", Instant.now())));
        when(offers.findById("offer-generic")).thenReturn(Optional.of(new CustomerOfferEntity("offer-generic", f.codec.write(f.definition),
            hash, "review", "staff", Instant.now())));
        return offers;
    }

    private ExternalIntegrationCompositionService service(ExternalIntegrationFixture f, CustomerOfferRepository offers) {
        return new ExternalIntegrationCompositionService(f.associations, offers, f.policy, f.compiler, f.codec, f.mapper);
    }
}
