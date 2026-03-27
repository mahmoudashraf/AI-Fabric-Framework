package com.ai.fabric.runtime.config;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.chat.spi.ChatSessionAccessControlPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@Configuration(proxyBeanMethods = false)
class RuntimeDevDefaultsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDevDefaultsConfiguration.class);

    @Bean
    SmartLifecycle runtimeEntityAccessPolicyRequiredValidator(RuntimeDevDefaultsProperties devDefaultsProperties,
                                                              ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider) {
        return new EntityAccessPolicyRequiredValidator(devDefaultsProperties, entityAccessPolicyProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.fabric.runtime.dev-defaults", name = "enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(EntityAccessPolicy.class)
    EntityAccessPolicy devAllowAllEntityAccessPolicy() {
        log.warn("Runtime dev-defaults are enabled: registering allow-all EntityAccessPolicy (DO NOT use in production).");
        return (userId, entity) -> true;
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.fabric.runtime.dev-defaults", name = "enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(ChatSessionAccessControlPolicy.class)
    ChatSessionAccessControlPolicy devChatSessionAccessControlPolicy() {
        return new ChatSessionAccessControlPolicy() {
            @Override
            public boolean canCreateConversation(String ownerId) {
                return StringUtils.hasText(ownerId);
            }

            @Override
            public boolean canAccessConversation(String ownerId, String conversationId) {
                return StringUtils.hasText(ownerId) && StringUtils.hasText(conversationId);
            }

            @Override
            public boolean canRecordTurn(String ownerId, String conversationId) {
                return StringUtils.hasText(ownerId) && StringUtils.hasText(conversationId);
            }

            @Override
            public boolean canDeleteConversation(String ownerId, String conversationId) {
                return StringUtils.hasText(ownerId) && StringUtils.hasText(conversationId);
            }
        };
    }

    static final class EntityAccessPolicyRequiredValidator implements SmartLifecycle {
        private final RuntimeDevDefaultsProperties devDefaultsProperties;
        private final ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider;
        private final AtomicBoolean running = new AtomicBoolean(false);

        EntityAccessPolicyRequiredValidator(RuntimeDevDefaultsProperties devDefaultsProperties,
                                            ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider) {
            this.devDefaultsProperties = devDefaultsProperties;
            this.entityAccessPolicyProvider = entityAccessPolicyProvider;
        }

        @Override
        public void start() {
            if (devDefaultsProperties != null && !devDefaultsProperties.isEnabled()) {
                EntityAccessPolicy policy = entityAccessPolicyProvider != null ? entityAccessPolicyProvider.getIfAvailable() : null;
                if (policy == null) {
                    // Fail-closed behavior is enforced by callers (access control checks deny when hooks are unavailable).
                    // Logging here makes the misconfiguration obvious without preventing the service from booting.
                    log.warn("No EntityAccessPolicy bean found and runtime dev-defaults are disabled. "
                        + "Access control checks will deny requests. For local/demo use, set ai.fabric.runtime.dev-defaults.enabled=true.");
                }
            }
            running.set(true);
        }

        @Override
        public void stop() {
            running.set(false);
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public int getPhase() {
            return Integer.MIN_VALUE;
        }
    }
}
