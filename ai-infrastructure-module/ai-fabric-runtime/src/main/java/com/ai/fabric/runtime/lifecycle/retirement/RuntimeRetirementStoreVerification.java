package com.ai.fabric.runtime.lifecycle.retirement;

import ai.fabric.chat.control.ConversationControlStore;
import ai.fabric.chat.control.JdbcConversationControlStore;
import ai.fabric.chat.storage.ChatSessionActionDraftStore;
import ai.fabric.chat.storage.ConversationAwareActionDraftStore;
import ai.fabric.intent.actiondraft.ActionDraftStore;
import ai.fabric.execution.chain.state.JdbcSpecialistChainExecutionRepository;
import ai.fabric.execution.chain.state.SpecialistChainExecutionRepository;
import ai.fabric.execution.review.persistence.*;
import ai.fabric.execution.state.DurableExecutionRepository;
import ai.fabric.execution.state.JdbcDurableExecutionRepository;
import com.ai.fabric.runtime.documents.repository.DocumentWorkRepository;
import com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryRepository;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationRepository;
import com.ai.fabric.runtime.webhook.ActionWebhookDeliveryRepository;
import org.quartz.Scheduler;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class RuntimeRetirementStoreVerification {
    private final ListableBeanFactory beans;
    public RuntimeRetirementStoreVerification(ListableBeanFactory beans) { this.beans = beans; }

    public boolean supported(RuntimeRetirementTopology.Feature feature) {
        return switch (feature) {
            case CHAT_SESSIONS -> true; // Verified separately against the actual fenced session and pending stores.
            case CONVERSATION_HANDOVER -> provider(ConversationControlStore.class, JdbcConversationControlStore.class);
            case HUMAN_REVIEW -> provider(ReviewTaskRepository.class, JdbcReviewTaskRepository.class)
                && provider(ReviewDispatchRepository.class, JdbcReviewDispatchRepository.class);
            case SPECIALIST_EXECUTION -> provider(DurableExecutionRepository.class, JdbcDurableExecutionRepository.class);
            case SPECIALIST_CHAINS -> provider(SpecialistChainExecutionRepository.class, JdbcSpecialistChainExecutionRepository.class);
            case SMART_BRAIN -> provider(SmartBrainOperationRepository.class, SimpleJpaRepository.class)
                && provider(SmartBrainDeliveryRepository.class, SimpleJpaRepository.class);
            case DOCUMENTS -> provider(DocumentWorkRepository.class, SimpleJpaRepository.class);
            case ACTION_WEBHOOK -> provider(ActionWebhookDeliveryRepository.class, SimpleJpaRepository.class);
            case INDEXING -> provider(ai.fabric.repository.IndexingQueueRepository.class, SimpleJpaRepository.class);
            case SCHEDULER -> persistentScheduler();
        };
    }
    public boolean actionDraftsSupported(boolean canonical) {
        return provider(ActionDraftStore.class, ConversationAwareActionDraftStore.class)
            || !canonical && provider(ActionDraftStore.class, ChatSessionActionDraftStore.class);
    }
    private boolean provider(Class<?> type, Class<?> supportedType) {
        try {
            Object provider = beans.getBeanProvider(type).getIfAvailable();
            return provider != null && AopUtils.getTargetClass(provider).equals(supportedType);
        } catch (NoUniqueBeanDefinitionException ambiguous) {
            return false;
        }
    }
    private boolean persistentScheduler() {
        String[] names = beans.getBeanNamesForType(Scheduler.class);
        if (names.length != 1) return false;
        try { return ((Scheduler) beans.getBean(names[0])).getMetaData().isJobStoreSupportsPersistence(); }
        catch (Exception exception) { return false; }
    }
}
