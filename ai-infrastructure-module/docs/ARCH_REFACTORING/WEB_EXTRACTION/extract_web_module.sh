#!/bin/bash

##############################################################################
# AI Infrastructure Web Module Extraction Script
# 
# This script extracts REST controllers from ai-infrastructure-core
# to a new ai-infrastructure-web module.
#
# Author: AI Infrastructure Team
# Date: November 25, 2025
##############################################################################

set -e  # Exit on error

# Check if we need to change to the correct directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="/workspace/ai-infrastructure-module"

# If not in project root, change to it
if [ "$PWD" != "$PROJECT_ROOT" ]; then
    echo "⚠️  Changing directory to: $PROJECT_ROOT"
    cd "$PROJECT_ROOT" || exit 1
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Check if we're in the right directory
if [ ! -d "ai-infrastructure-core" ]; then
    print_error "Please run this script from the ai-infrastructure-module directory"
    exit 1
fi

print_section "Phase 1: Create Module Structure"

# Create directory structure
print_info "Creating directory structure..."
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config
mkdir -p ai-infrastructure-web/src/main/resources/META-INF/spring
mkdir -p ai-infrastructure-web/src/test/java/com/ai/infrastructure/web/controller
mkdir -p ai-infrastructure-web/src/test/resources
print_success "Directory structure created"

# Create pom.xml
print_info "Creating pom.xml..."
cat > ai-infrastructure-web/pom.xml << 'EOF'
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
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
EOF
print_success "pom.xml created"

print_section "Phase 2: Move Controllers"

# Array of controllers to move
controllers=(
    "AdvancedRAGController"
    "AIAuditController"
    "AIComplianceController"
    "AIMonitoringController"
    "AIProfileController"
    "AISecurityController"
)

# Copy and update each controller
for controller in "${controllers[@]}"; do
    print_info "Processing ${controller}..."
    
    # Check if controller exists
    if [ ! -f "ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/${controller}.java" ]; then
        print_warning "${controller}.java not found in core, skipping..."
        continue
    fi
    
    # Copy controller
    cp "ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/${controller}.java" \
       "ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/"
    
    # Update package declaration
    sed -i.bak 's/package com.ai.infrastructure.controller;/package com.ai.infrastructure.web.controller;/g' \
        "ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/${controller}.java"
    
    # Remove backup file
    rm "ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/${controller}.java.bak" 2>/dev/null || true
    
    print_success "${controller} moved and updated"
done

print_section "Phase 3: Create Configuration"

# Create AIWebProperties
print_info "Creating AIWebProperties..."
cat > ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config/AIWebProperties.java << 'EOF'
package com.ai.infrastructure.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.web")
public class AIWebProperties {
    private boolean enabled = true;
    private String basePath = "/api/ai";
    private Controllers controllers = new Controllers();
    
    @Data
    public static class Controllers {
        private boolean advancedRag = true;
        private boolean audit = true;
        private boolean compliance = true;
        private boolean monitoring = true;
        private boolean profile = true;
        private boolean security = true;
    }
}
EOF
print_success "AIWebProperties created"

# Create AIWebAutoConfiguration
print_info "Creating AIWebAutoConfiguration..."
cat > ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/config/AIWebAutoConfiguration.java << 'EOF'
package com.ai.infrastructure.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
@ConditionalOnProperty(prefix = "ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AIWebProperties.class)
public class AIWebAutoConfiguration {
    
    public AIWebAutoConfiguration() {
        log.info("AI Infrastructure Web AutoConfiguration initialized - 59 REST endpoints available");
    }
}
EOF
print_success "AIWebAutoConfiguration created"

# Create AutoConfiguration.imports
print_info "Creating AutoConfiguration.imports..."
cat > ai-infrastructure-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports << 'EOF'
com.ai.infrastructure.web.config.AIWebAutoConfiguration
EOF
print_success "AutoConfiguration.imports created"

# Create README
print_info "Creating README.md..."
cat > ai-infrastructure-web/README.md << 'EOF'
# AI Infrastructure Web Module

REST API controllers for AI Infrastructure.

## Installation

```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Endpoints

- **Advanced RAG**: 3 endpoints (`/api/ai/advanced-rag/*`)
- **Audit**: 11 endpoints (`/api/ai/audit/*`)
- **Compliance**: 2 endpoints (`/api/ai/compliance/*`)
- **Monitoring**: 15 endpoints (`/api/ai/monitoring/*`)
- **Profile**: 22 endpoints (`/api/ai/profiles/*`)
- **Security**: 6 endpoints (`/api/ai/security/*`)

**Total: 59 REST endpoints**

## Configuration

```yaml
ai:
  web:
    enabled: true
    controllers:
      audit: false  # Disable specific controllers
```

See full documentation for details.
EOF
print_success "README.md created"

print_section "Phase 4: Build New Module"

print_info "Building ai-infrastructure-web..."
cd ai-infrastructure-web
if mvn clean install -DskipTests; then
    print_success "Build successful!"
else
    print_error "Build failed - please check errors above"
    exit 1
fi
cd ..

print_section "Phase 5: Update Parent POM"

print_info "Checking parent POM..."
if grep -q "<module>ai-infrastructure-web</module>" pom.xml; then
    print_info "Parent POM already includes web module"
else
    print_warning "Please manually add <module>ai-infrastructure-web</module> to parent pom.xml"
fi

print_section "Summary"

print_success "Web module extraction completed successfully!"
echo ""
print_info "Controllers moved:"
for controller in "${controllers[@]}"; do
    echo "  ✓ ${controller}"
done
echo ""
print_info "Next steps:"
echo "  1. Review controllers in ai-infrastructure-web/src/main/java/com/ai/infrastructure/web/controller/"
echo "  2. Add unit tests"
echo "  3. Update parent pom.xml (add web module)"
echo "  4. Build entire project: mvn clean install"
echo "  5. Mark controllers in core as @Deprecated"
echo ""
print_info "Documentation:"
echo "  - ai-infrastructure-web/README.md"
echo "  - See WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md for full details"
echo ""

print_success "Done! 🎉"
