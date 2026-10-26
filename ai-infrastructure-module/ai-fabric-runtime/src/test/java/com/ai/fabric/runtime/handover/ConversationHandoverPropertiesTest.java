package com.ai.fabric.runtime.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ConversationHandoverPropertiesTest {

    @Test
    void preservesFiveAttemptsUnlessExplicitlyConfigured() {
        assertThat(new ConversationHandoverProperties().getDispatchMaxAttempts()).isEqualTo(5);
    }

    @Test
    void bindsManagedDeskEightAttemptConfiguration() {
        var source = new MapConfigurationPropertySource(Map.of(
            "loomai.conversation-handover.dispatch-max-attempts", "8"
        ));
        var properties = new Binder(source).bind(
            "loomai.conversation-handover", Bindable.of(ConversationHandoverProperties.class)
        ).get();

        assertThat(properties.getDispatchMaxAttempts()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 9, Integer.MAX_VALUE})
    void rejectsConfigurationOutsideFiniteDeliveryBound(int attempts) {
        assertThatThrownBy(() -> new ConversationHandoverProperties().setDispatchMaxAttempts(attempts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Conversation handover dispatch attempts must be between 1 and 8.");
    }

    @Test
    void configurationBindingRejectsAnUnboundedAttemptValue() {
        var source = new MapConfigurationPropertySource(Map.of(
            "loomai.conversation-handover.dispatch-max-attempts", "0"
        ));

        assertThatThrownBy(() -> new Binder(source).bind(
            "loomai.conversation-handover", Bindable.of(ConversationHandoverProperties.class)
        )).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
