package com.ai.infrastructure.privacy;

import com.ai.infrastructure.dto.AIDataPrivacyRequest;
import com.ai.infrastructure.dto.AIDataPrivacyResponse;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI Data Privacy Service for data protection and privacy compliance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIDataPrivacyService {

    private final AICoreService aiCoreService;
    private final Map<String, Map<String, Object>> privacySettings = new ConcurrentHashMap<>();
    private final Map<String, List<String>> dataClassifications = new ConcurrentHashMap<>();

    /**
     * Process data privacy request
     */
    public AIDataPrivacyResponse processDataPrivacyRequest(AIDataPrivacyRequest request) {
        log.info("Processing data privacy request for user: {}", request.getUserId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Analyze data sensitivity
            String dataClassification = analyzeDataSensitivity(request.getContent());
            
            // Check privacy compliance
            boolean isCompliant = checkPrivacyCompliance(request, dataClassification);
            
            // Apply data anonymization if needed
            String processedContent = applyDataAnonymization(request.getContent(), dataClassification);
            
            // Check consent requirements
            boolean consentRequired = checkConsentRequirements(request, dataClassification);
            
            // Generate privacy recommendations
            List<String> recommendations = generatePrivacyRecommendations(request, dataClassification, isCompliant);
            
            // Apply privacy controls
            Map<String, Object> privacyControls = applyPrivacyControls(request, dataClassification);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AIDataPrivacyResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .dataClassification(dataClassification)
                .isCompliant(isCompliant)
                .processedContent(processedContent)
                .consentRequired(consentRequired)
                .recommendations(recommendations)
                .privacyControls(privacyControls)
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing data privacy request", e);
            return AIDataPrivacyResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .isCompliant(false)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Analyze data sensitivity using AI
     */
    private String analyzeDataSensitivity(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "PUBLIC";
        }
        
        try {
            String prompt = String.format(
                "Classify the sensitivity level of this data content. " +
                "Return one of: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED\n\n" +
                "Content: %s\n\n" +
                "Consider factors like: personal information, financial data, " +
                "health information, trade secrets, or other sensitive data.",
                content
            );
            
            String response = aiCoreService.generateText(prompt);
            String classification = response.trim().toUpperCase();
            
            // Validate classification
            if (Arrays.asList("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED").contains(classification)) {
                return classification;
            } else {
                return "INTERNAL"; // Default fallback
            }
            
        } catch (Exception e) {
            log.warn("Failed to analyze data sensitivity, using default classification", e);
            return "INTERNAL";
        }
    }

    /**
     * Check privacy compliance
     */
    private boolean checkPrivacyCompliance(AIDataPrivacyRequest request, String dataClassification) {
        // Check if user has consent for this data type
        if (!hasValidConsent(request, dataClassification)) {
            return false;
        }
        
        // Check if data processing purpose is legitimate
        if (!isLegitimatePurpose(request)) {
            return false;
        }
        
        // Check if data minimization is applied
        if (!isDataMinimized(request)) {
            return false;
        }
        
        // Check if retention period is appropriate
        if (!isRetentionPeriodAppropriate(request, dataClassification)) {
            return false;
        }
        
        return true;
    }

    /**
     * Apply data anonymization based on classification
     */
    private String applyDataAnonymization(String content, String dataClassification) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        switch (dataClassification) {
            case "RESTRICTED":
                return anonymizeRestrictedData(content);
            case "CONFIDENTIAL":
                return anonymizeConfidentialData(content);
            case "INTERNAL":
                return anonymizeInternalData(content);
            case "PUBLIC":
            default:
                return content; // No anonymization needed for public data
        }
    }

    /**
     * Anonymize restricted data
     */
    private String anonymizeRestrictedData(String content) {
        // Remove or mask highly sensitive information
        String anonymized = content;
        
        // Mask email addresses
        anonymized = anonymized.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[EMAIL]");
        
        // Mask phone numbers
        anonymized = anonymized.replaceAll("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "[PHONE]");
        
        // Mask SSN
        anonymized = anonymized.replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[SSN]");
        
        // Mask credit card numbers
        anonymized = anonymized.replaceAll("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b", "[CARD]");
        
        return anonymized;
    }

    /**
     * Anonymize confidential data
     */
    private String anonymizeConfidentialData(String content) {
        // Less aggressive anonymization for confidential data
        String anonymized = content;
        
        // Mask email addresses
        anonymized = anonymized.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[EMAIL]");
        
        // Mask phone numbers
        anonymized = anonymized.replaceAll("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "[PHONE]");
        
        return anonymized;
    }

    /**
     * Anonymize internal data
     */
    private String anonymizeInternalData(String content) {
        // Minimal anonymization for internal data
        String anonymized = content;
        
        // Only mask very sensitive information
        anonymized = anonymized.replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[SSN]");
        
        return anonymized;
    }

    /**
     * Check consent requirements
     */
    private boolean checkConsentRequirements(AIDataPrivacyRequest request, String dataClassification) {
        // Check if consent is required based on data classification
        switch (dataClassification) {
            case "RESTRICTED":
            case "CONFIDENTIAL":
                return true; // Always require consent for sensitive data
            case "INTERNAL":
                return request.isConsentRequired(); // Check user preference
            case "PUBLIC":
            default:
                return false; // No consent required for public data
        }
    }

    /**
     * Generate privacy recommendations
     */
    private List<String> generatePrivacyRecommendations(AIDataPrivacyRequest request, 
                                                       String dataClassification, 
                                                       boolean isCompliant) {
        List<String> recommendations = new ArrayList<>();
        
        if (!isCompliant) {
            recommendations.add("Review and update privacy settings to ensure compliance");
        }
        
        switch (dataClassification) {
            case "RESTRICTED":
                recommendations.add("Implement additional security measures for restricted data");
                recommendations.add("Ensure proper access controls are in place");
                recommendations.add("Consider data encryption for storage and transmission");
                break;
            case "CONFIDENTIAL":
                recommendations.add("Implement access logging and monitoring");
                recommendations.add("Ensure data is only accessible to authorized personnel");
                break;
            case "INTERNAL":
                recommendations.add("Review data handling procedures");
                recommendations.add("Ensure proper data classification is maintained");
                break;
        }
        
        if (request.isConsentRequired() && !request.isConsentGiven()) {
            recommendations.add("Obtain explicit consent before processing personal data");
        }
        
        if (request.getDataRetentionPeriod() == null) {
            recommendations.add("Define appropriate data retention period");
        }
        
        return recommendations;
    }

    /**
     * Apply privacy controls
     */
    private Map<String, Object> applyPrivacyControls(AIDataPrivacyRequest request, String dataClassification) {
        Map<String, Object> controls = new HashMap<>();
        
        // Access controls
        controls.put("accessLevel", getAccessLevel(dataClassification));
        controls.put("requiresAuthentication", true);
        controls.put("requiresAuthorization", !"PUBLIC".equals(dataClassification));
        
        // Data handling controls
        controls.put("encryptionRequired", "RESTRICTED".equals(dataClassification));
        controls.put("auditLoggingRequired", !"PUBLIC".equals(dataClassification));
        controls.put("dataMinimizationRequired", true);
        
        // Retention controls
        controls.put("retentionPeriod", getRetentionPeriod(dataClassification));
        controls.put("autoDeletionEnabled", true);
        
        // Sharing controls
        controls.put("externalSharingAllowed", "PUBLIC".equals(dataClassification));
        controls.put("internalSharingAllowed", !"RESTRICTED".equals(dataClassification));
        
        return controls;
    }

    /**
     * Get access level based on data classification
     */
    private String getAccessLevel(String dataClassification) {
        switch (dataClassification) {
            case "RESTRICTED":
                return "ADMIN_ONLY";
            case "CONFIDENTIAL":
                return "AUTHORIZED_USERS";
            case "INTERNAL":
                return "INTERNAL_USERS";
            case "PUBLIC":
            default:
                return "ALL_USERS";
        }
    }

    /**
     * Get retention period based on data classification
     */
    private int getRetentionPeriod(String dataClassification) {
        switch (dataClassification) {
            case "RESTRICTED":
                return 365; // 1 year
            case "CONFIDENTIAL":
                return 730; // 2 years
            case "INTERNAL":
                return 1095; // 3 years
            case "PUBLIC":
            default:
                return 2555; // 7 years
        }
    }

    /**
     * Check if user has valid consent
     */
    private boolean hasValidConsent(AIDataPrivacyRequest request, String dataClassification) {
        if (!checkConsentRequirements(request, dataClassification)) {
            return true; // No consent required
        }
        
        return request.isConsentGiven() && request.getConsentTimestamp() != null;
    }

    /**
     * Check if purpose is legitimate
     */
    private boolean isLegitimatePurpose(AIDataPrivacyRequest request) {
        return request.getPurpose() != null && 
               !request.getPurpose().trim().isEmpty() &&
               !request.getPurpose().toLowerCase().contains("illegal");
    }

    /**
     * Check if data is minimized
     */
    private boolean isDataMinimized(AIDataPrivacyRequest request) {
        // Check if only necessary data is being processed
        if (request.getContent() != null && request.getContent().length() > 50000) {
            return false; // Too much data
        }
        
        return true;
    }

    /**
     * Check if retention period is appropriate
     */
    private boolean isRetentionPeriodAppropriate(AIDataPrivacyRequest request, String dataClassification) {
        if (request.getDataRetentionPeriod() == null) {
            return true; // No retention period specified
        }
        
        int maxRetention = getRetentionPeriod(dataClassification);
        return request.getDataRetentionPeriod() <= maxRetention;
    }

    /**
     * Get privacy settings for a user
     */
    public Map<String, Object> getPrivacySettings(String userId) {
        return privacySettings.getOrDefault(userId, new HashMap<>());
    }

    /**
     * Update privacy settings for a user
     */
    public void updatePrivacySettings(String userId, Map<String, Object> settings) {
        privacySettings.put(userId, settings);
    }

    /**
     * Get data classifications for a user
     */
    public List<String> getDataClassifications(String userId) {
        return dataClassifications.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * Add data classification for a user
     */
    public void addDataClassification(String userId, String classification) {
        dataClassifications.computeIfAbsent(userId, k -> new ArrayList<>()).add(classification);
    }

    /**
     * Get privacy statistics
     */
    public Map<String, Object> getPrivacyStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = privacySettings.size();
        long usersWithConsent = privacySettings.values().stream()
            .mapToLong(settings -> (Boolean) settings.getOrDefault("consentGiven", false) ? 1 : 0)
            .sum();
        
        stats.put("totalUsers", totalUsers);
        stats.put("usersWithConsent", usersWithConsent);
        stats.put("consentRate", totalUsers > 0 ? (double) usersWithConsent / totalUsers : 0.0);
        
        // Data classification distribution
        Map<String, Long> classificationDistribution = dataClassifications.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.groupingBy(
                classification -> classification,
                Collectors.counting()
            ));
        stats.put("classificationDistribution", classificationDistribution);
        
        return stats;
    }

    /**
     * Generate privacy report
     */
    public Map<String, Object> generatePrivacyReport(String userId) {
        Map<String, Object> report = new HashMap<>();
        
        Map<String, Object> userSettings = getPrivacySettings(userId);
        List<String> userClassifications = getDataClassifications(userId);
        
        report.put("userId", userId);
        report.put("timestamp", LocalDateTime.now());
        report.put("privacySettings", userSettings);
        report.put("dataClassifications", userClassifications);
        report.put("consentGiven", userSettings.getOrDefault("consentGiven", false));
        report.put("dataMinimizationEnabled", userSettings.getOrDefault("dataMinimizationEnabled", true));
        report.put("auditLoggingEnabled", userSettings.getOrDefault("auditLoggingEnabled", true));
        
        return report;
    }

    /**
     * Clear privacy data for a user
     */
    public void clearPrivacyData(String userId) {
        privacySettings.remove(userId);
        dataClassifications.remove(userId);
    }
}