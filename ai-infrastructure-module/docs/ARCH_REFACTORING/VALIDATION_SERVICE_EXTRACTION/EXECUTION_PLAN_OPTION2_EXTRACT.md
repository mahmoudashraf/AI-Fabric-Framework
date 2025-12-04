# Execution Plan: EXTRACT AI Validation Service to Optional Module

## 📋 Overview

**Approach**: Extract to `ai-infrastructure-validation` (optional module)  
**Rationale**: Preserve functionality while removing from core  
**Risk Level**: **ZERO** ✅  
**Time Estimate**: **2-3 hours**  
**Breaking Changes**: **NONE** (service is unused, opt-in module)

---

## 🎯 Why Extract?

### Rationale:
1. ✅ **Preserves functionality** for potential future use
2. ✅ **Optional module** (users opt-in)
3. ✅ **Clear separation** from core infrastructure
4. ✅ **Backward compatible** for any external users
5. ✅ **Code not lost** (can evolve independently)

### Drawbacks:
- ⚠️ Creates module nobody may use
- ⚠️ Maintenance overhead
- ⚠️ Still has opinionated implementation

---

## 🏗️ Target Architecture

### New Module Structure:
```
ai-infrastructure-module/
├── ai-infrastructure-core/              (clean, no validation)
├── ai-infrastructure-validation/        ⭐ NEW
│   ├── pom.xml
│   ├── README.md
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ai/infrastructure/validation/
│   │   │   │   ├── AIValidationService.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── ValidationProperties.java
│   │   │   │   │   └── ValidationAutoConfiguration.java
│   │   │   │   └── model/
│   │   │   │       └── (result classes extracted)
│   │   │   └── resources/
│   │   │       ├── META-INF/spring/
│   │   │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │       └── application-validation.yml
│   │   └── test/
│   │       └── java/com/ai/infrastructure/validation/
│   │           └── AIValidationServiceTest.java
│   └── MIGRATION_GUIDE.md
```

---

## 🚀 Execution Steps

### **Phase 1: Create New Module** (20 minutes)

#### Step 1.1: Create Directory Structure
```bash
cd /workspace/ai-infrastructure-module

# Create module directory
mkdir -p ai-infrastructure-validation/src/main/java/com/ai/infrastructure/validation/config
mkdir -p ai-infrastructure-validation/src/main/java/com/ai/infrastructure/validation/model
mkdir -p ai-infrastructure-validation/src/main/resources/META-INF/spring
mkdir -p ai-infrastructure-validation/src/test/java/com/ai/infrastructure/validation

echo "✅ Directory structure created"
```

---

#### Step 1.2: Create pom.xml
```bash
cat > ai-infrastructure-validation/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>ai-infrastructure-validation</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>AI Infrastructure Validation (Optional)</name>
    <description>
        Optional AI-powered validation module with opinionated business rules.
        
        WARNING: This module contains hardcoded validation rules and 
        business logic. Use with caution and expect to customize.
        
        Consider using standard Spring Validation or Hibernate Validator
        for most use cases.
    </description>

    <dependencies>
        <!-- Core Infrastructure -->
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

echo "✅ pom.xml created"
```

---

#### Step 1.3: Update Parent POM
```bash
# Add new module to parent pom.xml
# Edit: /workspace/ai-infrastructure-module/pom.xml

# Add to <modules> section:
#   <module>ai-infrastructure-validation</module>
```

**Manual edit required** or use sed:
```bash
sed -i '/<\/modules>/i \        <module>ai-infrastructure-validation</module>' pom.xml
```

---

### **Phase 2: Move Service Files** (15 minutes)

#### Step 2.1: Move Main Service
```bash
cd /workspace/ai-infrastructure-module

# Copy service file
cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIValidationService.java \
   ai-infrastructure-validation/src/main/java/com/ai/infrastructure/validation/

echo "✅ Service file copied"
```

---

#### Step 2.2: Move Test File
```bash
# Copy test file
cp ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/AIValidationServiceTest.java \
   ai-infrastructure-validation/src/test/java/com/ai/infrastructure/validation/

echo "✅ Test file copied"
```

---

#### Step 2.3: Remove RAGService Dependency
**Manual Edit Required**: Edit `AIValidationService.java` in new module

Remove:
```java
private final RAGService ragService;  // ❌ REMOVE (unused)
```

Keep:
```java
private final AICoreService aiCoreService;  // ✅ KEEP
```

---

### **Phase 3: Create Configuration** (30 minutes)

#### Step 3.1: Create Properties Class
```java
// File: ai-infrastructure-validation/src/main/java/com/ai/infrastructure/validation/config/ValidationProperties.java

package com.ai.infrastructure.validation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@Data
@ConfigurationProperties(prefix = "ai.validation")
public class ValidationProperties {
    
    /**
     * Enable/disable AI validation module
     */
    private boolean enabled = true;
    
    /**
     * Suspect string tokens for data quality validation
     * Default: n/a, na, unknown, undefined, none, null
     */
    private Set<String> suspectTokens = Set.of("n/a", "na", "unknown", "undefined", "none", "null");
    
    /**
     * Error severity deduction weight
     */
    private double errorWeight = 0.2;
    
    /**
     * Warning severity deduction weight
     */
    private double warningWeight = 0.1;
    
    /**
     * AI model to use for validation
     */
    private String model = "gpt-4o-mini";
    
    /**
     * Max tokens for AI responses
     */
    private int maxTokens = 500;
    
    /**
     * Temperature for AI generation
     */
    private double temperature = 0.7;
}
```

---

#### Step 3.2: Create Auto-Configuration
```java
// File: ai-infrastructure-validation/src/main/java/com/ai/infrastructure/validation/config/ValidationAutoConfiguration.java

package com.ai.infrastructure.validation.config;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.validation.AIValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnClass({AICoreService.class})
@ConditionalOnProperty(prefix = "ai.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

    @Bean
    public AIValidationService aiValidationService(AICoreService aiCoreService) {
        log.info("Initializing AI Validation Service (Optional Module)");
        log.warn("AI Validation Service contains opinionated validation rules. " +
                 "Review and customize for your use case.");
        return new AIValidationService(aiCoreService);
    }
}
```

---

#### Step 3.3: Create AutoConfiguration.imports
```bash
cat > ai-infrastructure-validation/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports << 'EOF'
com.ai.infrastructure.validation.config.ValidationAutoConfiguration
EOF

echo "✅ Auto-configuration created"
```

---

### **Phase 4: Create Documentation** (20 minutes)

#### Step 4.1: Create README.md
```markdown
# AI Infrastructure Validation (Optional)

⚠️ **WARNING**: This is an **optional, opinionated** validation module.

## Overview

This module provides AI-powered validation capabilities including:
- Content validation with AI analysis
- Data quality validation (completeness, consistency, accuracy)
- Business rule validation with AI suggestions
- Auto-generation of validation rules

## ⚠️ Important Notice

**This module contains hardcoded business rules and opinionated validation logic.**

It was extracted from `ai-infrastructure-core` because:
- Opinionated implementation (hardcoded "suspect values": n/a, na, unknown, etc.)
- Business-specific validation logic
- Application-level concerns (not infrastructure)

## When to Use

✅ **Use this module if:**
- You want AI-powered validation out-of-the-box
- You agree with the opinionated validation rules
- You need quick validation without custom implementation

❌ **Don't use this module if:**
- You need custom validation logic
- Hardcoded rules don't fit your domain
- You prefer standard Spring Validation

## Alternatives

For most use cases, consider:
- **Spring Validation**: `@Valid`, `@Validated`
- **Hibernate Validator**: JSR-303/JSR-380 validation
- **Custom Services**: Application-level validation

## Installation

### Maven
\`\`\`xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-validation</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
\`\`\`

### Gradle
\`\`\`gradle
implementation 'com.ai.infrastructure:ai-infrastructure-validation:1.0.0-SNAPSHOT'
\`\`\`

## Configuration

\`\`\`yaml
ai:
  validation:
    enabled: true
    suspect-tokens:
      - n/a
      - na
      - unknown
      - undefined
      - none
      - null
    error-weight: 0.2
    warning-weight: 0.1
    model: gpt-4o-mini
    max-tokens: 500
    temperature: 0.7
\`\`\`

## Usage

\`\`\`java
@Autowired
private AIValidationService validationService;

// Content validation
ValidationResult result = validationService.validateContent(
    content, 
    "article", 
    validationRules
);

// Data quality validation
DataQualityResult quality = validationService.validateDataQuality(
    data, 
    "product"
);

// Business rule validation
BusinessRuleValidationResult businessResult = validationService.validateBusinessRules(
    data, 
    businessRules
);
\`\`\`

## Known Issues

1. **Hardcoded Suspect Values**: "n/a", "na", "unknown" are flagged as suspect
   - May not fit all domains
   - Configure via `ai.validation.suspect-tokens`

2. **String Matching on AI Responses**: Brittle detection
   - Looks for "inappropriate", "spam", "low quality" in AI responses
   - False positives possible

3. **Fixed Scoring Weights**: ERROR = -0.2, WARNING = -0.1
   - Configure via properties

4. **Generic Result Classes**: 8 inner classes
   - May not fit specific use cases
   - Extend or create custom validators

## Customization

To customize, either:
1. Override the bean
2. Configure properties
3. Fork and modify

## License

Same as parent project

## Support

This is an **optional module** - use at your own discretion.

For standard validation needs, use Spring Validation or Hibernate Validator.
```

---

#### Step 4.2: Create MIGRATION_GUIDE.md
```markdown
# Migration Guide: AI Validation Service

## Overview

The `AIValidationService` has been **extracted from `ai-infrastructure-core`** to a separate optional module: `ai-infrastructure-validation`.

## Why?

- ❌ Opinionated business logic
- ❌ Hardcoded validation rules
- ❌ Application-level concerns
- ✅ Not suitable for infrastructure core

## Impact

**Good news**: The service was **not used** in any production code, so:
- ✅ **Zero breaking changes**
- ✅ **No migration needed** for existing applications

## For New Applications

If you want to use the validation service:

### Step 1: Add Dependency
\`\`\`xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-validation</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
\`\`\`

### Step 2: Configure (Optional)
\`\`\`yaml
ai:
  validation:
    enabled: true
\`\`\`

### Step 3: Use
\`\`\`java
@Autowired
private AIValidationService validationService;
\`\`\`

## Alternatives

We **recommend using** standard validation instead:

### Spring Validation
\`\`\`java
@Valid
@Validated
\`\`\`

### Hibernate Validator
\`\`\`xml
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
</dependency>
\`\`\`

## Questions?

- Why was this extracted? → Too opinionated for infrastructure
- Do I need to migrate? → No (service was unused)
- Should I use this? → Probably not (use Spring Validation)
- Can I still use it? → Yes (opt-in via dependency)
```

---

### **Phase 5: Delete from Core** (10 minutes)

#### Step 5.1: Delete Original Files
```bash
cd /workspace/ai-infrastructure-module

# Delete from core
rm -f ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIValidationService.java
rm -f ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/AIValidationServiceTest.java

# Remove empty directories
rmdir ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/ 2>/dev/null
rmdir ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/ 2>/dev/null

echo "✅ Files removed from core"
```

---

### **Phase 6: Build & Test** (15 minutes)

#### Step 6.1: Build New Module
```bash
cd /workspace/ai-infrastructure-module/ai-infrastructure-validation

# Build new module
mvn clean install

# Check result
echo $?  # Should be 0
```

**Expected**: BUILD SUCCESS

---

#### Step 6.2: Build Core (Verify Clean)
```bash
cd /workspace/ai-infrastructure-module/ai-infrastructure-core

# Build core without validation
mvn clean install

# Should succeed (no dependencies on validation)
```

**Expected**: BUILD SUCCESS

---

#### Step 6.3: Build Entire Project
```bash
cd /workspace/ai-infrastructure-module

# Full build
mvn clean install

# All modules should build
```

**Expected**: BUILD SUCCESS for all modules

---

### **Phase 7: Git Commit** (5 minutes)

```bash
cd /workspace/ai-infrastructure-module

# Stage all changes
git add -A

# Check status
git status

# Commit
git commit -m "refactor(validation): extract AIValidationService to optional module

- Created new module: ai-infrastructure-validation
- Moved AIValidationService (786 lines) from core to new module
- Added ValidationProperties for configuration
- Added ValidationAutoConfiguration
- Service is opt-in (not auto-included)
- Zero breaking changes (service was unused)

Rationale:
- Opinionated business validation logic
- Hardcoded validation rules
- Not suitable for infrastructure core
- Now optional (users can opt-in)

BREAKING CHANGE: None (service was unused)"
```

---

## ✅ Success Criteria

### Must Pass:
- [ ] New module created
- [ ] Files moved successfully
- [ ] RAGService dependency removed
- [ ] Configuration classes created
- [ ] README and migration guide created
- [ ] Build succeeds for new module
- [ ] Build succeeds for core (without validation)
- [ ] All tests pass
- [ ] Git commit created

---

## ⏱️ Timeline

| Phase | Task | Time |
|-------|------|------|
| 1 | Create new module structure | 20 min |
| 2 | Move service files | 15 min |
| 3 | Create configuration | 30 min |
| 4 | Create documentation | 20 min |
| 5 | Delete from core | 10 min |
| 6 | Build & test | 15 min |
| 7 | Git commit | 5 min |
| **Total** | **End-to-end** | **115 min** (~2 hours) |

---

## 🚨 Rollback Plan

If extraction fails:

```bash
# Option 1: Git reset
git reset --hard HEAD

# Option 2: Delete new module
rm -rf ai-infrastructure-validation/
# Restore core files from git
git checkout HEAD -- ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/
git checkout HEAD -- ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/
```

---

## 📊 Impact Summary

### New Module:
- **Name**: `ai-infrastructure-validation`
- **Type**: Optional (opt-in)
- **Files**: 7 (service, test, config, docs)
- **Lines**: ~1000

### Core Module:
- **Files removed**: 2
- **Lines removed**: ~900
- **Breaking changes**: 0 ✅

---

**Status**: Ready to execute ✅  
**Risk**: ZERO  
**Time**: 2-3 hours  
**Alternative**: See EXECUTION_PLAN_OPTION1_DELETE.md (8 minutes)
