# Web Module Extraction - Quick Start

## 🚀 TL;DR - Execute This

```bash
# Navigate to ai-infrastructure-module
cd /workspace/ai-infrastructure-module

# Run the extraction script
bash extract_web_module.sh
```

---

## 📋 Manual Execution (Step-by-Step)

### Step 1: Create Module (5 minutes)

```bash
cd /workspace/ai-infrastructure-module

# Create directory structure
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config
mkdir -p ai-infrastructure-web/src/main/resources/META-INF/spring
mkdir -p ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/controller
```

### Step 2: Create pom.xml (Copy & Paste)

See full `pom.xml` in main implementation plan.

Key dependencies:
- `ai-infrastructure-core`
- `spring-boot-starter-web`
- `spring-boot-starter-validation`

### Step 3: Move Controllers (10 minutes)

```bash
# Copy controllers
cd /workspace/ai-infrastructure-module
for controller in AdvancedRAGController AIAuditController AIComplianceController AIMonitoringController AIProfileController AISecurityController; do
    cp ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/${controller}.java \
       ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/
done

# Update package declarations
cd ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller
for file in *.java; do
    sed -i 's/package com.ai.infrastructure.controller;/package com.ai.infrastructure.web.controller;/g' "$file"
done
```

### Step 4: Create Configuration (15 minutes)

Create these files (see templates in implementation plan):
1. `AIWebProperties.java`
2. `AIWebAutoConfiguration.java`
3. `org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Step 5: Build (5 minutes)

```bash
cd /workspace/ai-infrastructure-module/ai-infrastructure-web
mvn clean install
```

### Step 6: Update Parent POM (2 minutes)

Add to `ai-infrastructure-spring-boot-starter/pom.xml`:

```xml
<module>ai-infrastructure-web</module>
```

### Step 7: Verify (5 minutes)

```bash
cd /workspace/ai-infrastructure-module
mvn clean install

# Check that all modules build
```

---

## ✅ Success Checklist

- [ ] Module directory created
- [ ] pom.xml created
- [ ] 6 controllers moved
- [ ] Package declarations updated
- [ ] AutoConfiguration created
- [ ] Parent POM updated
- [ ] Builds successfully
- [ ] Tests pass

---

## 📊 What Gets Moved

| File | Lines | Endpoints | From | To |
|------|-------|-----------|------|-----|
| AdvancedRAGController.java | 95 | 3 | core | web |
| AIAuditController.java | 226 | 11 | core | web |
| AIComplianceController.java | 72 | 2 | core | web |
| AIMonitoringController.java | 279 | 15 | core | web |
| AIProfileController.java | 358 | 22 | core | web |
| AISecurityController.java | 141 | 6 | core | web |
| **TOTAL** | **1,171** | **59** | | |

---

## 🎯 After Extraction

### Core Module
- No longer contains controllers
- No Spring Web dependency required
- Can be used as pure library

### Web Module
- Contains all 6 controllers
- 59 REST endpoints
- Optional dependency
- Requires Spring Web

### Usage

**Before** (everything in core):
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
<!-- Controllers automatically included -->
```

**After** (modular):
```xml
<!-- Core services -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>

<!-- REST endpoints (optional) -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-web</artifactId>
</dependency>
```

---

## ⚡ Quick Commands Reference

```bash
# Create structure
mkdir -p ai-infrastructure-web/src/{main,test}/{java,resources}

# Copy controllers
cp core/controller/*.java web/controller/

# Update packages
sed -i 's/controller;/web.controller;/g' *.java

# Build
mvn clean install

# Test
mvn test

# Run sample
cd test-app && mvn spring-boot:run
```

---

## 🔧 Configuration

```yaml
# Enable/disable web module
ai:
  web:
    enabled: true
    
# Disable specific controllers
ai:
  web:
    controllers:
      audit: false
      security: false
```

---

## 📚 Documentation

- **Full Plan**: `WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md`
- **Analysis**: `CONTROLLER_REAL_JOB_ANALYSIS.md`
- **Changes**: `CHANGE_REQUESTS_LOG.md`

---

## ⏱️ Time Estimates

| Task | Time |
|------|------|
| Create structure | 5 min |
| Create pom.xml | 10 min |
| Move controllers | 10 min |
| Create config | 15 min |
| Create tests | 2 hours |
| Documentation | 1 hour |
| Verification | 30 min |
| **TOTAL** | **~4-5 hours** |

(With automation: ~30 minutes)

---

## 🆘 Troubleshooting

**Build fails**: Check pom.xml dependencies  
**Controllers not loading**: Check AutoConfiguration.imports  
**Imports broken**: Update package declarations  
**Tests fail**: Mock the services properly  

---

## 🎉 Success Criteria

✅ New module builds  
✅ All endpoints work  
✅ Core builds without controllers  
✅ Tests pass  
✅ Documentation complete  

---

**Ready? Start with Step 1!**
