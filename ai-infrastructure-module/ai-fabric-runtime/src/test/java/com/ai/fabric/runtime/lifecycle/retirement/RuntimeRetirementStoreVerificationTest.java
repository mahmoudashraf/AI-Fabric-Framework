package com.ai.fabric.runtime.lifecycle.retirement;

import static org.assertj.core.api.Assertions.assertThat;

import ai.fabric.chat.control.ConversationControlGateway;
import ai.fabric.chat.storage.ChatSessionActionDraftStore;
import ai.fabric.chat.storage.ConversationAwareActionDraftStore;
import ai.fabric.chat.storage.DefaultDatabaseChatSessionStorage;
import ai.fabric.intent.actiondraft.ActionDraftStore;
import ai.fabric.intent.actiondraft.InMemoryActionDraftStore;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class RuntimeRetirementStoreVerificationTest {
    @Test
    void verifiesEffectivePrimaryStoreRatherThanRequiringOnlyOneCandidate() {
        try (var context = context(true, false)) {
            var verification = new RuntimeRetirementStoreVerification(context);
            assertThat(context.getBeanNamesForType(ActionDraftStore.class)).hasSize(2);
            assertThat(verification.actionDraftsSupported(true)).isTrue();
            assertThat(verification.actionDraftsSupported(false)).isTrue();
        }
    }

    @Test
    void unsupportedPrimaryCannotBorrowAnotherCandidatesSupportedType() {
        try (var context = context(false, true)) {
            var verification = new RuntimeRetirementStoreVerification(context);
            assertThat(verification.actionDraftsSupported(true)).isFalse();
            assertThat(verification.actionDraftsSupported(false)).isFalse();
        }
    }

    @Test
    void missingAndAmbiguousProvidersRemainUnverified() {
        try (var empty = new GenericApplicationContext(); var ambiguous = context(false, false)) {
            empty.refresh();
            assertThat(new RuntimeRetirementStoreVerification(empty).actionDraftsSupported(true)).isFalse();
            assertThat(new RuntimeRetirementStoreVerification(ambiguous).actionDraftsSupported(true)).isFalse();
        }
    }

    @Test
    void legacyOnlyStoreCannotCertifyCanonicalState() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean(ActionDraftStore.class,
                () -> new ChatSessionActionDraftStore(new DefaultDatabaseChatSessionStorage(null)));
            context.refresh();
            var verification = new RuntimeRetirementStoreVerification(context);
            assertThat(verification.actionDraftsSupported(false)).isTrue();
            assertThat(verification.actionDraftsSupported(true)).isFalse();
        }
    }

    private GenericApplicationContext context(boolean canonicalPrimary, boolean memoryPrimary) {
        var context = new GenericApplicationContext();
        context.registerBean("defaultDrafts", ActionDraftStore.class, InMemoryActionDraftStore::new,
            definition -> definition.setPrimary(memoryPrimary));
        context.registerBean("canonicalDrafts", ActionDraftStore.class,
            () -> new ConversationAwareActionDraftStore(
                new ChatSessionActionDraftStore(new DefaultDatabaseChatSessionStorage(null)),
                context.getBeanProvider(ConversationControlGateway.class)),
            definition -> definition.setPrimary(canonicalPrimary));
        context.refresh();
        return context;
    }
}
