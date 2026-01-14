package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnClass(name = "com.ai.infrastructure.relationship.service.EntityRelationshipMapper")
@ConditionalOnBean(type = "com.ai.infrastructure.relationship.service.EntityRelationshipMapper")
@RequiredArgsConstructor
public class BehaviorRelationshipRegistration {

    private final ApplicationContext applicationContext;
    
    @PostConstruct
    public void registerRelationships() {
        log.info("Registering BehaviorInsights with RelationshipQuery module");
        try {
            Class<?> mapperClass = Class.forName("com.ai.infrastructure.relationship.service.EntityRelationshipMapper");
            Object relationshipMapper = applicationContext.getBean(mapperClass);
            mapperClass.getMethod("registerEntityType", Class.class).invoke(relationshipMapper, BehaviorInsights.class);
            log.info("BehaviorInsights registered as 'behavior-insight' entity type");
        } catch (Exception ex) {
            log.warn("Failed to register BehaviorInsights with RelationshipQuery module", ex);
        }
    }
}
