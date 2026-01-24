package com.ai.infrastructure.intent.action;

import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class AIActionRegistryTest {

    @Test
    void shouldDiscoverAnnotatedActionsAndNormalizeLookup() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AIActionRegistry.class);
            context.register(CancelSubscriptionAction.class);
            context.register(UpgradeSubscriptionAction.class);
            context.refresh();

            AIActionRegistry registry = context.getBean(AIActionRegistry.class);

            assertThat(registry.findHandler("cancel_subscription")).isPresent();
            assertThat(registry.findHandler("CANCEL_SUBSCRIPTION")).isPresent();
            assertThat(registry.findHandler("cancel subscription")).isPresent();
            assertThat(registry.findHandler("cancel-subscription")).isPresent();

            AIActionMetaData meta = registry.findMetadata("cancel_subscription").orElseThrow();
            assertThat(meta.getName()).isEqualTo("cancel_subscription");
            assertThat(meta.getRequiredParameters()).containsExactly("reason");
            assertThat(meta.getParameters()).containsKey("reason");
        }
    }

    @AIAction(
        name = "cancel_subscription",
        description = "Cancel my subscription",
        category = "subscription",
        requiresConfirmation = true
    )
    static class CancelSubscriptionAction {
        @ActionExecute
        public ActionResult execute(@Param(value = "reason", required = true, description = "Cancellation reason") String reason) {
            return ActionResult.builder()
                .success(true)
                .message("Cancelled: " + reason)
                .build();
        }
    }

    @AIAction(
        name = "upgrade_subscription",
        description = "Upgrade my subscription",
        category = "subscription",
        requiresConfirmation = true
    )
    static class UpgradeSubscriptionAction {
        @ActionExecute
        public ActionResult execute(@Param(value = "plan", required = true, description = "Target plan") String plan) {
            return ActionResult.builder()
                .success(true)
                .message("Upgraded to: " + plan)
                .build();
        }
    }
}

