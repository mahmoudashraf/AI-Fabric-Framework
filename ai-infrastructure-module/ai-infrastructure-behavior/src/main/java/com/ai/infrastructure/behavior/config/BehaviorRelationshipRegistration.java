package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(EntityRelationshipMapper.class)
@RequiredArgsConstructor
public class BehaviorRelationshipRegistration {
    
    private final EntityRelationshipMapper relationshipMapper;
    
    @PostConstruct
    public void registerRelationships() {
        log.info("Registering BehaviorInsights with RelationshipQuery module");
        relationshipMapper.registerEntityType(BehaviorInsights.class);
        log.info("BehaviorInsights registered as 'behavior-insight' entity type");
    }
}
