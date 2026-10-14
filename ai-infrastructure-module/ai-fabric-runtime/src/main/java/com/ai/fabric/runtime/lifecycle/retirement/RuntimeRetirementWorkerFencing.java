package com.ai.fabric.runtime.lifecycle.retirement;

import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.smartbrain.SmartBrainDeliveryWorker;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationWorker;
import com.ai.fabric.runtime.webhook.RuntimeActionWebhookDeliveryWorker;
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

@Configuration(proxyBeanMethods = false)
public class RuntimeRetirementWorkerFencing {
    private static final Map<Class<?>, Set<String>> BOUNDARIES = Map.of(
        RuntimeActionWebhookDeliveryWorker.class, Set.of("dispatchReadyDeliveries"),
        SmartBrainOperationWorker.class, Set.of("process"),
        SmartBrainDeliveryWorker.class, Set.of("deliver"),
        RuntimeConversationHandoverService.class, Set.of("scheduledMaintenance", "runMaintenance"),
        DocumentKnowledgeService.class, Set.of("cleanupRetentionScheduled", "reconcilePending")
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
                return service.getObject().withWorkerBoundary(!method.equals("cleanupRetentionScheduled"), () -> {
                    try { return invocation.proceed(); }
                    catch (RuntimeException | Error exception) { throw exception; }
                    catch (Throwable exception) { throw new IllegalStateException("Runtime worker failed.", exception); }
                });
            });
            Object fenced = proxy.getProxy();
            methods.forEach(method -> coverage.installed(type.getSimpleName() + "." + method));
            return fenced;
        }
    }
}
