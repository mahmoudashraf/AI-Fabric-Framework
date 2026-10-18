package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.aiworkspace.service.*;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerWorkspaceProfileValidatorTest {
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final AIWorkspaceExperiencePackRegistry packs = mock(AIWorkspaceExperiencePackRegistry.class);
    private final AIWorkspaceConnectionProfileRegistry connections = spy(new AIWorkspaceConnectionProfileRegistry(
        new PlatformAIWorkspaceProperties(null, false, 0, null, null, null, null, null)));
    private final AIWorkspaceAuthenticationProfileRegistry authentication = spy(new AIWorkspaceAuthenticationProfileRegistry(
        new com.fasterxml.jackson.databind.ObjectMapper()));
    private final CustomerWorkspaceProfileValidator validator = new CustomerWorkspaceProfileValidator(codec,
        new AIWorkspaceConfigurationValidator(new PlatformAIWorkspaceProperties(null, false, 0, null, null, null, null, null)),
        packs, connections, authentication);
    private final ObjectNode interaction = (ObjectNode) codec.tree("""
        {"packCode":"generic-workspace","packVersion":"1.0.0","connectionMode":"PUBLIC_RUNTIME_ANONYMOUS",
         "connectionProfileCode":"runtime-anonymous-direct","connectionProfileVersion":"1.0.0",
         "deliveryMode":"STANDALONE","connectionConfiguration":{},
         "configuration":{"app":{"assistantLabel":"Support"}}}
        """);
    private final ObjectNode theme = (ObjectNode) codec.tree("{\"primaryColor\":\"#123456\"}");
    private final ObjectNode auth = (ObjectNode) codec.tree("""
        {"profileCode":"supabase","profileVersion":"1.0.0","interactionPolicy":"PUBLIC_WITH_PROTECTED_CAPABILITIES",
         "configuration":{"projectUrl":"https://identity.test","publishableKey":"sb_publishable_fixture","methods":["email-password"]}}
        """);

    @Test void exactPublicPayloadsArePinnedAndPassedToExistingWorkspaceValidators() {
        Definition definition = definition();
        validator.validate(definition);
        verify(packs).validateConfiguration(eq("generic-workspace"), eq("1.0.0"), any());
        verify(connections).resolve(eq("runtime-anonymous-direct"), eq("1.0.0"), any(), any());
        verify(authentication).resolve(eq("supabase"), eq("1.0.0"), any(), any());
        var restarted = codec.read(codec.write(definition), Definition.class);
        assertThat(codec.hash(restarted)).isEqualTo(codec.hash(definition));
        assertThat(restarted.workspaceProfiles()).hasSize(1);
    }

    @Test void changingAProfileWithoutChangingItsReviewedPinIsRejected() {
        var original = definition();
        ObjectNode node = (ObjectNode) codec.tree(codec.write(original));
        ((ObjectNode) node.path("workspaceProfiles").get(0).path("theme")).put("primaryColor", "#ffffff");
        assertThatThrownBy(() -> validator.validate(codec.read(node.toString(), Definition.class)))
            .isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void aSecretCannotBePublishedEvenWhenItsHashHasBeenReviewed() {
        ((ObjectNode) auth.path("configuration")).put("clientSecret", "must-not-be-exposed");
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void connectionTokensAndPrivilegedProviderKeysAreRejectedByRealRegistries() {
        ((ObjectNode) interaction.path("connectionConfiguration")).put("accessToken", "private-token");
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
        ((ObjectNode) interaction.path("connectionConfiguration")).remove("accessToken");
        ((ObjectNode) auth.path("configuration")).put("publishableKey", "sb_secret_must-not-be-exposed");
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void nestedThemesAndNonCanonicalProviderConfigurationCannotProduceUnusablePins() {
        ((ObjectNode) interaction.path("configuration")).set("theme", theme.deepCopy());
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
        ((ObjectNode) interaction.path("configuration")).remove("theme");
        ((ObjectNode) auth.path("configuration")).put("projectUrl", "https://IDENTITY.test/");
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void unknownInteractionFieldsAndInvalidAuthModeFailClosed() {
        interaction.put("runtimeUrl", "https://unreviewed-runtime.test");
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
        interaction.remove("runtimeUrl");
        auth.put("interactionPolicy", "LOGIN_REQUIRED");
        assertThatThrownBy(() -> validator.validate(definition())).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void duplicateProfileIdentifiersAreRejected() {
        var node = (ObjectNode) codec.tree(codec.write(definition()));
        var profiles = (com.fasterxml.jackson.databind.node.ArrayNode) node.path("workspaceProfiles");
        profiles.add(profiles.get(0).deepCopy());
        assertThatThrownBy(() -> validator.validate(codec.read(node.toString(), Definition.class)))
            .isInstanceOf(CustomerSelfServiceException.class);
    }

    private Definition definition() {
        var base = CustomerOfferTestFixtures.definition();
        var profiles = List.of(new WorkspaceProfile("support", "Support workspace", interaction, theme, auth));
        var settings = List.of(
            new SettingRule(Setting.INTERACTION_PROFILE_ID, ValueType.CHOICE, 100, java.util.Set.of("workspace:" + codec.hash(interaction))),
            new SettingRule(Setting.THEME_PROFILE_ID, ValueType.CHOICE, 100, java.util.Set.of("theme:" + codec.hash(theme))),
            new SettingRule(Setting.AUTH_PROFILE_ID, ValueType.CHOICE, 100, java.util.Set.of("auth:" + codec.hash(auth))));
        return new Definition(base.schemaVersion(), base.name(), base.templateId(), base.template(), base.plugins(),
            base.artifacts(), base.targets(), base.operations(), settings, base.bindingSlots(), base.documentAudiences(),
            base.admissionPrerequisites(), base.limits(), base.topology(), base.approvedPolicyRef(),
            base.operatingPolicyRef(), base.identityPolicyRef(), base.storagePolicyRef(), profiles);
    }
}
