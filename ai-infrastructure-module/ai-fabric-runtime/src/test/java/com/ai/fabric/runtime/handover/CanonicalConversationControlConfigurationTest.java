package com.ai.fabric.runtime.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.fabric.chat.control.ConversationControlGateway;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.ConversationHandoverController;
import com.ai.fabric.runtime.web.ConversationHandoverErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

class CanonicalConversationControlConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ConversationHandoverConfiguration.class,
            RuntimeConversationHandoverService.class, ConversationHandoverController.class,
            ConversationHandoverErrorHandler.class)
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(ConversationControlGateway.class, () -> mock(ConversationControlGateway.class))
        .withBean(RuntimeAdmissionActivityService.class, () -> mock(RuntimeAdmissionActivityService.class))
        .withBean(RuntimeRequestAuthResolver.class, () -> mock(RuntimeRequestAuthResolver.class))
        .withBean(ChatRuntimeController.class, () -> mock(ChatRuntimeController.class))
        .withPropertyValues(
            "loomai.conversation-handover.encryption-secret=foundation-encryption-secret-1234567890",
            "loomai.conversation-handover.callback-signing-secret=foundation-fingerprint-secret-1234567890");

    @Test
    void defaultDisabledDoesNotInstallFoundation() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RuntimeConversationHandoverService.class)
            .doesNotHaveBean(ConversationHandoverController.class)
            .doesNotHaveBean(ConversationHandoverErrorHandler.class));
    }

    @Test
    void controlOnlyInstallsExistingServiceControllerAndAdviceWithoutHumanBinding() {
        runner.withPropertyValues("loomai.conversation-control.enabled=true",
            "loomai.conversation-handover.enabled=false").run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(RuntimeConversationHandoverService.class)
                    .hasSingleBean(ConversationHandoverController.class)
                    .hasSingleBean(ConversationHandoverErrorHandler.class);
                assertThat(context.getBean(ConversationHandoverProperties.class).getChannelBindingId()).isNull();
            });
    }

    @Test
    void existingHumanFlagAloneStillInstallsFoundation() {
        runner.withPropertyValues("loomai.conversation-handover.enabled=true",
            "loomai.conversation-handover.channel-binding-id=inbox-1").run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(RuntimeConversationHandoverService.class));
    }

    @Test
    void humanFlagStillRequiresActualChannelBinding() {
        runner.withPropertyValues("loomai.conversation-control.enabled=true",
            "loomai.conversation-handover.enabled=true").run(context ->
                assertThat(context).hasFailed().getFailure()
                    .hasRootCauseMessage("LOOMAI_CONVERSATION_HANDOVER_CHANNEL_BINDING_ID is required when handover is enabled."));
    }

    @Test
    void controlOnlyStillRequiresBothDistinctDedicatedSecrets() {
        for (String secret : java.util.List.of("encryption-secret", "callback-signing-secret")) {
            runner.withPropertyValues("loomai.conversation-control.enabled=true",
                "loomai.conversation-handover." + secret + "=").run(context ->
                    assertThat(context).hasFailed());
        }
        runner.withPropertyValues("loomai.conversation-control.enabled=true",
            "loomai.conversation-handover.callback-signing-secret=foundation-encryption-secret-1234567890")
            .run(context -> assertThat(context).hasFailed().getFailure()
                .hasRootCauseMessage("Conversation handover encryption and callback secrets must be distinct."));
    }

    @Test
    void packagedYamlMapsIndependentFlagSecretsAndExistingHumanFallback() throws Exception {
        var sources = new YamlPropertySourceLoader().load("runtime", new ClassPathResource("application.yml"));
        for (boolean human : java.util.List.of(false, true)) {
            var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("LOOMAI_CONVERSATION_HANDOVER_ENABLED", Boolean.toString(human));
            sources.forEach(source -> environment.getPropertySources().addLast(source));
            assertThat(environment.getProperty("ai.chat.control.enabled", Boolean.class)).isEqualTo(human);
            assertThat(environment.getProperty("loomai.conversation-control.enabled", Boolean.class)).isEqualTo(human);
            environment.setProperty("LOOMAI_CONVERSATION_CONTROL_ENABLED", "true");
            environment.setProperty("LOOMAI_CONVERSATION_CONTROL_ENCRYPTION_SECRET", "control-encryption");
            environment.setProperty("LOOMAI_CONVERSATION_CONTROL_FINGERPRINT_SECRET", "control-fingerprint");
            assertThat(environment.getProperty("ai.chat.control.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("loomai.conversation-control.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("loomai.conversation-handover.encryption-secret")).isEqualTo("control-encryption");
            assertThat(environment.getProperty("loomai.conversation-handover.callback-signing-secret")).isEqualTo("control-fingerprint");
            environment.setProperty("LOOMAI_CONVERSATION_HANDOVER_ENCRYPTION_SECRET", "human-encryption");
            environment.setProperty("LOOMAI_CONVERSATION_HANDOVER_CALLBACK_SIGNING_SECRET", "human-signing");
            assertThat(environment.getProperty("loomai.conversation-handover.encryption-secret")).isEqualTo("human-encryption");
            assertThat(environment.getProperty("loomai.conversation-handover.callback-signing-secret")).isEqualTo("human-signing");
        }
    }
}
