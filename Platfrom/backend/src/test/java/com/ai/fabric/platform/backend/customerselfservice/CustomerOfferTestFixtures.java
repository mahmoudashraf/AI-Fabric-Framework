package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.security.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class CustomerOfferTestFixtures {
    private CustomerOfferTestFixtures() { }

    static Definition definition() {
        return new Definition("1", "Reviewed test offer", "reviewed-template",
            new PluginPin("template-plugin", "template-v1", "template-hash"), List.of(),
            List.of(new ArtifactPin("runtime-artifact", "artifact-hash")),
            List.of(new TargetPermission("staging-target", TargetIntent.CUSTOMER_STAGING, "staging-hash"),
                new TargetPermission("production-target", TargetIntent.CUSTOMER_PRODUCTION, "production-hash")),
            EnumSet.allOf(Operation.class),
            List.of(new SettingRule(Setting.DEPLOYMENT_NAME, ValueType.TEXT, 100, Set.of()),
                new SettingRule(Setting.APP_TITLE, ValueType.TEXT, 100, Set.of()),
                new SettingRule(Setting.AUTH_PROFILE_ID, ValueType.CHOICE, 100, Set.of("reviewed-mixed-auth"))),
            List.of(new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("reviewed-s3"))),
            Set.of(DocumentAudience.PUBLIC), EnumSet.allOf(Prerequisite.class),
            // Fictional unit-test policy values, not live measurements or admission defaults.
            new ReviewedLimits("test-measurement", 1, 1, 1000, 1024, 2048, 1, 1, 3, 1, 24, 24, 24),
            "test-reviewed-policy", "test-operating-policy", "test-identity-policy", "test-storage-policy");
    }

    static DeploymentTargetProfileEntity profile(String id, String serverId) {
        var profile = new DeploymentTargetProfileEntity();
        profile.setId(id);
        profile.setActive(true);
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("not-authority");
        profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setCredentialRefId("PRIVATE_CREDENTIAL_REF");
        profile.setProviderConfigJson("{\"serverUuid\":\"" + serverId + "\",\"destinationUuid\":\"destination\",\"private\":\"PRIVATE_PROVIDER_VALUE\"}");
        profile.setResourceDefaultsJson("{}");
        profile.setNetworkPolicyJson("{}");
        return profile;
    }

    static void authenticate(String actor, PlatformRole role, String mode) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal(actor, role, actor, mode), null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    static CustomerOfferCodec codec() {
        return new CustomerOfferCodec(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }
}
