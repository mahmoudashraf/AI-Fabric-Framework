package com.easyluxury.ai.controller;

import com.easyluxury.ai.service.AISmartValidation;
import com.easyluxury.ai.service.ContentValidationService;
import com.easyluxury.ai.service.ValidationRuleEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SmartValidationController
 * 
 * REST controller for AI-powered smart validation operations including
 * content validation, data quality validation, and rule generation.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/validation")
@RequiredArgsConstructor
@Tag(name = "Smart Validation", description = "AI-powered smart validation services")
public class SmartValidationController {
    
    private final AIValidationService validationService;
    private final ContentValidationService contentValidationService;
    private final ValidationRuleEngine validationRuleEngine;
    
    /**
     * Validate content with AI
     */
    @PostMapping("/content")
    @Operation(summary = "Validate content with AI", description = "Validate content using AI-powered analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid content data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AIValidationService.ValidationResult> validateContent(
            @RequestBody Map<String, Object> validationRequest) {
        log.info("Validating content with AI");
        
        String content = (String) validationRequest.get("content");
        String contentType = (String) validationRequest.get("contentType");
        Map<String, Object> validationRules = (Map<String, Object>) validationRequest.get("validationRules");
        
        AIValidationService.ValidationResult result = validationService.validateContent(content, contentType, validationRules);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Generate validation rules
     */
    @PostMapping("/rules/generate")
    @Operation(summary = "Generate validation rules", description = "Generate AI-powered validation rules from data patterns")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation rules generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data for rule generation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ValidationRuleEngine.ValidationRuleSet> generateValidationRules(
            @RequestBody Map<String, Object> ruleGenerationRequest) {
        log.info("Generating validation rules for data type: {}", ruleGenerationRequest.get("dataType"));
        
        String dataType = (String) ruleGenerationRequest.get("dataType");
        List<Map<String, Object>> sampleData = (List<Map<String, Object>>) ruleGenerationRequest.get("sampleData");
        Map<String, Object> businessContext = (Map<String, Object>) ruleGenerationRequest.get("businessContext");
        
        ValidationRuleEngine.ValidationRuleSet ruleSet = validationRuleEngine.generateValidationRules(dataType, sampleData, businessContext);
        
        return ResponseEntity.ok(ruleSet);
    }
    
    /**
     * Validate data quality
     */
    @PostMapping("/data-quality")
    @Operation(summary = "Validate data quality", description = "Validate data quality using AI analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Data quality validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid data for quality validation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AIValidationService.DataQualityResult> validateDataQuality(
            @RequestBody Map<String, Object> qualityValidationRequest) {
        log.info("Validating data quality for type: {}", qualityValidationRequest.get("dataType"));
        
        List<Map<String, Object>> data = (List<Map<String, Object>>) qualityValidationRequest.get("data");
        String dataType = (String) qualityValidationRequest.get("dataType");
        
        AIValidationService.DataQualityResult result = validationService.validateDataQuality(data, dataType);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Validate business rules
     */
    @PostMapping("/business-rules")
    @Operation(summary = "Validate business rules", description = "Validate data against business rules using AI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Business rule validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid data for business rule validation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AIValidationService.BusinessRuleValidationResult> validateBusinessRules(
            @RequestBody Map<String, Object> businessRuleRequest) {
        log.info("Validating business rules");
        
        List<Map<String, Object>> data = (List<Map<String, Object>>) businessRuleRequest.get("data");
        Map<String, Object> businessRules = (Map<String, Object>) businessRuleRequest.get("businessRules");
        
        AIValidationService.BusinessRuleValidationResult result = validationService.validateBusinessRules(data, businessRules);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Validate text content
     */
    @PostMapping("/text")
    @Operation(summary = "Validate text content", description = "Validate text content with AI analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Text validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid text content"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ContentValidationService.TextValidationResult> validateTextContent(
            @RequestBody Map<String, Object> textValidationRequest) {
        log.info("Validating text content of type: {}", textValidationRequest.get("contentType"));
        
        String textContent = (String) textValidationRequest.get("textContent");
        String contentType = (String) textValidationRequest.get("contentType");
        String validationLevel = (String) textValidationRequest.getOrDefault("validationLevel", "standard");
        
        ContentValidationService.TextValidationResult result = contentValidationService.validateTextContent(textContent, contentType, validationLevel);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Validate image content
     */
    @PostMapping("/image")
    @Operation(summary = "Validate image content", description = "Validate image content metadata")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Image validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid image metadata"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ContentValidationService.ImageValidationResult> validateImageContent(
            @RequestBody Map<String, Object> imageMetadata) {
        log.info("Validating image content metadata");
        
        ContentValidationService.ImageValidationResult result = contentValidationService.validateImageContent(imageMetadata);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Validate multimedia content
     */
    @PostMapping("/multimedia")
    @Operation(summary = "Validate multimedia content", description = "Validate multimedia content metadata")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Multimedia validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid multimedia metadata"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ContentValidationService.MultimediaValidationResult> validateMultimediaContent(
            @RequestBody Map<String, Object> multimediaMetadata) {
        log.info("Validating multimedia content metadata");
        
        ContentValidationService.MultimediaValidationResult result = contentValidationService.validateMultimediaContent(multimediaMetadata);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Validate structured content
     */
    @PostMapping("/structured")
    @Operation(summary = "Validate structured content", description = "Validate structured content (JSON, XML, etc.)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Structured content validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid structured content"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ContentValidationService.StructuredContentValidationResult> validateStructuredContent(
            @RequestBody Map<String, Object> structuredValidationRequest) {
        log.info("Validating structured content of type: {}", structuredValidationRequest.get("contentType"));
        
        String structuredContent = (String) structuredValidationRequest.get("structuredContent");
        String contentType = (String) structuredValidationRequest.get("contentType");
        Map<String, Object> schema = (Map<String, Object>) structuredValidationRequest.get("schema");
        
        ContentValidationService.StructuredContentValidationResult result = contentValidationService.validateStructuredContent(structuredContent, contentType, schema);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Optimize validation rules
     */
    @PostMapping("/rules/optimize")
    @Operation(summary = "Optimize validation rules", description = "Optimize existing validation rules based on performance data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation rules optimized successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid rules for optimization"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ValidationRuleEngine.ValidationRuleSet> optimizeValidationRules(
            @RequestBody Map<String, Object> optimizationRequest) {
        log.info("Optimizing validation rules");
        
        Map<String, Object> existingRules = (Map<String, Object>) optimizationRequest.get("existingRules");
        Map<String, Object> performanceData = (Map<String, Object>) optimizationRequest.get("performanceData");
        
        ValidationRuleEngine.ValidationRuleSet optimizedRuleSet = validationRuleEngine.optimizeExistingRules(existingRules, performanceData);
        
        return ResponseEntity.ok(optimizedRuleSet);
    }
    
    /**
     * Generate dynamic validation rules
     */
    @PostMapping("/rules/dynamic")
    @Operation(summary = "Generate dynamic validation rules", description = "Generate dynamic validation rules based on real-time data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Dynamic validation rules generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data for dynamic rule generation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ValidationRuleEngine.DynamicValidationRules> generateDynamicRules(
            @RequestBody Map<String, Object> dynamicRuleRequest) {
        log.info("Generating dynamic validation rules for data type: {}", dynamicRuleRequest.get("dataType"));
        
        String dataType = (String) dynamicRuleRequest.get("dataType");
        List<Map<String, Object>> realTimeData = (List<Map<String, Object>>) dynamicRuleRequest.get("realTimeData");
        String adaptationLevel = (String) dynamicRuleRequest.getOrDefault("adaptationLevel", "moderate");
        
        ValidationRuleEngine.DynamicValidationRules dynamicRules = validationRuleEngine.generateDynamicRules(dataType, realTimeData, adaptationLevel);
        
        return ResponseEntity.ok(dynamicRules);
    }
    
    /**
     * Validate rule consistency
     */
    @PostMapping("/rules/consistency")
    @Operation(summary = "Validate rule consistency", description = "Validate consistency and conflicts in validation rules")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rule consistency validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid rule set for consistency validation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ValidationRuleEngine.RuleConsistencyAnalysis> validateRuleConsistency(
            @RequestBody ValidationRuleEngine.ValidationRuleSet ruleSet) {
        log.info("Validating rule consistency for data type: {}", ruleSet.getDataType());
        
        ValidationRuleEngine.RuleConsistencyAnalysis analysis = validationRuleEngine.validateRuleConsistency(ruleSet);
        
        return ResponseEntity.ok(analysis);
    }
    
    /**
     * Generate rule documentation
     */
    @PostMapping("/rules/documentation")
    @Operation(summary = "Generate rule documentation", description = "Generate comprehensive documentation for validation rules")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rule documentation generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid rule set for documentation generation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ValidationRuleEngine.RuleDocumentation> generateRuleDocumentation(
            @RequestBody ValidationRuleEngine.ValidationRuleSet ruleSet) {
        log.info("Generating rule documentation for data type: {}", ruleSet.getDataType());
        
        ValidationRuleEngine.RuleDocumentation documentation = validationRuleEngine.generateRuleDocumentation(ruleSet);
        
        return ResponseEntity.ok(documentation);
    }
}