package com.easyluxury.ai.controller;

import com.easyluxury.ai.dto.AIConfigurationStatusDto;
import com.easyluxury.ai.service.AIHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Configuration Controller
 * 
 * REST endpoints for AI configuration management and settings.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/configuration")
@RequiredArgsConstructor
@Tag(name = "AI Configuration", description = "AI configuration management and settings endpoints")
public class AIConfigurationController {
    
    private final AIHealthService aiHealthService;
    
    @GetMapping("/status")
    @Operation(
        summary = "Get AI configuration status",
        description = "Get comprehensive AI configuration status and settings"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration status retrieved successfully",
            content = @Content(schema = @Schema(implementation = AIConfigurationStatusDto.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AIConfigurationStatusDto> getConfigurationStatus() {
        log.info("Retrieving AI configuration status");
        
        try {
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            return ResponseEntity.ok(configStatus);
        } catch (Exception e) {
            log.error("Error retrieving AI configuration status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(AIConfigurationStatusDto.builder()
                    .configurationValid(false)
                    .message("Failed to retrieve configuration status: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/settings")
    @Operation(
        summary = "Get AI settings",
        description = "Get current AI settings and configuration parameters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings retrieved successfully",
            content = @Content(schema = @Schema(implementation = SettingsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SettingsResponse> getSettings() {
        log.info("Retrieving AI settings");
        
        try {
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            
            SettingsResponse response = SettingsResponse.builder()
                .providerSettings(configStatus.getProviderSettings())
                .serviceSettings(configStatus.getServiceSettings())
                .easyluxurySettings(configStatus.getEasyluxurySettings())
                .featureFlags(configStatus.getFeatureFlags())
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving AI settings: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(SettingsResponse.builder()
                    .message("Failed to retrieve settings: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    @GetMapping("/features")
    @Operation(
        summary = "Get AI feature flags",
        description = "Get current AI feature flags and their status"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Feature flags retrieved successfully",
            content = @Content(schema = @Schema(implementation = FeatureFlagsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FeatureFlagsResponse> getFeatureFlags() {
        log.info("Retrieving AI feature flags");
        
        try {
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            
            FeatureFlagsResponse response = FeatureFlagsResponse.builder()
                .featureFlags(configStatus.getFeatureFlags())
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving AI feature flags: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(FeatureFlagsResponse.builder()
                    .message("Failed to retrieve feature flags: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    @GetMapping("/providers")
    @Operation(
        summary = "Get AI provider information",
        description = "Get information about configured AI providers"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Provider information retrieved successfully",
            content = @Content(schema = @Schema(implementation = ProvidersResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProvidersResponse> getProviders() {
        log.info("Retrieving AI provider information");
        
        try {
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            
            ProvidersResponse response = ProvidersResponse.builder()
                .providerSettings(configStatus.getProviderSettings())
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving AI provider information: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ProvidersResponse.builder()
                    .message("Failed to retrieve provider information: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    @GetMapping("/validation")
    @Operation(
        summary = "Validate AI configuration",
        description = "Validate AI configuration and return detailed validation results"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration validation completed",
            content = @Content(schema = @Schema(implementation = ValidationResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ValidationResponse> validateConfiguration() {
        log.info("Validating AI configuration");
        
        try {
            boolean isValid = aiHealthService.validateConfiguration();
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            
            ValidationResponse response = ValidationResponse.builder()
                .valid(isValid)
                .message(isValid ? "Configuration is valid" : "Configuration validation failed")
                .details(configStatus)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error validating AI configuration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ValidationResponse.builder()
                    .valid(false)
                    .message("Configuration validation failed: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    @GetMapping("/summary")
    @Operation(
        summary = "Get AI configuration summary",
        description = "Get a summary of AI configuration status and key settings"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration summary retrieved successfully",
            content = @Content(schema = @Schema(implementation = ConfigurationSummaryResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ConfigurationSummaryResponse> getConfigurationSummary() {
        log.info("Retrieving AI configuration summary");
        
        try {
            AIConfigurationStatusDto configStatus = aiHealthService.getConfigurationStatus();
            
            ConfigurationSummaryResponse response = ConfigurationSummaryResponse.builder()
                .configurationValid(configStatus.isConfigurationValid())
                .message(configStatus.getMessage())
                .providerCount(configStatus.getProviderSettings() != null ? configStatus.getProviderSettings().size() : 0)
                .featureCount(configStatus.getFeatureFlags() != null ? configStatus.getFeatureFlags().size() : 0)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving AI configuration summary: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ConfigurationSummaryResponse.builder()
                    .configurationValid(false)
                    .message("Failed to retrieve configuration summary: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    // ==================== Response DTOs ====================
    
    @Schema(description = "Settings response")
    public static class SettingsResponse {
        @Schema(description = "Provider settings")
        private Map<String, Object> providerSettings;
        
        @Schema(description = "Service settings")
        private Map<String, Object> serviceSettings;
        
        @Schema(description = "Easy Luxury specific settings")
        private Map<String, Object> easyluxurySettings;
        
        @Schema(description = "Feature flags")
        private Map<String, Object> featureFlags;
        
        @Schema(description = "Response message")
        private String message;
        
        @Schema(description = "Response timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static SettingsResponseBuilder builder() {
            return new SettingsResponseBuilder();
        }
        
        public static class SettingsResponseBuilder {
            private Map<String, Object> providerSettings;
            private Map<String, Object> serviceSettings;
            private Map<String, Object> easyluxurySettings;
            private Map<String, Object> featureFlags;
            private String message;
            private java.time.LocalDateTime timestamp;
            
            public SettingsResponseBuilder providerSettings(Map<String, Object> providerSettings) {
                this.providerSettings = providerSettings;
                return this;
            }
            
            public SettingsResponseBuilder serviceSettings(Map<String, Object> serviceSettings) {
                this.serviceSettings = serviceSettings;
                return this;
            }
            
            public SettingsResponseBuilder easyluxurySettings(Map<String, Object> easyluxurySettings) {
                this.easyluxurySettings = easyluxurySettings;
                return this;
            }
            
            public SettingsResponseBuilder featureFlags(Map<String, Object> featureFlags) {
                this.featureFlags = featureFlags;
                return this;
            }
            
            public SettingsResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public SettingsResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public SettingsResponse build() {
                SettingsResponse response = new SettingsResponse();
                response.providerSettings = this.providerSettings;
                response.serviceSettings = this.serviceSettings;
                response.easyluxurySettings = this.easyluxurySettings;
                response.featureFlags = this.featureFlags;
                response.message = this.message;
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public Map<String, Object> getProviderSettings() { return providerSettings; }
        public void setProviderSettings(Map<String, Object> providerSettings) { this.providerSettings = providerSettings; }
        
        public Map<String, Object> getServiceSettings() { return serviceSettings; }
        public void setServiceSettings(Map<String, Object> serviceSettings) { this.serviceSettings = serviceSettings; }
        
        public Map<String, Object> getEasyluxurySettings() { return easyluxurySettings; }
        public void setEasyluxurySettings(Map<String, Object> easyluxurySettings) { this.easyluxurySettings = easyluxurySettings; }
        
        public Map<String, Object> getFeatureFlags() { return featureFlags; }
        public void setFeatureFlags(Map<String, Object> featureFlags) { this.featureFlags = featureFlags; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    @Schema(description = "Feature flags response")
    public static class FeatureFlagsResponse {
        @Schema(description = "Feature flags")
        private Map<String, Object> featureFlags;
        
        @Schema(description = "Response message")
        private String message;
        
        @Schema(description = "Response timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static FeatureFlagsResponseBuilder builder() {
            return new FeatureFlagsResponseBuilder();
        }
        
        public static class FeatureFlagsResponseBuilder {
            private Map<String, Object> featureFlags;
            private String message;
            private java.time.LocalDateTime timestamp;
            
            public FeatureFlagsResponseBuilder featureFlags(Map<String, Object> featureFlags) {
                this.featureFlags = featureFlags;
                return this;
            }
            
            public FeatureFlagsResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public FeatureFlagsResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public FeatureFlagsResponse build() {
                FeatureFlagsResponse response = new FeatureFlagsResponse();
                response.featureFlags = this.featureFlags;
                response.message = this.message;
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public Map<String, Object> getFeatureFlags() { return featureFlags; }
        public void setFeatureFlags(Map<String, Object> featureFlags) { this.featureFlags = featureFlags; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    @Schema(description = "Providers response")
    public static class ProvidersResponse {
        @Schema(description = "Provider settings")
        private Map<String, Object> providerSettings;
        
        @Schema(description = "Response message")
        private String message;
        
        @Schema(description = "Response timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static ProvidersResponseBuilder builder() {
            return new ProvidersResponseBuilder();
        }
        
        public static class ProvidersResponseBuilder {
            private Map<String, Object> providerSettings;
            private String message;
            private java.time.LocalDateTime timestamp;
            
            public ProvidersResponseBuilder providerSettings(Map<String, Object> providerSettings) {
                this.providerSettings = providerSettings;
                return this;
            }
            
            public ProvidersResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public ProvidersResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public ProvidersResponse build() {
                ProvidersResponse response = new ProvidersResponse();
                response.providerSettings = this.providerSettings;
                response.message = this.message;
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public Map<String, Object> getProviderSettings() { return providerSettings; }
        public void setProviderSettings(Map<String, Object> providerSettings) { this.providerSettings = providerSettings; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    @Schema(description = "Validation response")
    public static class ValidationResponse {
        @Schema(description = "Whether configuration is valid")
        private boolean valid;
        
        @Schema(description = "Validation message")
        private String message;
        
        @Schema(description = "Configuration details")
        private AIConfigurationStatusDto details;
        
        @Schema(description = "Validation timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static ValidationResponseBuilder builder() {
            return new ValidationResponseBuilder();
        }
        
        public static class ValidationResponseBuilder {
            private boolean valid;
            private String message;
            private AIConfigurationStatusDto details;
            private java.time.LocalDateTime timestamp;
            
            public ValidationResponseBuilder valid(boolean valid) {
                this.valid = valid;
                return this;
            }
            
            public ValidationResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public ValidationResponseBuilder details(AIConfigurationStatusDto details) {
                this.details = details;
                return this;
            }
            
            public ValidationResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public ValidationResponse build() {
                ValidationResponse response = new ValidationResponse();
                response.valid = this.valid;
                response.message = this.message;
                response.details = this.details;
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public AIConfigurationStatusDto getDetails() { return details; }
        public void setDetails(AIConfigurationStatusDto details) { this.details = details; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    @Schema(description = "Configuration summary response")
    public static class ConfigurationSummaryResponse {
        @Schema(description = "Whether configuration is valid")
        private boolean configurationValid;
        
        @Schema(description = "Summary message")
        private String message;
        
        @Schema(description = "Number of providers")
        private int providerCount;
        
        @Schema(description = "Number of features")
        private int featureCount;
        
        @Schema(description = "Summary timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static ConfigurationSummaryResponseBuilder builder() {
            return new ConfigurationSummaryResponseBuilder();
        }
        
        public static class ConfigurationSummaryResponseBuilder {
            private boolean configurationValid;
            private String message;
            private int providerCount;
            private int featureCount;
            private java.time.LocalDateTime timestamp;
            
            public ConfigurationSummaryResponseBuilder configurationValid(boolean configurationValid) {
                this.configurationValid = configurationValid;
                return this;
            }
            
            public ConfigurationSummaryResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public ConfigurationSummaryResponseBuilder providerCount(int providerCount) {
                this.providerCount = providerCount;
                return this;
            }
            
            public ConfigurationSummaryResponseBuilder featureCount(int featureCount) {
                this.featureCount = featureCount;
                return this;
            }
            
            public ConfigurationSummaryResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public ConfigurationSummaryResponse build() {
                ConfigurationSummaryResponse response = new ConfigurationSummaryResponse();
                response.configurationValid = this.configurationValid;
                response.message = this.message;
                response.providerCount = this.providerCount;
                response.featureCount = this.featureCount;
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public boolean isConfigurationValid() { return configurationValid; }
        public void setConfigurationValid(boolean configurationValid) { this.configurationValid = configurationValid; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public int getProviderCount() { return providerCount; }
        public void setProviderCount(int providerCount) { this.providerCount = providerCount; }
        
        public int getFeatureCount() { return featureCount; }
        public void setFeatureCount(int featureCount) { this.featureCount = featureCount; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    @Schema(description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;
        
        @Schema(description = "Error code")
        private String code;
        
        @Schema(description = "Timestamp")
        private String timestamp;
        
        public ErrorResponse() {}
        
        public ErrorResponse(String message, String code) {
            this.message = message;
            this.code = code;
            this.timestamp = java.time.LocalDateTime.now().toString();
        }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
}