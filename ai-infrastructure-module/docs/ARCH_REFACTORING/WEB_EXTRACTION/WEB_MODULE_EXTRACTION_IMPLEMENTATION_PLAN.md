# Web Module Extraction - Implementation Plan

## 🎯 Objective

Extract all 6 REST controllers (1,171 lines, 59 endpoints) from `ai-infrastructure-core` to a new `ai-infrastructure-web` module.

**Decision**: Option 1 - Extract ALL controllers ✅

---

## 📊 Scope

### Files to Extract (6 controllers):
1. `AdvancedRAGController.java` (95 lines, 3 endpoints)
2. `AIAuditController.java` (226 lines, 11 endpoints)
3. `AIComplianceController.java` (72 lines, 2 endpoints)
4. `AIMonitoringController.java` (279 lines, 15 endpoints)
5. `AIProfileController.java` (358 lines, 22 endpoints)
6. `AISecurityController.java` (141 lines, 6 endpoints)

**Total**: 1,171 lines, 59 endpoints

---

## 🗓️ Timeline

**Total Effort**: 2-3 days

| Phase | Duration | Description |
|-------|----------|-------------|
| Phase 1: Setup | 2-3 hours | Create module structure |
| Phase 2: Move Controllers | 3-4 hours | Move files and update imports |
| Phase 3: Configuration | 2-3 hours | Create AutoConfiguration |
| Phase 4: Testing | 4-6 hours | Unit + Integration tests |
| Phase 5: Documentation | 2-3 hours | README, migration guide |
| Phase 6: Cleanup | 1-2 hours | Remove from core, verify |

---

## 📋 Phase 1: Create Module Structure (2-3 hours)

### Step 1.1: Create Directory Structure

```bash
# Create base directory
mkdir -p ai-infrastructure-web

# Create source directories
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config

# Create resource directories
mkdir -p ai-infrastructure-web/src/main/resources
mkdir -p ai-infrastructure-web/src/main/resources/META-INF/spring

# Create test directories
mkdir -p ai-infrastructure-web/src/test/java/com/ai/infrastructure/web
mkdir -p ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/test/resources
```

### Step 1.2: Create pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ai-infrastructure-web</artifactId>
    <packaging>jar</packaging>

    <name>AI Infrastructure Web</name>
    <description>REST controllers for AI Infrastructure</description>

    <dependencies>
        <!-- Core dependency - REQUIRED -->
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Spring Boot Web - REQUIRED for controllers -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Spring Security Test (for securing endpoints) -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 1.3: Update Parent POM

Edit `ai-infrastructure-spring-boot-starter/pom.xml`:

```xml
<modules>
    <module>ai-infrastructure-core</module>
    <module>ai-infrastructure-web</module>  <!-- ADD THIS -->
    <module>ai-infrastructure-behavior</module>
    <!-- ... other modules ... -->
</modules>
```

### Step 1.4: Verify Module Structure

```bash
# Check structure
tree ai-infrastructure-web

# Expected output:
# ai-infrastructure-web/
# ├── pom.xml
# └── src/
#     ├── main/
#     │   ├── java/
#     │   │   └── com/ai/infrastructure/web/
#     │   │       ├── controller/
#     │   │       └── config/
#     │   └── resources/
#     │       └── META-INF/spring/
#     └── test/
#         ├── java/
#         │   └── com/ai/infrastructure/web/
#         └── resources/
```

**Checkpoint**: ✅ Module structure created

---

## 📋 Phase 2: Move Controllers (3-4 hours)

### Step 2.1: Copy Controllers to New Module

```bash
# From ai-infrastructure-module directory
cd ai-infrastructure-module

# Copy controllers one by one
cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/AdvancedRAGController.java \
   ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/

cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/AIAuditController.java \
   ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/

cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/AIComplianceController.java \
   ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/

cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/AIMonitoringController.java \
   ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/

cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/AIProfileController.java \
   ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/

cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/AISecurityController.java \
   ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/
```

### Step 2.2: Update Package Declarations

Update package in each controller from:
```java
package com.ai.infrastructure.controller;
```

To:
```java
package com.ai.infrastructure.web.controller;
```

**Files to update** (6 files):
- AdvancedRAGController.java
- AIAuditController.java
- AIComplianceController.java
- AIMonitoringController.java
- AIProfileController.java
- AISecurityController.java

### Step 2.3: Verify Imports

All imports should still work since we depend on core:
```java
import com.ai.infrastructure.dto.*;           // From core
import com.ai.infrastructure.service.*;       // From core
import com.ai.infrastructure.audit.*;         // From core
import com.ai.infrastructure.compliance.*;    // From core
import com.ai.infrastructure.monitoring.*;    // From core
import com.ai.infrastructure.security.*;      // From core
```

**No import changes needed** - all dependencies come from core module ✅

### Step 2.4: Compile New Module

```bash
cd ai-infrastructure-web
mvn clean compile

# Should compile successfully
# If errors, check:
# 1. Package declarations updated?
# 2. Parent POM includes new module?
# 3. Dependencies correct in pom.xml?
```

**Checkpoint**: ✅ Controllers moved and compile successfully

---

## 📋 Phase 3: Create AutoConfiguration (2-3 hours)

### Step 3.1: Create Properties Class

Create `ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config/AIWebProperties.java`:

```java
package com.ai.infrastructure.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AI Infrastructure Web module
 */
@Data
@ConfigurationProperties(prefix = "ai.web")
public class AIWebProperties {
    
    /**
     * Enable/disable web controllers
     */
    private boolean enabled = true;
    
    /**
     * Base path for all AI endpoints (default: /api/ai)
     */
    private String basePath = "/api/ai";
    
    /**
     * Enable/disable individual controller groups
     */
    private Controllers controllers = new Controllers();
    
    /**
     * Security settings
     */
    private Security security = new Security();
    
    @Data
    public static class Controllers {
        /**
         * Enable Advanced RAG controller
         */
        private boolean advancedRag = true;
        
        /**
         * Enable Audit controller
         */
        private boolean audit = true;
        
        /**
         * Enable Compliance controller
         */
        private boolean compliance = true;
        
        /**
         * Enable Monitoring controller
         */
        private boolean monitoring = true;
        
        /**
         * Enable Profile controller
         */
        private boolean profile = true;
        
        /**
         * Enable Security controller
         */
        private boolean security = true;
    }
    
    @Data
    public static class Security {
        /**
         * Require authentication for all endpoints
         */
        private boolean requireAuth = false;
        
        /**
         * Enable CORS
         */
        private boolean enableCors = true;
        
        /**
         * Allowed origins for CORS
         */
        private String[] allowedOrigins = {"*"};
    }
}
```

### Step 3.2: Create AutoConfiguration

Create `ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config/AIWebAutoConfiguration.java`:

```java
package com.ai.infrastructure.web.config;

import com.ai.infrastructure.web.controller.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for AI Infrastructure Web module
 * 
 * Automatically configures REST controllers when:
 * 1. This module is on the classpath
 * 2. Running in a web application
 * 3. ai.web.enabled=true (default)
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
@ConditionalOnProperty(prefix = "ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AIWebProperties.class)
public class AIWebAutoConfiguration {
    
    public AIWebAutoConfiguration() {
        log.info("AI Infrastructure Web AutoConfiguration initialized");
    }
    
    /**
     * Configure CORS if enabled
     */
    @Bean
    @ConditionalOnProperty(prefix = "ai.web.security", name = "enable-cors", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer corsConfigurer(AIWebProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping(properties.getBasePath() + "/**")
                    .allowedOrigins(properties.getSecurity().getAllowedOrigins())
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false);
                
                log.info("CORS configured for AI Infrastructure Web endpoints");
            }
        };
    }
    
    // Controllers are automatically discovered by Spring due to @RestController annotation
    // But we log when they're loaded
    
    @Bean
    public ControllerLogger controllerLogger(AIWebProperties properties) {
        return new ControllerLogger(properties);
    }
    
    /**
     * Helper class to log which controllers are enabled
     */
    public static class ControllerLogger {
        public ControllerLogger(AIWebProperties properties) {
            log.info("AI Infrastructure Web Controllers:");
            log.info("  - Advanced RAG: {}", properties.getControllers().isAdvancedRag() ? "ENABLED" : "DISABLED");
            log.info("  - Audit: {}", properties.getControllers().isAudit() ? "ENABLED" : "DISABLED");
            log.info("  - Compliance: {}", properties.getControllers().isCompliance() ? "ENABLED" : "DISABLED");
            log.info("  - Monitoring: {}", properties.getControllers().isMonitoring() ? "ENABLED" : "DISABLED");
            log.info("  - Profile: {}", properties.getControllers().isProfile() ? "ENABLED" : "DISABLED");
            log.info("  - Security: {}", properties.getControllers().isSecurity() ? "ENABLED" : "DISABLED");
            log.info("Total endpoints: 59");
        }
    }
}
```

### Step 3.3: Create Spring Boot AutoConfiguration Entry

Create `ai-infrastructure-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.ai.infrastructure.web.config.AIWebAutoConfiguration
```

### Step 3.4: Create application.properties Template

Create `ai-infrastructure-web/src/main/resources/application-ai-web.properties`:

```properties
# AI Infrastructure Web Configuration

# Enable/disable web controllers
ai.web.enabled=true

# Base path for all AI endpoints
ai.web.base-path=/api/ai

# Individual controller toggles
ai.web.controllers.advanced-rag=true
ai.web.controllers.audit=true
ai.web.controllers.compliance=true
ai.web.controllers.monitoring=true
ai.web.controllers.profile=true
ai.web.controllers.security=true

# Security settings
ai.web.security.require-auth=false
ai.web.security.enable-cors=true
ai.web.security.allowed-origins=*

# Logging
logging.level.com.ai.infrastructure.web=INFO
```

**Checkpoint**: ✅ AutoConfiguration created

---

## 📋 Phase 4: Testing (4-6 hours)

### Step 4.1: Create Base Test Class

Create `ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/BaseWebTest.java`:

```java
package com.ai.infrastructure.web;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base test class for web controller tests
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseWebTest {
    // Common test setup can go here
}
```

### Step 4.2: Create Controller Tests

For each controller, create a test class. Example for `AdvancedRAGController`:

Create `ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/controller/AdvancedRAGControllerTest.java`:

```java
package com.ai.infrastructure.web.controller;

import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.web.BaseWebTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdvancedRAGControllerTest extends BaseWebTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AdvancedRAGService advancedRAGService;
    
    @Test
    void testPerformAdvancedRAG_Success() throws Exception {
        // Given
        AdvancedRAGRequest request = AdvancedRAGRequest.builder()
            .query("test query")
            .build();
        
        AdvancedRAGResponse response = AdvancedRAGResponse.builder()
            .query("test query")
            .success(true)
            .build();
        
        when(advancedRAGService.performAdvancedRAG(any())).thenReturn(response);
        
        // When/Then
        mockMvc.perform(post("/api/ai/advanced-rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.query").value("test query"));
    }
    
    @Test
    void testGetStats_Success() throws Exception {
        mockMvc.perform(get("/api/ai/advanced-rag/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalQueries").exists());
    }
    
    @Test
    void testHealthCheck_Success() throws Exception {
        mockMvc.perform(get("/api/ai/advanced-rag/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

**Repeat for all 6 controllers** (similar pattern for each)

### Step 4.3: Create Integration Test

Create `ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/AIWebIntegrationTest.java`:

```java
package com.ai.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AIWebIntegrationTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
    
    @Test
    void controllersAreLoaded() {
        assertThat(context.containsBean("advancedRAGController")).isTrue();
        assertThat(context.containsBean("AIAuditController")).isTrue();
        assertThat(context.containsBean("AIComplianceController")).isTrue();
        assertThat(context.containsBean("AIMonitoringController")).isTrue();
        assertThat(context.containsBean("AIProfileController")).isTrue();
        assertThat(context.containsBean("AISecurityController")).isTrue();
    }
}
```

### Step 4.4: Run Tests

```bash
cd ai-infrastructure-web
mvn clean test

# All tests should pass
```

**Checkpoint**: ✅ Tests passing

---

## 📋 Phase 5: Documentation (2-3 hours)

### Step 5.1: Create README

Create `ai-infrastructure-web/README.md`:

```markdown
# AI Infrastructure Web Module

REST API controllers for AI Infrastructure.

## Overview

This module provides REST endpoints for AI Infrastructure functionality including:
- Advanced RAG operations
- Audit logging
- Compliance checking
- Monitoring and metrics
- AI profile management
- Security analysis

## Installation

### Maven

```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.ai.infrastructure:ai-infrastructure-web:1.0.0'
```

## Configuration

Add to `application.yml`:

```yaml
ai:
  web:
    enabled: true
    base-path: /api/ai
    controllers:
      advanced-rag: true
      audit: true
      compliance: true
      monitoring: true
      profile: true
      security: true
    security:
      require-auth: false
      enable-cors: true
      allowed-origins: "*"
```

## Endpoints

### Advanced RAG Controller
- `POST /api/ai/advanced-rag/search` - Perform advanced RAG search
- `GET /api/ai/advanced-rag/stats` - Get RAG statistics
- `GET /api/ai/advanced-rag/health` - Health check

### Audit Controller (11 endpoints)
- `POST /api/ai/audit/log` - Log audit event
- `GET /api/ai/audit/logs` - Get all audit logs
- `GET /api/ai/audit/logs/{userId}` - Get user audit logs
- ... [8 more endpoints]

### Compliance Controller
- `POST /api/ai/compliance/check` - Check compliance
- `GET /api/ai/compliance/health` - Health check

### Monitoring Controller (15 endpoints)
- `GET /api/ai/monitoring/health` - Get health status
- `GET /api/ai/monitoring/metrics` - Get metrics
- ... [13 more endpoints]

### Profile Controller (22 endpoints)
- `POST /api/ai/profiles` - Create profile
- `GET /api/ai/profiles/{id}` - Get profile
- ... [20 more endpoints]

### Security Controller
- `POST /api/ai/security/analyze` - Analyze security
- `GET /api/ai/security/events` - Get security events
- ... [4 more endpoints]

**Total: 59 REST endpoints**

## Security

⚠️ **Important**: By default, endpoints are NOT secured. You should:

1. Enable authentication:
```yaml
ai:
  web:
    security:
      require-auth: true
```

2. Configure Spring Security:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ai/**").authenticated()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
```

## Usage Examples

### Advanced RAG Search

```java
@Autowired
private RestTemplate restTemplate;

AdvancedRAGRequest request = AdvancedRAGRequest.builder()
    .query("Find similar products")
    .limit(10)
    .build();

AdvancedRAGResponse response = restTemplate.postForObject(
    "http://localhost:8080/api/ai/advanced-rag/search",
    request,
    AdvancedRAGResponse.class
);
```

### Audit Logging

```bash
curl -X POST http://localhost:8080/api/ai/audit/log \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "action": "search",
    "metadata": {}
  }'
```

## Disabling Controllers

To disable specific controllers:

```yaml
ai:
  web:
    controllers:
      audit: false  # Disable audit controller
      profile: false  # Disable profile controller
```

## Migration from Core

If you were using controllers from `ai-infrastructure-core`, update your imports:

**Before:**
```java
import com.ai.infrastructure.controller.AdvancedRAGController;
```

**After:**
```java
import com.ai.infrastructure.web.controller.AdvancedRAGController;
```

## Requirements

- Java 17+
- Spring Boot 3.0+
- ai-infrastructure-core 1.0.0+

## License

[Your License]
```

### Step 5.2: Create Migration Guide

Create `ai-infrastructure-web/MIGRATION_GUIDE.md`:

```markdown
# Migration Guide: Controllers from Core to Web Module

## Overview

Controllers have been moved from `ai-infrastructure-core` to `ai-infrastructure-web`.

## Who is Affected?

You are affected if you:
- Use any REST controllers from ai-infrastructure-core
- Have custom code that imports controllers
- Have tests that reference controllers

## Changes Required

### 1. Add Dependency

Add the new web module to your `pom.xml`:

```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Update Imports

If you have any code that imports controllers, update the package:

**Before:**
```java
import com.ai.infrastructure.controller.AdvancedRAGController;
import com.ai.infrastructure.controller.AIAuditController;
// etc.
```

**After:**
```java
import com.ai.infrastructure.web.controller.AdvancedRAGController;
import com.ai.infrastructure.web.controller.AIAuditController;
// etc.
```

### 3. Configuration

If you want to disable controllers, use new properties:

```yaml
ai:
  web:
    enabled: false  # Disable all web controllers
    
    # Or disable individual controllers:
    controllers:
      audit: false
```

## Endpoints (No Changes)

All endpoint URLs remain the same:
- `/api/ai/advanced-rag/**`
- `/api/ai/audit/**`
- `/api/ai/compliance/**`
- `/api/ai/monitoring/**`
- `/api/ai/profiles/**`
- `/api/ai/security/**

## Backward Compatibility

**Breaking Changes:**
- Controllers no longer in core module
- Package names changed

**Non-Breaking:**
- Endpoint URLs unchanged
- Controller behavior unchanged
- DTOs and services unchanged

## Timeline

- **Version 1.0**: Controllers in core (deprecated)
- **Version 2.0**: Controllers moved to web module
- **Version 3.0**: Controllers removed from core

## Need Help?

[Contact information or links to support]
```

### Step 5.3: Update Main Documentation

Update `ai-infrastructure-module/README.md` to mention the new web module.

**Checkpoint**: ✅ Documentation complete

---

## 📋 Phase 6: Cleanup and Verification (1-2 hours)

### Step 6.1: Remove Controllers from Core

**Option A: Delete Immediately** (Breaking change)
```bash
cd ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller
rm AdvancedRAGController.java
rm AIAuditController.java
rm AIComplianceController.java
rm AIMonitoringController.java
rm AIProfileController.java
rm AISecurityController.java

# Also delete the controller package if empty
cd ..
rmdir controller
```

**Option B: Deprecate First** (Gradual migration)
```bash
# Keep files but add @Deprecated annotation
# Add in each controller:
@Deprecated(since = "2.0", forRemoval = true)
@RestController
public class AdvancedRAGController {
    // Keep implementation but mark as deprecated
}
```

**Recommendation**: Use Option B for one release cycle, then Option A

### Step 6.2: Update Core Module Tests

Check if any tests in core reference the controllers:

```bash
cd ai-infrastructure-core/src/test
grep -r "controller" . --include="*.java"

# If found, either:
# 1. Delete the tests (controllers moved)
# 2. Move tests to web module
```

### Step 6.3: Verify Core Compiles Without Controllers

```bash
cd ai-infrastructure-core
mvn clean compile

# Should compile successfully even without controllers
```

### Step 6.4: Build Entire Project

```bash
cd ai-infrastructure-module
mvn clean install

# All modules should build successfully
```

### Step 6.5: Run All Tests

```bash
mvn clean test

# All tests should pass
```

**Checkpoint**: ✅ Cleanup complete, all builds passing

---

## 📋 Phase 7: Integration Verification (1 hour)

### Step 7.1: Create Test Application

Create a simple Spring Boot app to verify the module works:

Create `test-app/pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-web</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

Create `test-app/src/main/java/TestApplication.java`:

```java
@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
```

### Step 7.2: Start Test Application

```bash
cd test-app
mvn spring-boot:run

# Check logs for:
# - "AI Infrastructure Web AutoConfiguration initialized"
# - "AI Infrastructure Web Controllers: [list of controllers]"
```

### Step 7.3: Test Endpoints

```bash
# Test health endpoints
curl http://localhost:8080/api/ai/advanced-rag/health
curl http://localhost:8080/api/ai/audit/health
curl http://localhost:8080/api/ai/compliance/health
curl http://localhost:8080/api/ai/monitoring/health
curl http://localhost:8080/api/ai/security/health

# All should return 200 OK with status: UP
```

### Step 7.4: Test Configuration

Test that configuration works:

Create `test-app/src/main/resources/application.yml`:

```yaml
ai:
  web:
    controllers:
      audit: false  # Disable audit controller
```

Restart and verify audit endpoints return 404:

```bash
curl http://localhost:8080/api/ai/audit/health
# Should return 404
```

**Checkpoint**: ✅ Integration verified

---

## 📋 Final Checklist

Before considering the extraction complete:

### Module Structure
- [ ] `ai-infrastructure-web` directory created
- [ ] `pom.xml` created with correct dependencies
- [ ] Package structure: `com.ai.infrastructure.web.controller`
- [ ] Parent POM updated to include new module

### Code Migration
- [ ] All 6 controllers copied to new module
- [ ] Package declarations updated
- [ ] Controllers compile without errors
- [ ] No broken imports

### Configuration
- [ ] `AIWebProperties` created
- [ ] `AIWebAutoConfiguration` created
- [ ] `AutoConfiguration.imports` file created
- [ ] Sample configuration properties provided

### Testing
- [ ] Unit tests for each controller
- [ ] Integration test
- [ ] All tests pass
- [ ] Test coverage >80%

### Documentation
- [ ] README.md created
- [ ] MIGRATION_GUIDE.md created
- [ ] API documentation complete
- [ ] Configuration examples provided
- [ ] Security warnings documented

### Cleanup
- [ ] Controllers removed/deprecated in core
- [ ] Core module compiles without controllers
- [ ] No orphaned tests in core
- [ ] All modules build successfully

### Verification
- [ ] Test application created
- [ ] Endpoints accessible
- [ ] Configuration works
- [ ] Can disable controllers
- [ ] CORS works if enabled

### Release
- [ ] Version bumped (if breaking change)
- [ ] CHANGELOG updated
- [ ] Release notes prepared
- [ ] Migration guide published

---

## 🚀 Execution Commands

Complete execution script:

```bash
#!/bin/bash

set -e  # Exit on error

echo "=== Phase 1: Create Module Structure ==="
cd /workspace/ai-infrastructure-module

# Create directories
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config
mkdir -p ai-infrastructure-web/src/main/resources/META-INF/spring
mkdir -p ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/test/resources

echo "✓ Directory structure created"

# (pom.xml and other files created via templates above)

echo "=== Phase 2: Move Controllers ==="

# Copy controllers
for controller in AdvancedRAGController AIAuditController AIComplianceController AIMonitoringController AIProfileController AISecurityController; do
    cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/${controller}.java \
       ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/
    
    # Update package declaration
    sed -i 's/package com.ai.infrastructure.controller;/package com.ai.infrastructure.web.controller;/g' \
        ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/${controller}.java
    
    echo "✓ Moved ${controller}"
done

echo "=== Phase 3: Build New Module ==="
cd ai-infrastructure-web
mvn clean install

echo "=== Phase 4: Verify ==="
cd ..
mvn clean install

echo "=== COMPLETE ==="
echo "Web module extracted successfully!"
echo ""
echo "Next steps:"
echo "1. Review controllers in ai-infrastructure-web"
echo "2. Add tests"
echo "3. Update documentation"
echo "4. Remove/deprecate controllers in core"
```

---

## 📊 Success Metrics

The extraction is successful when:

1. ✅ New module builds without errors
2. ✅ All 59 endpoints still work
3. ✅ Tests pass (>80% coverage)
4. ✅ Documentation complete
5. ✅ Core module compiles without controllers
6. ✅ Integration test passes
7. ✅ Configuration works as expected

---

## 🎯 Post-Extraction Tasks

After successful extraction:

1. **Announce to Team**
   - Send email about new module
   - Update team documentation
   - Schedule knowledge sharing session

2. **Update CI/CD**
   - Add new module to build pipeline
   - Update deployment scripts
   - Configure artifact publishing

3. **Monitor Adoption**
   - Track who's using new module
   - Collect feedback
   - Address issues quickly

4. **Plan Core Cleanup**
   - Set date for removing deprecated controllers
   - Notify users of breaking change
   - Provide support during migration

---

## ❓ FAQ

**Q: Do I need to extract all controllers at once?**
A: Recommended, but you can extract one at a time if needed.

**Q: Will existing endpoints still work?**
A: Yes, all endpoint URLs remain the same.

**Q: What if I don't want the web module?**
A: Don't add it as a dependency - controllers won't load.

**Q: Can I mix old and new?**
A: For one release cycle, yes (with deprecation warnings).

**Q: What about security?**
A: Security configuration is your responsibility - document this clearly.

---

**Estimated Total Effort**: 2-3 days  
**Risk Level**: Low  
**Breaking Changes**: Yes (package names, requires new dependency)  
**Recommended Approach**: Phased (deprecate first, then remove)

---

**Ready to execute?** Start with Phase 1!
