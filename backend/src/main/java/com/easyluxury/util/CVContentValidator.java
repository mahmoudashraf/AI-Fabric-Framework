package com.easyluxury.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
public class CVContentValidator {

    private static final int MAX_CV_LENGTH = 50000;
    private static final int MIN_CV_LENGTH = 100;
    
    // Patterns for potential malicious content
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script.*?>.*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_PATTERN = Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(union|select|insert|update|delete|drop|create|alter)\\s+", Pattern.CASE_INSENSITIVE);
    
    public ValidationResult validate(String cvContent) {
        if (cvContent == null) {
            return ValidationResult.error("CV content cannot be null");
        }
        
        String trimmed = cvContent.trim();
        
        if (trimmed.isEmpty()) {
            return ValidationResult.error("CV content cannot be empty");
        }
        
        if (trimmed.length() < MIN_CV_LENGTH) {
            return ValidationResult.error("CV content too short (minimum 100 characters)");
        }
        
        if (trimmed.length() > MAX_CV_LENGTH) {
            return ValidationResult.error("CV content too long (maximum 50,000 characters)");
        }
        
        // Check for malicious content
        if (containsMaliciousContent(trimmed)) {
            log.warn("CV content contains potentially malicious content");
            return ValidationResult.error("CV content contains invalid characters or patterns");
        }
        
        // Check for reasonable content structure
        if (!hasReasonableStructure(trimmed)) {
            return ValidationResult.error("CV content does not appear to be a valid CV");
        }
        
        return ValidationResult.success(sanitize(trimmed));
    }
    
    private boolean containsMaliciousContent(String content) {
        return SCRIPT_PATTERN.matcher(content).find() ||
               JAVASCRIPT_PATTERN.matcher(content).find() ||
               SQL_INJECTION_PATTERN.matcher(content).find();
    }
    
    private boolean hasReasonableStructure(String content) {
        // Check for common CV keywords
        String lowerContent = content.toLowerCase();
        boolean hasName = lowerContent.matches(".*\\b(name|full name|first name|last name)\\b.*");
        boolean hasExperience = lowerContent.matches(".*\\b(experience|work|employment|job|position|role)\\b.*");
        boolean hasEducation = lowerContent.matches(".*\\b(education|degree|university|college|school)\\b.*");
        boolean hasSkills = lowerContent.matches(".*\\b(skills|abilities|competencies|technologies)\\b.*");
        
        return hasName || hasExperience || hasEducation || hasSkills;
    }
    
    private String sanitize(String content) {
        // Remove potentially dangerous characters but keep the content readable
        return content.replaceAll("[<>\"'&]", " ")
                     .replaceAll("\\s+", " ")
                     .trim();
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String sanitizedContent;
        
        private ValidationResult(boolean valid, String message, String sanitizedContent) {
            this.valid = valid;
            this.message = message;
            this.sanitizedContent = sanitizedContent;
        }
        
        public static ValidationResult success(String sanitizedContent) {
            return new ValidationResult(true, null, sanitizedContent);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSanitizedContent() {
            return sanitizedContent;
        }
    }
}