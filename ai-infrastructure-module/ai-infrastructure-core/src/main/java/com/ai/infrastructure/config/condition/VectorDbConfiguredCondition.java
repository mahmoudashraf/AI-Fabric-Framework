package com.ai.infrastructure.config.condition;

import com.ai.infrastructure.rag.VectorDatabaseService;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Activates vector-related infrastructure when {@code ai.vector-db.type} is set.
 */
public class VectorDbConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String type = context.getEnvironment().getProperty("ai.vector-db.type");
        if (StringUtils.hasText(type) && !"false".equalsIgnoreCase(type.trim())) {
            return true;
        }

        if (context.getBeanFactory() instanceof ListableBeanFactory listableBeanFactory) {
            String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                listableBeanFactory,
                VectorDatabaseService.class,
                false,
                false
            );
            return beanNames.length > 0;
        }

        return false;
    }
}
