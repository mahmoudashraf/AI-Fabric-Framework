package com.ai.infrastructure.intent.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionHandlerRegistryTest {

    private TestActionHandler cancelHandler;
    private TestActionHandler upgradeHandler;

    @BeforeEach
    void setUp() {
        cancelHandler = new TestActionHandler("cancel_subscription");
        upgradeHandler = new TestActionHandler("upgrade_subscription");
    }

    @Test
    void shouldIndexHandlersByActionName() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(cancelHandler, upgradeHandler));
        registry.initialize();

        assertThat(registry.findHandler("cancel_subscription")).isPresent();
        assertThat(registry.findHandler("CANCEL_SUBSCRIPTION")).isPresent();
        assertThat(registry.findMetadata("upgrade_subscription"))
            .isPresent()
            .get()
            .extracting(AIActionMetaData::getName)
            .isEqualTo("upgrade_subscription");
    }

    @Test
    void shouldIgnoreHandlersWithMissingMetadata() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(
            new TestActionHandler(null),
            cancelHandler
        ));
        registry.initialize();

        assertThat(registry.getHandlerMap()).hasSize(1);
        assertThat(registry.findHandler("cancel_subscription")).isPresent();
    }

    private static final class TestActionHandler implements ActionHandler {

        private final String name;

        private TestActionHandler(String name) {
            this.name = name;
        }

        @Override
        public AIActionMetaData getActionMetadata() {
            if (name == null) {
                return null;
            }
            return AIActionMetaData.builder()
                .name(name)
                .description("desc")
                .parameters(Map.of())
                .build();
        }

        @Override
        public boolean validateActionAllowed(String userId) {
            return true;
        }

        @Override
        public String getConfirmationMessage(Map<String, Object> params) {
            return "Confirm?";
        }

        @Override
        public ActionResult executeAction(Map<String, Object> params, String userId) {
            return ActionResult.builder().success(true).message("ok").build();
        }

        @Override
        public ActionResult handleError(Exception e, String userId) {
            return ActionResult.builder().success(false).message(e.getMessage()).build();
        }
    }
}
