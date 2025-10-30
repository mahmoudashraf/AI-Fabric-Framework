package com.easyluxury.ai.mapper;

import com.easyluxury.ai.dto.UserAIInsightsRequest;
import com.easyluxury.ai.dto.UserAIInsightsResponse;
import com.easyluxury.ai.dto.UserBehaviorRequest;
import com.easyluxury.ai.dto.UserBehaviorResponse;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

/**
 * UserAIMapper
 * 
 * MapStruct mapper for User AI DTOs and entities.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Mapper(componentModel = "spring")
public interface UserAIMapper {
    
    UserAIMapper INSTANCE = Mappers.getMapper(UserAIMapper.class);
    
    /**
     * Map UserBehavior to UserBehaviorResponse
     */
    @Mapping(target = "behaviorType", expression = "java(userBehavior.getBehaviorType().name())")
    @Mapping(target = "metadata", ignore = true)
    UserBehaviorResponse toUserBehaviorResponse(UserBehavior userBehavior);
    
    /**
     * Map UserBehaviorRequest to UserBehavior
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "searchVector", ignore = true)
    @Mapping(target = "aiAnalysis", ignore = true)
    @Mapping(target = "aiInsights", ignore = true)
    @Mapping(target = "behaviorScore", ignore = true)
    @Mapping(target = "significanceScore", ignore = true)
    @Mapping(target = "patternFlags", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "metadata", expression = "java(request.getMetadata() != null ? request.getMetadata().toString() : null)")
    UserBehavior toUserBehavior(UserBehaviorRequest request);
    
    /**
     * Map User to UserAIInsightsResponse
     */
    @Mapping(target = "insights", source = "aiInsights")
    @Mapping(target = "recommendations", ignore = true)
    @Mapping(target = "patterns", ignore = true)
    @Mapping(target = "anomalies", ignore = true)
    @Mapping(target = "statistics", ignore = true)
    @Mapping(target = "topInterests", ignore = true)
    @Mapping(target = "topCategories", ignore = true)
    @Mapping(target = "topBrands", ignore = true)
    @Mapping(target = "analysisTimestamp", ignore = true)
    @Mapping(target = "analysisType", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    UserAIInsightsResponse toUserAIInsightsResponse(User user);
    
    /**
     * Map list of UserBehavior to list of UserBehaviorResponse
     */
    List<UserBehaviorResponse> toUserBehaviorResponseList(List<UserBehavior> userBehaviors);
    
    /**
     * Map metadata string to Map
     */
    default Map<String, Object> mapMetadata(String metadata) {
        // Simple implementation - in real scenario, you might want to parse JSON
        return metadata != null ? Map.of("raw", metadata) : Map.of();
    }
}