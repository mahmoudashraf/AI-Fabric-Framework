package com.easyluxury.ai.mapper;

import com.easyluxury.ai.dto.OrderAIAnalysisRequest;
import com.easyluxury.ai.dto.OrderAIAnalysisResponse;
import com.easyluxury.ai.dto.OrderPatternRequest;
import com.easyluxury.ai.dto.OrderPatternResponse;
import com.easyluxury.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * OrderAIMapper
 * 
 * MapStruct mapper for Order AI DTOs and entities.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Mapper(componentModel = "spring")
public interface OrderAIMapper {
    
    OrderAIMapper INSTANCE = Mappers.getMapper(OrderAIMapper.class);
    
    /**
     * Map Order to OrderAIAnalysisResponse
     */
    @Mapping(target = "analysis", source = "aiAnalysis")
    @Mapping(target = "patterns", source = "aiPatterns")
    @Mapping(target = "anomalies", ignore = true)
    @Mapping(target = "riskLevel", expression = "java(order.getRiskLevel() != null ? order.getRiskLevel().name() : null)")
    @Mapping(target = "statistics", ignore = true)
    @Mapping(target = "riskFactors", ignore = true)
    @Mapping(target = "recommendations", ignore = true)
    @Mapping(target = "analysisTimestamp", ignore = true)
    @Mapping(target = "analysisType", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    OrderAIAnalysisResponse toOrderAIAnalysisResponse(Order order);
    
    /**
     * Map list of Order to list of OrderAIAnalysisResponse
     */
    List<OrderAIAnalysisResponse> toOrderAIAnalysisResponseList(List<Order> orders);
    
    /**
     * Map Order to OrderPatternResponse (for pattern analysis)
     */
    @Mapping(target = "patterns", source = "aiPatterns")
    @Mapping(target = "anomalies", ignore = true)
    @Mapping(target = "seasonalPatterns", ignore = true)
    @Mapping(target = "temporalPatterns", ignore = true)
    @Mapping(target = "valuePatterns", ignore = true)
    @Mapping(target = "statusPatterns", ignore = true)
    @Mapping(target = "paymentPatterns", ignore = true)
    @Mapping(target = "riskPatterns", ignore = true)
    @Mapping(target = "statistics", ignore = true)
    @Mapping(target = "topCategories", ignore = true)
    @Mapping(target = "topPaymentMethods", ignore = true)
    @Mapping(target = "riskFactors", ignore = true)
    @Mapping(target = "averageOrderValue", ignore = true)
    @Mapping(target = "totalRevenue", ignore = true)
    @Mapping(target = "totalOrders", ignore = true)
    @Mapping(target = "completionRate", ignore = true)
    @Mapping(target = "cancellationRate", ignore = true)
    @Mapping(target = "analysisTimestamp", ignore = true)
    @Mapping(target = "patternType", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    OrderPatternResponse toOrderPatternResponse(Order order);
    
    /**
     * Map list of Order to list of OrderPatternResponse
     */
    List<OrderPatternResponse> toOrderPatternResponseList(List<Order> orders);
}