package com.easyluxury.ai.controller;

import com.easyluxury.ai.dto.AIHealthStatusDto;
import com.easyluxury.ai.dto.AIConfigurationStatusDto;
import com.easyluxury.ai.service.AIHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Health Controller
 * 
 * REST endpoints for AI health monitoring and status checking.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/health")
@RequiredArgsConstructor
@Tag(name = "AI Health", description = "AI health monitoring and status endpoints")
public class AIHealthController {
    
    private final AIHealthService aiHealthService;
    
    @GetMapping("/status")
    @Operation(
        summary = "Get AI health status",
        description = "Get comprehensive AI health status including all services and configurations"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health status retrieved successfully",
            content = @Content(schema = @Schema(implementation = AIHealthStatusDto.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AIHealthStatusDto> getHealthStatus() {
        log.info("Retrieving AI health status");
        
        try {
            AIHealthStatusDto healthStatus = aiHealthService.getAIHealthStatus();
            return ResponseEntity.ok(healthStatus);
        } catch (Exception e) {
            log.error("Error retrieving AI health status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(AIHealthStatusDto.builder()
                    .overallStatus("ERROR")
                    .message("Failed to retrieve health status: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/configuration")
    @Operation(
        summary = "Get AI configuration status",
        description = "Get AI configuration status and settings"
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
    
    @PostMapping("/validate")
    @Operation(
        summary = "Validate AI configuration",
        description = "Validate AI configuration and return validation results"
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
            String message = isValid ? "Configuration is valid" : "Configuration validation failed";
            
            ValidationResponse response = ValidationResponse.builder()
                .valid(isValid)
                .message(message)
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
    
    @GetMapping("/services")
    @Operation(
        summary = "Get AI services status",
        description = "Get status of individual AI services"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Services status retrieved successfully",
            content = @Content(schema = @Schema(implementation = ServicesStatusResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServicesStatusResponse> getServicesStatus() {
        log.info("Retrieving AI services status");
        
        try {
            AIHealthStatusDto healthStatus = aiHealthService.getAIHealthStatus();
            
            ServicesStatusResponse response = ServicesStatusResponse.builder()
                .coreService(healthStatus.getCoreServiceStatus())
                .embeddingService(healthStatus.getEmbeddingServiceStatus())
                .searchService(healthStatus.getSearchServiceStatus())
                .ragService(healthStatus.getRagServiceStatus())
                .overallStatus(healthStatus.getOverallStatus())
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving AI services status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ServicesStatusResponse.builder()
                    .overallStatus("ERROR")
                    .message("Failed to retrieve services status: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    // ==================== Response DTOs ====================
    
    @Schema(description = "Configuration validation response")
    public static class ValidationResponse {
        @Schema(description = "Whether configuration is valid")
        private boolean valid;
        
        @Schema(description = "Validation message")
        private String message;
        
        @Schema(description = "Validation timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static ValidationResponseBuilder builder() {
            return new ValidationResponseBuilder();
        }
        
        public static class ValidationResponseBuilder {
            private boolean valid;
            private String message;
            private java.time.LocalDateTime timestamp;
            
            public ValidationResponseBuilder valid(boolean valid) {
                this.valid = valid;
                return this;
            }
            
            public ValidationResponseBuilder message(String message) {
                this.message = message;
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
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    @Schema(description = "Services status response")
    public static class ServicesStatusResponse {
        @Schema(description = "Core service status")
        private String coreService;
        
        @Schema(description = "Embedding service status")
        private String embeddingService;
        
        @Schema(description = "Search service status")
        private String searchService;
        
        @Schema(description = "RAG service status")
        private String ragService;
        
        @Schema(description = "Overall status")
        private String overallStatus;
        
        @Schema(description = "Status message")
        private String message;
        
        @Schema(description = "Status timestamp")
        private java.time.LocalDateTime timestamp;
        
        // Builder pattern
        public static ServicesStatusResponseBuilder builder() {
            return new ServicesStatusResponseBuilder();
        }
        
        public static class ServicesStatusResponseBuilder {
            private String coreService;
            private String embeddingService;
            private String searchService;
            private String ragService;
            private String overallStatus;
            private String message;
            private java.time.LocalDateTime timestamp;
            
            public ServicesStatusResponseBuilder coreService(String coreService) {
                this.coreService = coreService;
                return this;
            }
            
            public ServicesStatusResponseBuilder embeddingService(String embeddingService) {
                this.embeddingService = embeddingService;
                return this;
            }
            
            public ServicesStatusResponseBuilder searchService(String searchService) {
                this.searchService = searchService;
                return this;
            }
            
            public ServicesStatusResponseBuilder ragService(String ragService) {
                this.ragService = ragService;
                return this;
            }
            
            public ServicesStatusResponseBuilder overallStatus(String overallStatus) {
                this.overallStatus = overallStatus;
                return this;
            }
            
            public ServicesStatusResponseBuilder message(String message) {
                this.message = message;
                return this;
            }
            
            public ServicesStatusResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public ServicesStatusResponse build() {
                ServicesStatusResponse response = new ServicesStatusResponse();
                response.coreService = this.coreService;
                response.embeddingService = this.embeddingService;
                response.searchService = this.searchService;
                response.ragService = this.ragService;
                response.overallStatus = this.overallStatus;
                response.message = this.message;
                response.timestamp = this.timestamp;
                return response;
            }
        }
        
        // Getters and setters
        public String getCoreService() { return coreService; }
        public void setCoreService(String coreService) { this.coreService = coreService; }
        
        public String getEmbeddingService() { return embeddingService; }
        public void setEmbeddingService(String embeddingService) { this.embeddingService = embeddingService; }
        
        public String getSearchService() { return searchService; }
        public void setSearchService(String searchService) { this.searchService = searchService; }
        
        public String getRagService() { return ragService; }
        public void setRagService(String ragService) { this.ragService = ragService; }
        
        public String getOverallStatus() { return overallStatus; }
        public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
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