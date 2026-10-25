package com.ai.fabric.runtime.lifecycle.retirement;

import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationWorker;
import com.ai.fabric.runtime.webhook.RuntimeActionWebhookDeliveryWorker;
import ai.fabric.indexing.queue.IndexingQueueService;
import ai.fabric.indexing.worker.AsyncIndexingWorker;
import ai.fabric.indexing.worker.BatchIndexingWorker;
import ai.fabric.indexing.worker.IndexingCleanupScheduler;
import ai.fabric.indexing.worker.IndexingWorkProcessor;
import ai.fabric.indexing.worker.SyncIndexingRetryWorker;
import java.util.Map;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
public class RuntimeRetirementWorkerFencing {
    private static final Map<Class<?>, Set<String>> BOUNDARIES = Map.ofEntries(
        Map.entry(RuntimeActionWebhookDeliveryWorker.class, Set.of("dispatchReadyDeliveries")),
        Map.entry(SmartBrainOperationWorker.class, Set.of("process")),
        Map.entry(SmartBrainDeliveryWorker.class, Set.of("deliver")),
        Map.entry(RuntimeConversationHandoverService.class, Set.of("scheduledMaintenance", "runMaintenance")),
        Map.entry(DocumentKnowledgeService.class, Set.of("cleanupRetentionScheduled", "reconcilePending")),
        Map.entry(IndexingWorkProcessor.class, Set.of("process")),
        Map.entry(IndexingQueueService.class, Set.of("enqueue", "enqueueForSynchronousDispatch", "lease", "claimSynchronous",
            "markCompleted", "markFailure", "markSuperseded", "resetStuckEntries", "releaseOrphanedSynchronousEntries",
            "purgeCompletedOlderThan", "purgeDeadLettersOlderThan")),
        Map.entry(AsyncIndexingWorker.class, Set.of("run")),
        Map.entry(BatchIndexingWorker.class, Set.of("run")),
        Map.entry(SyncIndexingRetryWorker.class, Set.of("run")),
        Map.entry(IndexingCleanupScheduler.class, Set.of("reclaimStuckEntries", "purgeOldEntries"))
    );
    @Bean
    static BeanPostProcessor runtimeRetirementWorkerPostProcessor(ObjectProvider<RuntimeTargetRetirementService> service,
        RuntimeRetirementFencingCoverage coverage) {
        return new WorkerProcessor(service, coverage);
    }

    private static final class WorkerProcessor implements BeanPostProcessor, Ordered {
        private final ObjectProvider<RuntimeTargetRetirementService> service;
        private final RuntimeRetirementFencingCoverage coverage;
        private WorkerProcessor(ObjectProvider<RuntimeTargetRetirementService> service, RuntimeRetirementFencingCoverage coverage) {
            this.service = service;
            this.coverage = coverage;
        }
        // ScheduledAnnotationBeanPostProcessor must register the already-fenced worker.
        public int getOrder() { return 0; }
        public Object postProcessAfterInitialization(Object bean, String name) {
            Class<?> type = AopUtils.getTargetClass(bean);
            Set<String> methods = BOUNDARIES.get(type);
            if (methods == null) return bean;
            ProxyFactory proxy = new ProxyFactory(bean);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice((MethodInterceptor) invocation -> {
                String method = invocation.getMethod().getName();
                if (!methods.contains(method)) return invocation.proceed();
                try {
                    return service.getObject().withObservedWorkerBoundary(type.getSimpleName() + "." + method,
                        !Set.of("cleanupRetentionScheduled", "purgeOldEntries", "purgeCompletedOlderThan", "purgeDeadLettersOlderThan").contains(method), () -> {
                            try { return invocation.proceed(); }
                            catch (RuntimeException | Error exception) { throw exception; }
                            catch (Throwable exception) { throw new IllegalStateException("Runtime worker failed.", exception); }
                        });
                } catch (ResponseStatusException blocked) {
                    if (invocation.getMethod().getReturnType() == void.class
                        && Set.of("RUNTIME_TARGET_AUTHORITY_FENCED", "TARGET_NEW_WORK_BLOCKED").contains(blocked.getReason())) return null;
                    throw blocked;
                }
            });
            Object fenced = proxy.getProxy();
            methods.forEach(method -> coverage.installed(type.getSimpleName() + "." + method));
            return fenced;
        }
    }
}
