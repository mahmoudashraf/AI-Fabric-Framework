package com.ai.infrastructure.validation;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.rag.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * AI Validation Service
 * 
 * Generic AI-powered validation service that provides intelligent data validation,
 * content analysis, and automatic rule generation using machine learning.
 * 
 * This service can be used by any application for AI-powered validation tasks.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIValidationService {
    
    private final AICoreService aiCoreService;
    private final RAGService ragService;
    
    /**
     * Validate content using AI-powered analysis
     * 
     * @param content the content to validate
     * @param contentType the type of content
     * @param validationRules custom validation rules
     * @return validation result with AI insights
     */
    public ValidationResult validateContent(String content, String contentType, Map<String, Object> validationRules) {
        try {
            log.debug("Validating content of type: {} with AI", contentType);
            
            // Perform AI-powered content analysis
            String aiAnalysis = analyzeContentWithAI(content, contentType);
            
            // Apply traditional validation rules
            List<ValidationError> traditionalErrors = applyTraditionalValidation(content, contentType, validationRules);
            
            // Apply AI-powered validation rules
            List<ValidationError> aiErrors = applyAIValidation(content, contentType, aiAnalysis);
            
            // Combine all errors
            List<ValidationError> allErrors = new ArrayList<>();
            allErrors.addAll(traditionalErrors);
            allErrors.addAll(aiErrors);
            
            // Generate validation insights
            String validationInsights = generateValidationInsights(content, contentType, allErrors, aiAnalysis);
            
            // Calculate validation score
            double validationScore = calculateValidationScore(content, allErrors);
            
            // Create validation result
            ValidationResult result = ValidationResult.builder()
                .isValid(allErrors.isEmpty())
                .errors(allErrors)
                .aiAnalysis(aiAnalysis)
                .validationInsights(validationInsights)
                .validationScore(validationScore)
                .contentType(contentType)
                .validatedAt(new Date())
                .build();
            
            log.debug("Content validation completed with score: {}", validationScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating content of type: {}", contentType, e);
            throw new RuntimeException("Failed to validate content", e);
        }
    }
    
    /**
     * Generate smart validation rules based on content patterns
     * 
     * @param contentType the type of content
     * @param sampleData sample data for analysis
     * @return generated validation rules
     */
    public Map<String, Object> generateValidationRules(String contentType, List<String> sampleData) {
        try {
            log.debug("Generating validation rules for content type: {}", contentType);
            
            // Analyze sample data patterns
            Map<String, Object> patterns = analyzeDataPatterns(sampleData);
            
            // Generate AI-powered validation rules
            String aiRules = generateAIRules(contentType, sampleData, patterns);
            
            // Parse AI rules into structured format
            Map<String, Object> structuredRules = parseAIRules(aiRules);
            
            // Add traditional rules based on patterns
            Map<String, Object> traditionalRules = generateTraditionalRules(patterns);
            
            // Combine all rules
            Map<String, Object> allRules = new HashMap<>();
            allRules.put("aiRules", structuredRules);
            allRules.put("traditionalRules", traditionalRules);
            allRules.put("contentType", contentType);
            allRules.put("generatedAt", new Date());
            
            log.debug("Successfully generated validation rules for content type: {}", contentType);
            
            return allRules;
            
        } catch (Exception e) {
            log.error("Error generating validation rules for content type: {}", contentType, e);
            throw new RuntimeException("Failed to generate validation rules", e);
        }
    }
    
    /**
     * Validate data quality using AI
     * 
     * @param data the data to validate
     * @param dataType the type of data
     * @return data quality validation result
     */
    public DataQualityResult validateDataQuality(List<Map<String, Object>> data, String dataType) {
        try {
            log.debug("Validating data quality for type: {}", dataType);
            
            // Analyze data completeness
            CompletenessAnalysis completeness = analyzeDataCompleteness(data);
            
            // Analyze data consistency
            ConsistencyAnalysis consistency = analyzeDataConsistency(data);
            
            // Analyze data accuracy
            AccuracyAnalysis accuracy = analyzeDataAccuracy(data, dataType);
            
            // Generate AI-powered quality insights
            String qualityInsights = generateQualityInsights(data, completeness, consistency, accuracy);
            
            // Calculate overall quality score
            double qualityScore = calculateQualityScore(completeness, consistency, accuracy);
            
            // Create quality result
            DataQualityResult result = DataQualityResult.builder()
                .overallScore(qualityScore)
                .completeness(completeness)
                .consistency(consistency)
                .accuracy(accuracy)
                .qualityInsights(qualityInsights)
                .dataType(dataType)
                .validatedAt(new Date())
                .build();
            
            log.debug("Data quality validation completed with score: {}", qualityScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating data quality for type: {}", dataType, e);
            throw new RuntimeException("Failed to validate data quality", e);
        }
    }
    
    /**
     * Validate business rules using AI
     * 
     * @param data the data to validate
     * @param businessRules the business rules to apply
     * @return business rule validation result
     */
    public BusinessRuleValidationResult validateBusinessRules(List<Map<String, Object>> data, Map<String, Object> businessRules) {
        try {
            log.debug("Validating business rules with AI");
            
            // Apply traditional business rules
            List<BusinessRuleError> traditionalErrors = applyTraditionalBusinessRules(data, businessRules);
            
            // Generate AI-powered business rule suggestions
            String aiSuggestions = generateBusinessRuleSuggestions(data, businessRules);
            
            // Apply AI-enhanced business rules
            List<BusinessRuleError> aiErrors = applyAIBusinessRules(data, businessRules, aiSuggestions);
            
            // Combine all errors
            List<BusinessRuleError> allErrors = new ArrayList<>();
            allErrors.addAll(traditionalErrors);
            allErrors.addAll(aiErrors);
            
            // Generate business rule insights
            String businessInsights = generateBusinessRuleInsights(data, allErrors, aiSuggestions);
            
            // Create validation result
            BusinessRuleValidationResult result = BusinessRuleValidationResult.builder()
                .isValid(allErrors.isEmpty())
                .errors(allErrors)
                .aiSuggestions(aiSuggestions)
                .businessInsights(businessInsights)
                .validatedAt(new Date())
                .build();
            
            log.debug("Business rule validation completed with {} errors", allErrors.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating business rules", e);
            throw new RuntimeException("Failed to validate business rules", e);
        }
    }
    
    /**
     * Analyze content with AI
     */
    private String analyzeContentWithAI(String content, String contentType) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Analyze the following %s content for quality, accuracy, and compliance: %s", 
                        contentType, content))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to analyze content with AI", e);
            return "AI analysis unavailable";
        }
    }
    
    /**
     * Apply traditional validation
     */
    private List<ValidationError> applyTraditionalValidation(String content, String contentType, Map<String, Object> rules) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Length validation
        if (rules.containsKey("maxLength")) {
            int maxLength = (Integer) rules.get("maxLength");
            if (content.length() > maxLength) {
                errors.add(ValidationError.builder()
                    .field("content")
                    .message("Content exceeds maximum length of " + maxLength)
                    .severity("ERROR")
                    .build());
            }
        }
        
        // Pattern validation
        if (rules.containsKey("pattern")) {
            String pattern = (String) rules.get("pattern");
            if (!Pattern.matches(pattern, content)) {
                errors.add(ValidationError.builder()
                    .field("content")
                    .message("Content does not match required pattern")
                    .severity("ERROR")
                    .build());
            }
        }
        
        return errors;
    }
    
    /**
     * Apply AI validation
     */
    private List<ValidationError> applyAIValidation(String content, String contentType, String aiAnalysis) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Simple AI validation based on analysis
        if (aiAnalysis.contains("inappropriate") || aiAnalysis.contains("spam")) {
            errors.add(ValidationError.builder()
                .field("content")
                .message("Content flagged as inappropriate by AI analysis")
                .severity("WARNING")
                .build());
        }
        
        if (aiAnalysis.contains("low quality") || aiAnalysis.contains("poor")) {
            errors.add(ValidationError.builder()
                .field("content")
                .message("Content quality is low according to AI analysis")
                .severity("WARNING")
                .build());
        }
        
        return errors;
    }
    
    /**
     * Generate validation insights
     */
    private String generateValidationInsights(String content, String contentType, List<ValidationError> errors, String aiAnalysis) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Generate validation insights for %s content. Errors: %s, AI Analysis: %s", 
                        contentType, 
                        errors.stream().map(e -> e.getMessage()).collect(java.util.stream.Collectors.joining(", ")),
                        aiAnalysis))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to generate validation insights", e);
            return "Validation insights unavailable";
        }
    }
    
    /**
     * Calculate validation score
     */
    private double calculateValidationScore(String content, List<ValidationError> errors) {
        double baseScore = 1.0;
        
        // Deduct points for errors
        for (ValidationError error : errors) {
            if ("ERROR".equals(error.getSeverity())) {
                baseScore -= 0.2;
            } else if ("WARNING".equals(error.getSeverity())) {
                baseScore -= 0.1;
            }
        }
        
        return Math.max(0.0, baseScore);
    }
    
    /**
     * Analyze data patterns
     */
    private Map<String, Object> analyzeDataPatterns(List<String> sampleData) {
        Map<String, Object> patterns = new HashMap<>();
        
        // Length patterns
        List<Integer> lengths = sampleData.stream()
            .map(String::length)
            .collect(java.util.stream.Collectors.toList());
        
        patterns.put("avgLength", lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0));
        patterns.put("minLength", lengths.stream().mapToInt(Integer::intValue).min().orElse(0));
        patterns.put("maxLength", lengths.stream().mapToInt(Integer::intValue).max().orElse(0));
        
        // Character patterns
        patterns.put("containsNumbers", sampleData.stream().anyMatch(s -> s.matches(".*\\d.*")));
        patterns.put("containsSpecialChars", sampleData.stream().anyMatch(s -> s.matches(".*[^a-zA-Z0-9\\s].*")));
        
        return patterns;
    }
    
    /**
     * Generate AI rules
     */
    private String generateAIRules(String contentType, List<String> sampleData, Map<String, Object> patterns) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Generate validation rules for %s content based on these patterns: %s", 
                        contentType, patterns.toString()))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to generate AI rules", e);
            return "AI rules generation unavailable";
        }
    }
    
    /**
     * Parse AI rules
     */
    private Map<String, Object> parseAIRules(String aiRules) {
        Map<String, Object> rules = new HashMap<>();
        
        // Simple parsing - in real implementation, this would be more sophisticated
        rules.put("aiGenerated", true);
        rules.put("rules", aiRules);
        rules.put("confidence", 0.8);
        
        return rules;
    }
    
    /**
     * Generate traditional rules
     */
    private Map<String, Object> generateTraditionalRules(Map<String, Object> patterns) {
        Map<String, Object> rules = new HashMap<>();
        
        if (patterns.containsKey("maxLength")) {
            rules.put("maxLength", (Integer) patterns.get("maxLength") + 100);
        }
        
        if (patterns.containsKey("minLength")) {
            rules.put("minLength", Math.max(0, (Integer) patterns.get("minLength") - 10));
        }
        
        return rules;
    }
    
    /**
     * Analyze data completeness
     */
    private CompletenessAnalysis analyzeDataCompleteness(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return CompletenessAnalysis.builder()
                .completenessScore(1.0)
                .missingFields(0)
                .totalFields(0)
                .build();
        }

        Set<String> allKeys = data.stream()
            .flatMap(record -> record.keySet().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (allKeys.isEmpty()) {
            return CompletenessAnalysis.builder()
                .completenessScore(1.0)
                .missingFields(0)
                .totalFields(0)
                .build();
        }

        int missingFields = 0;

        for (Map<String, Object> record : data) {
            for (String key : allKeys) {
                Object value = record.get(key);
                if (value == null) {
                    missingFields++;
                } else if (value instanceof CharSequence sequence && sequence.toString().trim().isEmpty()) {
                    missingFields++;
                }
            }
        }

        int totalCells = data.size() * allKeys.size();
        double completenessScore = totalCells == 0 ? 1.0 : 1.0 - (double) missingFields / totalCells;

        return CompletenessAnalysis.builder()
            .completenessScore(completenessScore)
            .missingFields(missingFields)
            .totalFields(allKeys.size())
            .build();
    }
    
    /**
     * Analyze data consistency
     */
    private ConsistencyAnalysis analyzeDataConsistency(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return ConsistencyAnalysis.builder()
                .consistencyScore(1.0)
                .inconsistencies(0)
                .totalRecords(0)
                .build();
        }

        Set<String> allKeys = data.stream()
            .flatMap(record -> record.keySet().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (allKeys.isEmpty()) {
            return ConsistencyAnalysis.builder()
                .consistencyScore(1.0)
                .inconsistencies(0)
                .totalRecords(data.size())
                .build();
        }

        Map<String, Class<?>> canonicalTypes = new HashMap<>();
        int typeMismatches = 0;
        int evaluatedCells = 0;

        for (String key : allKeys) {
            for (Map<String, Object> record : data) {
                if (!record.containsKey(key)) {
                    continue;
                }
                Object value = record.get(key);
                if (value == null) {
                    continue;
                }

                evaluatedCells++;
                Class<?> normalized = normalizeType(value);
                Class<?> canonical = canonicalTypes.putIfAbsent(key, normalized);
                if (canonical != null && !canonical.equals(normalized)) {
                    typeMismatches++;
                }
            }
        }

        double consistencyScore = evaluatedCells == 0 ? 1.0 : 1.0 - (double) typeMismatches / evaluatedCells;

        return ConsistencyAnalysis.builder()
            .consistencyScore(consistencyScore)
            .inconsistencies(typeMismatches)
            .totalRecords(data.size())
            .build();
    }
    
    /**
     * Analyze data accuracy
     */
    private AccuracyAnalysis analyzeDataAccuracy(List<Map<String, Object>> data, String dataType) {
        if (data.isEmpty()) {
            return AccuracyAnalysis.builder()
                .accuracyScore(1.0)
                .errors(0)
                .totalRecords(0)
                .build();
        }

        int totalEvaluatedValues = 0;
        int suspectValues = 0;

        for (Map<String, Object> record : data) {
            for (Object value : record.values()) {
                if (value == null) {
                    continue;
                }
                totalEvaluatedValues++;
                if (isSuspectValue(value)) {
                    suspectValues++;
                }
            }
        }

        double accuracyScore = totalEvaluatedValues == 0 ? 1.0 : 1.0 - (double) suspectValues / totalEvaluatedValues;

        return AccuracyAnalysis.builder()
            .accuracyScore(accuracyScore)
            .errors(suspectValues)
            .totalRecords(data.size())
            .build();
    }
    
    /**
     * Generate quality insights
     */
    private String generateQualityInsights(List<Map<String, Object>> data, CompletenessAnalysis completeness, 
                                         ConsistencyAnalysis consistency, AccuracyAnalysis accuracy) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Generate data quality insights. Completeness: %.2f, Consistency: %.2f, Accuracy: %.2f", 
                        completeness.getCompletenessScore(), consistency.getConsistencyScore(), accuracy.getAccuracyScore()))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to generate quality insights", e);
            return "Quality insights unavailable";
        }
    }
    
    /**
     * Calculate quality score
     */
    private double calculateQualityScore(CompletenessAnalysis completeness, ConsistencyAnalysis consistency, AccuracyAnalysis accuracy) {
        return (completeness.getCompletenessScore() + consistency.getConsistencyScore() + accuracy.getAccuracyScore()) / 3.0;
    }

    private Class<?> normalizeType(Object value) {
        if (value instanceof Number) {
            return Number.class;
        }
        if (value instanceof CharSequence) {
            return CharSequence.class;
        }
        if (value instanceof Collection) {
            return Collection.class;
        }
        if (value instanceof Map) {
            return Map.class;
        }
        return value.getClass();
    }

    private static final Set<String> SUSPECT_STRING_TOKENS = Set.of("n/a", "na", "unknown", "undefined", "none", "null");

    private boolean isSuspectValue(Object value) {
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            return Double.isNaN(numericValue) || Double.isInfinite(numericValue);
        }
        if (value instanceof CharSequence sequence) {
            String normalized = sequence.toString().trim();
            if (normalized.isEmpty()) {
                return true;
            }
            return SUSPECT_STRING_TOKENS.contains(normalized.toLowerCase(Locale.ROOT));
        }
        return false;
    }
    
    /**
     * Apply traditional business rules
     */
    private List<BusinessRuleError> applyTraditionalBusinessRules(List<Map<String, Object>> data, Map<String, Object> rules) {
        List<BusinessRuleError> errors = new ArrayList<>();
        
        // Simple business rule validation
        for (Map<String, Object> record : data) {
            if (rules.containsKey("requiredFields")) {
                List<String> requiredFields = (List<String>) rules.get("requiredFields");
                for (String field : requiredFields) {
                    if (!record.containsKey(field) || record.get(field) == null) {
                        errors.add(BusinessRuleError.builder()
                            .rule("requiredFields")
                            .message("Required field " + field + " is missing")
                            .severity("ERROR")
                            .build());
                    }
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Generate business rule suggestions
     */
    private String generateBusinessRuleSuggestions(List<Map<String, Object>> data, Map<String, Object> rules) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Suggest business rules for this data. Current rules: %s, Data sample: %s", 
                        rules.toString(), data.stream().limit(5).collect(java.util.stream.Collectors.toList()).toString()))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to generate business rule suggestions", e);
            return "Business rule suggestions unavailable";
        }
    }
    
    /**
     * Apply AI business rules
     */
    private List<BusinessRuleError> applyAIBusinessRules(List<Map<String, Object>> data, Map<String, Object> rules, String aiSuggestions) {
        List<BusinessRuleError> errors = new ArrayList<>();
        
        // Simple AI business rule validation
        if (aiSuggestions.contains("anomaly") || aiSuggestions.contains("suspicious")) {
            errors.add(BusinessRuleError.builder()
                .rule("aiAnomalyDetection")
                .message("AI detected potential anomalies in data")
                .severity("WARNING")
                .build());
        }
        
        return errors;
    }
    
    /**
     * Generate business rule insights
     */
    private String generateBusinessRuleInsights(List<Map<String, Object>> data, List<BusinessRuleError> errors, String aiSuggestions) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Generate business rule insights. Errors: %s, AI Suggestions: %s", 
                        errors.stream().map(e -> e.getMessage()).collect(java.util.stream.Collectors.joining(", ")),
                        aiSuggestions))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to generate business rule insights", e);
            return "Business rule insights unavailable";
        }
    }
    
    // Inner classes for validation results
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private boolean isValid;
        private List<ValidationError> errors;
        private String aiAnalysis;
        private String validationInsights;
        private double validationScore;
        private String contentType;
        private Date validatedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String message;
        private String severity;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataQualityResult {
        private double overallScore;
        private CompletenessAnalysis completeness;
        private ConsistencyAnalysis consistency;
        private AccuracyAnalysis accuracy;
        private String qualityInsights;
        private String dataType;
        private Date validatedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CompletenessAnalysis {
        private double completenessScore;
        private int missingFields;
        private int totalFields;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConsistencyAnalysis {
        private double consistencyScore;
        private int inconsistencies;
        private int totalRecords;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccuracyAnalysis {
        private double accuracyScore;
        private int errors;
        private int totalRecords;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BusinessRuleValidationResult {
        private boolean isValid;
        private List<BusinessRuleError> errors;
        private String aiSuggestions;
        private String businessInsights;
        private Date validatedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BusinessRuleError {
        private String rule;
        private String message;
        private String severity;
    }
}