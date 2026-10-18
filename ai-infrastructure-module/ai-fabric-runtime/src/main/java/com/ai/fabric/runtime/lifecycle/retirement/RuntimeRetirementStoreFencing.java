package com.ai.fabric.runtime.lifecycle.retirement;

import ai.fabric.chat.control.*;
import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.storage.DefaultDatabaseChatSessionStorage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeRetirementStoreFencing {
    @Bean
    static BeanPostProcessor runtimeRetirementStorePostProcessor(ObjectProvider<RuntimeTargetRetirementService> service,
        RuntimeRetirementFencingCoverage coverage) {
        return new BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof ChatSessionStorageProvider store && !(bean instanceof FencedSessions)) {
                    return new FencedSessions(store, service);
                }
                if (bean instanceof ConversationControlGateway gateway && !(bean instanceof FencedControl)) {
                    coverage.installed("conversation-control");
                    coverage.installed("conversation-orchestration-state");
                    return new FencedControl(gateway, service);
                }
                return bean;
            }
        };
    }

    public static final class FencedSessions implements ChatSessionStorageProvider {
        private final ChatSessionStorageProvider delegate;
        private final ObjectProvider<RuntimeTargetRetirementService> service;

        public FencedSessions(ChatSessionStorageProvider delegate, ObjectProvider<RuntimeTargetRetirementService> service) {
            this.delegate = delegate;
            this.service = service;
        }
        public boolean databaseBacked() { return AopUtils.getTargetClass(delegate) == DefaultDatabaseChatSessionStorage.class; }
        public ChatSession save(ChatSession session) {
            return service.getObject().withConversationWrite(session.getId(), session.getOwnerId(), () -> delegate.save(session));
        }
        public Optional<ChatSession> findById(String conversationId) { return delegate.findById(conversationId); }
        public List<ChatSession> findByOwnerId(String ownerId) { return delegate.findByOwnerId(ownerId); }
        public void deleteById(String conversationId) {
            Optional<ChatSession> session = delegate.findById(conversationId);
            if (session.isEmpty()) return;
            service.getObject().withExistingConversation(conversationId, session.get().getOwnerId(), () -> {
                delegate.deleteById(conversationId);
                return null;
            });
        }
    }

    private static final class FencedControl implements ConversationControlGateway {
        private final ConversationControlGateway delegate;
        private final ObjectProvider<RuntimeTargetRetirementService> service;
        private FencedControl(ConversationControlGateway delegate, ObjectProvider<RuntimeTargetRetirementService> service) {
            this.delegate = delegate;
            this.service = service;
        }
        private <T> T write(ConversationScope scope, Supplier<T> action) {
            RuntimeTargetRetirementService retirement = service.getObject();
            retirement.requireConversationScope(scope.deploymentId(), scope.tenantId());
            return retirement.withConversationWrite(scope.conversationId(), scope.ownerId(), action);
        }
        private <T> T existing(ConversationScope scope, Supplier<T> action) {
            RuntimeTargetRetirementService retirement = service.getObject();
            retirement.requireConversationScope(scope.deploymentId(), scope.tenantId());
            return retirement.withExistingConversation(scope.conversationId(), scope.ownerId(), action);
        }
        public ConversationControlSnapshot status(ConversationScope scope) { return existing(scope, () -> delegate.status(scope)); }
        public ConversationControlResult transition(ConversationControlCommand command) {
            return command.type() == ConversationControlCommandType.CLOSE
                ? existing(command.scope(), () -> delegate.transition(command))
                : write(command.scope(), () -> delegate.transition(command));
        }
        public ConversationTurnLease acquireAiTurnLease(ConversationScope scope, Duration ttl) { return write(scope, () -> delegate.acquireAiTurnLease(scope, ttl)); }
        public void verifyAiTurnLease(ConversationTurnLease lease) {
            write(lease.scope(), () -> { delegate.verifyAiTurnLease(lease); return null; });
        }
        public ConversationMessage appendMessage(ConversationMessageCommand command, ConversationTurnLease lease) {
            return write(command.scope(), () -> delegate.appendMessage(command, lease));
        }
        public List<ConversationMessage> messages(ConversationScope scope, long afterSequence, int limit) {
            service.getObject().requireConversationScope(scope.deploymentId(), scope.tenantId());
            return delegate.messages(scope, afterSequence, limit);
        }
        public Optional<ConversationOrchestrationState> orchestrationState(
            ConversationScope scope,
            long expectedControlEpoch
        ) {
            return existing(scope, () -> delegate.orchestrationState(scope, expectedControlEpoch));
        }
        public ConversationOrchestrationState updateOrchestrationState(
            ConversationScope scope,
            long expectedControlEpoch,
            UnaryOperator<Map<String, Object>> mutation
        ) {
            return write(scope, () -> delegate.updateOrchestrationState(
                scope,
                expectedControlEpoch,
                mutation
            ));
        }
        public void clearOrchestrationState(ConversationScope scope, long expectedControlEpoch) {
            existing(scope, () -> {
                delegate.clearOrchestrationState(scope, expectedControlEpoch);
                return null;
            });
        }
    }
}
