package com.ai.fabric.runtime.review;

import ai.fabric.execution.action.ActionOutcomeProjector;
import ai.fabric.execution.action.ActionOutcomeProjectorRegistry;
import ai.fabric.execution.action.ActionOutcomeView;
import ai.fabric.intent.action.AIActionNames;
import ai.fabric.intent.action.ActionResult;
import com.ai.fabric.runtime.tasks.ManagedTasksBundle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared safe receipt projections; custom registered action projections take precedence. */
@Configuration(proxyBeanMethods = false)
public class RuntimeActionOutcomeConfiguration {
    @Bean
    @ConditionalOnMissingBean(ActionOutcomeProjectorRegistry.class)
    ActionOutcomeProjectorRegistry runtimeActionOutcomeProjectors(ObjectProvider<ReviewProposalProperties> reviews,
        ObjectProvider<ManagedTasksBundle> tasks, List<ActionOutcomeProjector> existing) {
        List<ActionOutcomeProjector> projectors = new ArrayList<>(existing);
        var selected = new LinkedHashSet<String>();
        var review = reviews.getIfAvailable();
        if (review != null && review.enabled()) selected.addAll(review.allowedActions());
        var bundle = tasks.getIfAvailable();
        if (bundle != null) bundle.tasks().forEach(task -> selected.addAll(task.writeActions()));
        for (String action : selected) {
            if (projectors.stream().anyMatch(projector -> action.equals(AIActionNames.normalize(projector.actionName())))) continue;
            projectors.add(new ActionOutcomeProjector() {
                public String actionName() { return action; }
                public ActionOutcomeView project(ActionResult result) {
                    boolean completed = result != null && result.isSuccess();
                    return new ActionOutcomeView(action,
                        completed ? "The application action completed." : "The application action did not complete.",
                        Map.of("completed", completed));
                }
            });
        }
        return new ActionOutcomeProjectorRegistry(projectors);
    }
}
