# Pre-Release Cleanup & Module Separation Plan
## Preparing AI Fabric Framework for Open Source Launch

**Version**: 1.0
**Date**: 2026-01-24
**Status**: Planning Phase
**Target**: Clean separation of Community and Enterprise modules

---

## Executive Summary

This document outlines the cleanup and restructuring tasks required before launching AI Fabric Framework as an open source project with a dual-licensing model (Community + Enterprise).

**Goals**:
1. ✅ **Clean separation** - Community and Enterprise modules clearly separated
2. ✅ **Production-ready** - Remove debug code, internal references, TODOs
3. ✅ **Documented** - Every module has clear documentation
4. ✅ **Tested** - 80%+ test coverage on core modules
5. ✅ **Secure** - No secrets, credentials, or sensitive data
6. ✅ **Licensed** - Correct license headers on all files

**Timeline**: 2-3 weeks before launch

<<<<<<< Updated upstream
=======
**Implementation note (this repo)**: We are targeting a **monorepo** separation for beta. See:
- `changes/release/MONOREPO_COMMUNITY_ENTERPRISE_SEPARATION_CHANGE_PLAN.md`
- `changes/release/BETA_RELEASE_SCOPE_AND_GATES.md`
- `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`

>>>>>>> Stashed changes
---

## Table of Contents

1. [Module Separation Strategy](#module-separation-strategy)
2. [Repository Structure](#repository-structure)
3. [Code Cleanup Tasks](#code-cleanup-tasks)
4. [Documentation Requirements](#documentation-requirements)
5. [Quality Assurance](#quality-assurance)
6. [Security & Compliance](#security--compliance)
7. [Release Checklist](#release-checklist)
8. [Migration Scripts](#migration-scripts)

---

## Module Separation Strategy

### Recommended Approach: Separate Repositories

**Why separate repositories?**
- ✅ Clear licensing boundaries (Apache 2.0 vs BSL 1.1)
- ✅ Different access controls (public vs requires license for Maven)
- ✅ Independent release cycles
- ✅ Easier to manage contributions (Community = open, Enterprise = restricted)
- ✅ Cleaner for users (clone only what they need)

**Alternative**: Monorepo with separate top-level folders (easier during development, but more complex licensing)

---

### Repository Structure (Recommended)

#### Repository 1: Community Edition (Public)

```
github.com/your-org/ai-fabric-framework
├── README.md                           # Main project README
├── LICENSE                             # Apache License 2.0
├── CONTRIBUTING.md                     # Contribution guidelines
├── CODE_OF_CONDUCT.md                  # Community standards
├── SECURITY.md                         # Security policy
├── .github/
│   ├── workflows/                      # CI/CD pipelines
│   ├── ISSUE_TEMPLATE/                 # Issue templates
│   └── PULL_REQUEST_TEMPLATE.md        # PR template
│
├── ai-infrastructure-module/           # Core framework modules
│   ├── ai-fabric-core/                 # Core orchestration
│   ├── ai-infrastructure-rag/          # RAG pipeline
│   ├── ai-infrastructure-chat-session/ # Chat session management
│   ├── ai-infrastructure-pii/          # Basic PII detection
│   ├── ai-infrastructure-governance/   # Governance SPI (interfaces only)
│   ├── ai-infrastructure-core/         # Shared utilities
│   ├── ai-fabric-starter/              # All-in-one starter
│   └── ai-fabric-provider-starter/     # Lightweight starter
│
├── ai-community-providers/             # Community LLM providers
│   ├── ai-infrastructure-provider-openai/
│   └── ai-infrastructure-provider-onnx/
│
├── ai-community-vector-dbs/            # Community vector databases
│   ├── ai-infrastructure-vector-lucene/
│   └── ai-infrastructure-vector-memory/
│
├── Real_Apps/                          # Demo applications
│   ├── chat-capabilities-demo/
│   ├── getting-started-tutorial/
│   └── simple-rag-example/
│
├── docs/                               # Documentation
│   ├── getting-started.md
│   ├── architecture.md
│   ├── action-handlers.md
│   ├── rag-pipeline.md
│   ├── configuration-reference.md
│   └── migration-guides/
│
└── pom.xml                             # Parent POM
```

---

#### Repository 2: Enterprise Edition (Public - Different License)

```
github.com/your-org/ai-fabric-enterprise
├── README.md                           # Enterprise features overview
├── LICENSE                             # Business Source License 1.1
├── NOTICE.md                           # Commercial license info
├── CONTRIBUTING.md                     # CLA required
├── .github/
│   └── workflows/                      # CI/CD pipelines
│
├── ai-enterprise-module/               # Enterprise modules
│   ├── ai-enterprise-core/             # Enterprise shared utilities
│   ├── ai-enterprise-multi-tenancy/    # Multi-tenancy support
│   ├── ai-enterprise-rbac/             # Role-based access control
│   ├── ai-enterprise-cost-management/  # Usage tracking & budgets
│   ├── ai-enterprise-monitoring/       # Prometheus, Grafana, OpenTelemetry
│   ├── ai-enterprise-ha/               # High availability & clustering
│   ├── ai-enterprise-experimentation/  # A/B testing framework
│   │
│   ├── compliance/                     # Compliance modules
│   │   ├── ai-enterprise-compliance-gdpr/
│   │   ├── ai-enterprise-compliance-hipaa/
│   │   └── ai-enterprise-compliance-soc2/
│   │
│   ├── security/                       # Advanced security
│   │   ├── ai-enterprise-security-ml-pii/    # ML-based PII detection
│   │   ├── ai-enterprise-security-rbac/      # RBAC implementation
│   │   └── ai-enterprise-security-sso/       # SSO integrations
│   │
│   └── ai-enterprise-starter/          # Enterprise all-in-one starter
│
├── ai-enterprise-providers/            # Premium LLM providers
│   ├── ai-infrastructure-provider-azure/
│   ├── ai-infrastructure-provider-anthropic/
│   ├── ai-infrastructure-provider-gemini/
│   └── ai-infrastructure-provider-cohere/
│
├── ai-enterprise-vector-dbs/           # Premium vector databases
│   ├── ai-infrastructure-vector-pinecone/
│   ├── ai-infrastructure-vector-qdrant/
│   ├── ai-infrastructure-vector-milvus/
│   └── ai-infrastructure-vector-weaviate/
│
├── Real_Apps/                          # Enterprise demo apps
│   ├── multi-tenant-saas-demo/
│   ├── enterprise-compliance-demo/
│   └── high-availability-demo/
│
├── docs/                               # Enterprise documentation
│   ├── installation.md
│   ├── multi-tenancy-guide.md
│   ├── rbac-configuration.md
│   ├── monitoring-setup.md
│   ├── compliance-guides/
│   └── enterprise-features.md
│
└── pom.xml                             # Parent POM
```

---

### Alternative: Monorepo Structure (Development Phase)

If you prefer to keep everything in one repository during development:

```
ai-fabric-framework/
├── LICENSE-COMMUNITY                   # Apache 2.0
├── LICENSE-ENTERPRISE                  # BSL 1.1
├── README.md
│
├── community-modules/                  # Apache 2.0 licensed
│   ├── ai-infrastructure-module/
│   ├── ai-community-providers/
│   ├── ai-community-vector-dbs/
│   └── Real_Apps/
│
├── enterprise-modules/                 # BSL 1.1 licensed
│   ├── ai-enterprise-module/
│   ├── ai-enterprise-providers/
│   ├── ai-enterprise-vector-dbs/
│   └── Real_Apps/
│
└── docs/
    ├── community/
    └── enterprise/
```

**Note**: Can split into separate repos later when ready for launch.

---

## Code Cleanup Tasks

### Phase 1: Remove Internal/Sensitive Content (Week 1)

#### 1.1 Remove Secrets & Credentials

**Tasks**:
- [ ] Scan for hardcoded API keys
  ```bash
  # Search for common secret patterns
  grep -r "api.key\s*=\s*['\"]" --include="*.java" --include="*.yml" --include="*.properties"
  grep -r "password\s*=\s*['\"]" --include="*.java" --include="*.yml" --include="*.properties"
  grep -r "secret\s*=\s*['\"]" --include="*.java" --include="*.yml" --include="*.properties"
  ```
- [ ] Replace with environment variable references
- [ ] Review all `application.yml` and `application.properties` files
- [ ] Check for database credentials
- [ ] Verify no OAuth tokens or session secrets

**Example fix**:
```yaml
# Before (BAD - hardcoded)
ai.providers.openai.api-key: sk-proj-abc123...

# After (GOOD - environment variable)
ai.providers.openai.api-key: ${OPENAI_API_KEY}
```

---

#### 1.2 Remove Internal References

**Tasks**:
- [ ] Search for internal company names
  ```bash
  grep -r "YourCompanyInc" --include="*.java" --include="*.md"
  grep -r "internal.company.com" --include="*.java" --include="*.yml"
  ```
- [ ] Replace internal URLs with public ones or remove
- [ ] Remove references to internal tools (Jira, Confluence, etc.)
- [ ] Check comments for sensitive information
- [ ] Review package names for internal references

**Example**:
```java
// Before
// TODO: Integrate with internal-auth-service.company.com

// After
// TODO: Add authentication integration example
```

---

#### 1.3 Clean Debug & Development Code

**Tasks**:
- [ ] Remove `System.out.println()` debug statements
  ```bash
  grep -r "System.out.println" --include="*.java"
  ```
- [ ] Remove commented-out code blocks (keep only essential comments)
- [ ] Remove unused imports
- [ ] Remove experimental/WIP classes
- [ ] Clean up `TODO` comments (resolve or make generic)

**Script to find debug code**:
```bash
# Find debug statements
grep -rn "System.out" --include="*.java" > debug_statements.txt
grep -rn "printStackTrace" --include="*.java" >> debug_statements.txt
grep -rn "//TODO:" --include="*.java" > todos.txt
```

---

#### 1.4 Remove Test Data

**Tasks**:
- [ ] Review demo applications for test data
- [ ] Remove real customer names/emails from examples
- [ ] Use generic placeholders (user@example.com, Acme Corp, etc.)
- [ ] Clean sample datasets
- [ ] Remove internal project references

**Example**:
```java
// Before
String customerEmail = "john.doe@realcompany.com";

// After
String customerEmail = "user@example.com";
```

---

### Phase 2: Code Quality Improvements (Week 2)

#### 2.1 Code Formatting & Style

**Tasks**:
- [ ] Run code formatter (Google Java Format or similar)
  ```bash
  # Install Google Java Format
  # Format all Java files
  find . -name "*.java" -exec google-java-format -i {} \;
  ```
- [ ] Consistent indentation (tabs vs spaces)
- [ ] Remove trailing whitespace
- [ ] Consistent naming conventions
- [ ] Add missing `@Override` annotations

---

#### 2.2 Documentation Cleanup

**Tasks**:
- [ ] Add JavaDoc to all public classes
- [ ] Add JavaDoc to all public methods
- [ ] Document complex algorithms
- [ ] Add package-level documentation (package-info.java)
- [ ] Update outdated comments

**Example**:
```java
/**
 * Handles adding items to a user's shopping cart.
 *
 * <p>This action requires user authentication and performs the following:
 * <ul>
 *   <li>Validates the product SKU exists</li>
 *   <li>Checks inventory availability</li>
 *   <li>Creates or retrieves the user's active cart</li>
 *   <li>Adds the item with specified quantity</li>
 * </ul>
 *
 * @see CartService
 * @since 1.0.0
 */
@AIAction(
    name = "add_to_cart",
    description = "Add a product to shopping cart",
    category = "commerce",
    accessMode = com.ai.infrastructure.intent.action.ActionAccessMode.WRITE_ONLY,
    requiresConfirmation = true
)
public class AddToCartActionHandler {
    // ...
}
```

---

#### 2.3 Deprecation & API Cleanup

**Tasks**:
- [ ] Mark deprecated methods with `@Deprecated` and JavaDoc
- [ ] Remove unused public methods
- [ ] Consolidate duplicate code
- [ ] Simplify overly complex methods
- [ ] Review public API surface (minimize exposed internals)

**Example**:
```java
/**
 * @deprecated Use {@link #executeAction(Map, ActionContext)} instead.
 * This method will be removed in version 2.0.
 */
@Deprecated(since = "1.0", forRemoval = true)
public ActionResult executeAction(Map<String, Object> params, String userId) {
    // Old implementation
}
```

---

### Phase 3: Module Separation (Week 2)

#### 3.1 Identify Enterprise-Only Code

**Criteria for Enterprise modules**:
- Multi-tenancy features
- Advanced security (RBAC, SSO, ML-based PII)
- Premium LLM providers (Azure, Anthropic, Gemini, Cohere)
- Premium vector databases (Pinecone, Qdrant, Milvus, Weaviate)
- Cost management
- Advanced monitoring (Prometheus, Grafana)
- Compliance templates (GDPR, HIPAA, SOC2)
- High availability features

**Tasks**:
- [ ] Create inventory of enterprise features
- [ ] Identify dependencies between Community and Enterprise
- [ ] Plan module extraction order

---

#### 3.2 Move Enterprise Modules

**Script template** (for each enterprise module):

```bash
#!/bin/bash
# move-to-enterprise.sh

SOURCE_REPO="ai-fabric-framework"
TARGET_REPO="ai-fabric-enterprise"

MODULE_NAME="ai-infrastructure-provider-azure"
SOURCE_PATH="$SOURCE_REPO/ai-infrastructure-module/$MODULE_NAME"
TARGET_PATH="$TARGET_REPO/ai-enterprise-providers/$MODULE_NAME"

# Create target directory
mkdir -p "$TARGET_PATH"

# Move module preserving git history
cd "$SOURCE_REPO"
git filter-repo --path "$SOURCE_PATH" --path-rename "$SOURCE_PATH:$TARGET_PATH"

# Or simpler: just copy and preserve history manually
cp -r "$SOURCE_PATH" "$TARGET_PATH"

echo "Moved $MODULE_NAME to enterprise repository"
```

**Modules to move**:
- [ ] Azure OpenAI provider → `ai-enterprise-providers/`
- [ ] Anthropic provider → `ai-enterprise-providers/`
- [ ] Gemini provider → `ai-enterprise-providers/`
- [ ] Cohere provider → `ai-enterprise-providers/`
- [ ] Pinecone vector DB → `ai-enterprise-vector-dbs/`
- [ ] Qdrant vector DB → `ai-enterprise-vector-dbs/`
- [ ] Milvus vector DB → `ai-enterprise-vector-dbs/`
- [ ] Weaviate vector DB → `ai-enterprise-vector-dbs/`

---

#### 3.3 Update Dependencies

After moving modules:

**Community `pom.xml`** - Remove enterprise dependencies:
```xml
<!-- Remove these from Community -->
<!--
<dependency>
    <groupId>com.ai.fabric.enterprise</groupId>
    <artifactId>ai-infrastructure-provider-azure</artifactId>
</dependency>
-->
```

**Enterprise `pom.xml`** - Add Community dependency:
```xml
<!-- Enterprise depends on Community -->
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>${community.version}</version>
</dependency>
```

---

#### 3.4 Create New Enterprise Modules

**New modules to create** (don't exist yet):

```bash
# Create enterprise-only modules
mkdir -p ai-fabric-enterprise/ai-enterprise-module/ai-enterprise-multi-tenancy
mkdir -p ai-fabric-enterprise/ai-enterprise-module/ai-enterprise-rbac
mkdir -p ai-fabric-enterprise/ai-enterprise-module/ai-enterprise-cost-management
mkdir -p ai-fabric-enterprise/ai-enterprise-module/ai-enterprise-monitoring
mkdir -p ai-fabric-enterprise/ai-enterprise-module/compliance/ai-enterprise-compliance-gdpr
mkdir -p ai-fabric-enterprise/ai-enterprise-module/compliance/ai-enterprise-compliance-hipaa
mkdir -p ai-fabric-enterprise/ai-enterprise-module/compliance/ai-enterprise-compliance-soc2
```

**Scaffold template**:
```
ai-enterprise-multi-tenancy/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/ai/fabric/enterprise/multitenancy/
│   │   │   ├── TenantContext.java
│   │   │   ├── TenantContextInterceptor.java
│   │   │   ├── TenantResolver.java
│   │   │   └── config/
│   │   │       └── MultiTenancyAutoConfiguration.java
│   │   └── resources/
│   │       ├── META-INF/spring.factories
│   │       └── application-multi-tenancy.yml
│   └── test/
│       └── java/com/ai/fabric/enterprise/multitenancy/
│           └── TenantContextInterceptorTest.java
└── README.md
```

---

## Documentation Requirements

### Community Edition Documentation

#### 1. Main README.md

**Required sections**:
- [ ] Project overview (what it is, why it exists)
- [ ] Key features (bullet points)
- [ ] Quick start (30-second install)
- [ ] Getting started tutorial (5-minute first app)
- [ ] Links to detailed docs
- [ ] Community links (Discord, issues, discussions)
- [ ] License information
- [ ] Badge section (build status, license, version)

**Template**:
```markdown
# AI Fabric Framework

> The Spring-native AI framework that makes building conversational AI applications as easy as writing a REST controller.

[![Build Status](https://github.com/your-org/ai-fabric-framework/workflows/CI/badge.svg)](https://github.com/your-org/ai-fabric-framework/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.ai.fabric/ai-fabric-core.svg)](https://search.maven.org/artifact/com.ai.fabric/ai-fabric-core)

## What is AI Fabric?

AI Fabric is a production-ready framework for building AI-powered applications using Spring Boot. It combines RAG (Retrieval-Augmented Generation), action execution, and multi-turn chat in a single, easy-to-use framework.

**Perfect for**: Customer support chatbots, e-commerce assistants, knowledge bases, internal tools

## Features

- ✅ **RAG Pipeline** - Semantic search + LLM generation
- ✅ **Action Handlers** - Execute business logic from natural language
- ✅ **Multi-turn Chat** - Conversation state management
- ✅ **Spring Native** - Familiar annotations, dependency injection
- ✅ **Production Ready** - Security, governance, observability built-in

## Quick Start

Add dependency:
```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Create your first action:
```java
@AIAction(
    name = "greet",
    description = "Greet the user",
    category = "general",
    accessMode = com.ai.infrastructure.intent.action.ActionAccessMode.READ,
    requiresConfirmation = false
)
@Component
public class GreetActionHandler {
    @ActionExecute
    public ActionResult execute(@Param String name) {
        return ActionResult.success("Hello, " + name + "!");
    }
}
```

Configure:
```yaml
ai:
  providers:
    llm-provider: openai
    model: gpt-4
  vector-db:
    type: lucene
```

Run!

## Documentation

- [Getting Started Guide](docs/getting-started.md)
- [Architecture Overview](docs/architecture.md)
- [Action Handlers](docs/action-handlers.md)
- [RAG Pipeline](docs/rag-pipeline.md)
- [Configuration Reference](docs/configuration-reference.md)

## Community

- [Discord](https://discord.gg/your-invite)
- [GitHub Discussions](https://github.com/your-org/ai-fabric-framework/discussions)
- [Contributing Guide](CONTRIBUTING.md)

## Enterprise Edition

Need multi-tenancy, RBAC, compliance (GDPR/HIPAA), or 24/7 support?
See [AI Fabric Enterprise](https://github.com/your-org/ai-fabric-enterprise)

## License

Apache License 2.0 - see [LICENSE](LICENSE)
```

---

#### 2. CONTRIBUTING.md

**Required sections**:
- [ ] Code of conduct reference
- [ ] How to report bugs
- [ ] How to suggest features
- [ ] Development setup
- [ ] Pull request process
- [ ] Code style guide
- [ ] Testing requirements

---

#### 3. docs/ Folder Structure

**Required documentation**:
```
docs/
├── getting-started.md              # 10-minute tutorial
├── architecture.md                 # System design overview
├── action-handlers.md              # How to create actions
├── rag-pipeline.md                 # RAG configuration
├── chat-sessions.md                # Multi-turn conversations
├── configuration-reference.md      # All YAML settings
├── security.md                     # PII, access control
├── deployment/
│   ├── docker.md
│   ├── kubernetes.md
│   └── aws.md
├── examples/
│   ├── simple-chatbot.md
│   ├── ecommerce-assistant.md
│   └── knowledge-base.md
└── migration-guides/
    └── v0-to-v1.md
```

---

### Enterprise Edition Documentation

#### 1. Main README.md

**Template**:
```markdown
# AI Fabric Enterprise Edition

> Production-grade features for AI Fabric Framework: multi-tenancy, RBAC, compliance, and 24/7 support.

⚠️ **License**: Business Source License 1.1
✅ **Free for**: Development, testing, evaluation
💰 **Paid for**: Production use ([pricing](https://yourcompany.com/pricing))

## Enterprise Features

- 🏢 **Multi-Tenancy** - Isolate data per customer
- 🔐 **RBAC** - Role-based access control
- 📊 **Cost Management** - Track LLM costs per user/tenant
- 📈 **Monitoring** - Prometheus, Grafana, OpenTelemetry
- ⚖️ **Compliance** - GDPR, HIPAA, SOC2 templates
- 🌍 **All LLM Providers** - Azure, Anthropic, Gemini, Cohere
- 💾 **All Vector DBs** - Pinecone, Qdrant, Milvus, Weaviate
- 🆘 **24/7 Support** - SLA-backed support

## Quick Start (Development)

```xml
<dependency>
    <groupId>com.ai.fabric.enterprise</groupId>
    <artifactId>ai-enterprise-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Enable multi-tenancy:
```yaml
ai:
  multi-tenancy:
    enabled: true
```

## License

- **Development/Testing**: Free (no license required)
- **Production**: Requires commercial license
- **Converts to Apache 2.0**: January 1, 2029

[Get Commercial License](https://yourcompany.com/pricing) | [Read Full License](LICENSE)
```

---

## Quality Assurance

### 1. Testing Requirements

**Minimum test coverage targets**:
- [ ] Core modules: 80%+ coverage
- [ ] Action handlers: 70%+ coverage
- [ ] RAG pipeline: 75%+ coverage
- [ ] Integration tests for all starters

**Run coverage report**:
```bash
mvn clean test jacoco:report
# Check target/site/jacoco/index.html
```

---

### 2. Static Analysis

**Tools to run**:
- [ ] SonarQube / SonarCloud
  ```bash
  mvn clean verify sonar:sonar \
    -Dsonar.projectKey=ai-fabric-framework \
    -Dsonar.host.url=https://sonarcloud.io
  ```
- [ ] SpotBugs (find bugs)
- [ ] PMD (code quality)
- [ ] Checkstyle (code style)

**Quality gates**:
- Zero critical bugs
- Zero security vulnerabilities
- Maintainability rating: A
- Code smells: < 100

---

### 3. Dependency Audit

**Check for vulnerabilities**:
```bash
mvn dependency-check:check
```

**Update vulnerable dependencies**:
- [ ] Review dependency report
- [ ] Update to latest secure versions
- [ ] Test compatibility
- [ ] Document any version pins

---

### 4. Build Verification

**Tasks**:
- [ ] Clean build succeeds
  ```bash
  mvn clean install
  ```
- [ ] All tests pass
- [ ] No compilation warnings
- [ ] Build on Java 21 (minimum supported version)
- [ ] Build on Java 23 (latest)

---

## Security & Compliance

### 1. Security Scan

**Tools**:
- [ ] OWASP Dependency Check
- [ ] Snyk
- [ ] GitHub Security Alerts (enable)
- [ ] Secret scanning (GitHub)

**Checklist**:
- [ ] No hardcoded secrets
- [ ] No SQL injection vulnerabilities
- [ ] No XSS vulnerabilities
- [ ] Secure defaults (e.g., HTTPS only)
- [ ] Input validation on all public APIs

---

### 2. License Compliance

**Community modules**:
- [ ] All files have Apache 2.0 header
- [ ] LICENSE file at repository root
- [ ] NOTICE file (if using third-party code)
- [ ] No GPL dependencies (incompatible with Apache)

**License header template**:
```java
/*
 * Copyright 2024-2026 Your Company Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

**Script to add headers**:
```bash
#!/bin/bash
# add-license-headers.sh

HEADER_FILE="license-header.txt"

find . -name "*.java" -type f | while read file; do
    if ! grep -q "Licensed under the Apache License" "$file"; then
        cat "$HEADER_FILE" "$file" > "$file.new"
        mv "$file.new" "$file"
        echo "Added header to: $file"
    fi
done
```

---

**Enterprise modules**:
- [ ] All files have BSL 1.1 header
- [ ] LICENSE file (BSL 1.1 text)
- [ ] NOTICE.md explaining commercial use
- [ ] Change Date set (4 years from release)

---

### 3. Privacy & Data Handling

**Review**:
- [ ] No personal data logged
- [ ] PII detection working correctly
- [ ] Audit logs don't contain sensitive data
- [ ] Demo data is anonymized
- [ ] Example configurations use placeholders

---

## Release Checklist

### Pre-Release (1 week before)

**Code**:
- [ ] All cleanup tasks completed
- [ ] Test coverage meets targets
- [ ] Static analysis passing
- [ ] No critical bugs
- [ ] Documentation complete

**Legal**:
- [ ] License files correct
- [ ] All headers added
- [ ] Contributor License Agreement (CLA) ready
- [ ] Terms of Service drafted
- [ ] Privacy Policy drafted

**Infrastructure**:
- [ ] CI/CD pipeline configured
- [ ] GitHub repository ready
- [ ] Domain registered (yourproject.com)
- [ ] Email setup (support@, sales@)
- [ ] Discord/Slack community created

---

### Release Day

**GitHub**:
- [ ] Push to public repositories
- [ ] Create v1.0.0 tag
- [ ] Create GitHub release with notes
- [ ] Pin important issues/discussions

**Maven Central**:
- [ ] Publish Community artifacts
- [ ] Verify artifacts downloadable
- [ ] Test dependency resolution

**Communication**:
- [ ] Post on Hacker News
- [ ] Post on Reddit (r/java, r/SpringBoot, r/MachineLearning)
- [ ] Tweet announcement
- [ ] LinkedIn post
- [ ] Email announcement (if mailing list)

**Monitoring**:
- [ ] Watch GitHub issues
- [ ] Monitor community channels
- [ ] Track download metrics

---

### Post-Release (First week)

**Support**:
- [ ] Respond to issues within 24 hours
- [ ] Answer questions in community
- [ ] Fix critical bugs immediately
- [ ] Release patch version if needed

**Marketing**:
- [ ] Write launch blog post
- [ ] Submit to tech newsletters
- [ ] Reach out to influencers
- [ ] Post in relevant communities

**Metrics**:
- [ ] Track GitHub stars
- [ ] Track Maven downloads
- [ ] Monitor sentiment
- [ ] Collect feedback

---

## Migration Scripts

### Script 1: Move Module to Enterprise

```bash
#!/bin/bash
# move-module-to-enterprise.sh
# Usage: ./move-module-to-enterprise.sh ai-infrastructure-provider-azure

set -e

MODULE_NAME=$1

if [ -z "$MODULE_NAME" ]; then
    echo "Usage: $0 <module-name>"
    exit 1
fi

COMMUNITY_REPO="ai-fabric-framework"
ENTERPRISE_REPO="ai-fabric-enterprise"

SOURCE_PATH="$COMMUNITY_REPO/ai-infrastructure-module/$MODULE_NAME"
TARGET_PATH="$ENTERPRISE_REPO/ai-enterprise-providers/$MODULE_NAME"

echo "Moving $MODULE_NAME to enterprise repository..."

# Check if source exists
if [ ! -d "$SOURCE_PATH" ]; then
    echo "Error: Source path does not exist: $SOURCE_PATH"
    exit 1
fi

# Create target directory
mkdir -p "$ENTERPRISE_REPO/ai-enterprise-providers"

# Copy module
cp -r "$SOURCE_PATH" "$TARGET_PATH"

# Update license headers
find "$TARGET_PATH" -name "*.java" -type f | while read file; do
    sed -i 's/Licensed under the Apache License, Version 2.0/Licensed under the Business Source License 1.1/g' "$file"
done

# Remove from community repo
cd "$COMMUNITY_REPO"
git rm -r "ai-infrastructure-module/$MODULE_NAME"
git commit -m "Move $MODULE_NAME to enterprise edition"

# Add to enterprise repo
cd "../$ENTERPRISE_REPO"
git add "ai-enterprise-providers/$MODULE_NAME"
git commit -m "Add $MODULE_NAME from community edition"

echo "✅ Module moved successfully!"
echo "Next steps:"
echo "1. Update POMs (remove from community, add to enterprise)"
echo "2. Update documentation"
echo "3. Push changes to remote"
```

---

### Script 2: Add License Headers

```bash
#!/bin/bash
# add-license-headers.sh
# Usage: ./add-license-headers.sh community|enterprise

set -e

LICENSE_TYPE=$1

if [ "$LICENSE_TYPE" != "community" ] && [ "$LICENSE_TYPE" != "enterprise" ]; then
    echo "Usage: $0 community|enterprise"
    exit 1
fi

if [ "$LICENSE_TYPE" == "community" ]; then
    HEADER_FILE="license-header-apache.txt"
    REPO_PATH="ai-fabric-framework"
else
    HEADER_FILE="license-header-bsl.txt"
    REPO_PATH="ai-fabric-enterprise"
fi

echo "Adding $LICENSE_TYPE license headers..."

find "$REPO_PATH" -name "*.java" -type f | while read file; do
    # Skip if header already exists
    if grep -q "Copyright.*Your Company Inc" "$file"; then
        echo "Skipping (already has header): $file"
        continue
    fi

    # Add header
    {
        cat "$HEADER_FILE"
        echo ""
        cat "$file"
    } > "$file.new"

    mv "$file.new" "$file"
    echo "✅ Added header: $file"
done

echo "Done! Headers added to all Java files."
```

**license-header-apache.txt**:
```
/*
 * Copyright 2024-2026 Your Company Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

**license-header-bsl.txt**:
```
/*
 * Copyright 2024-2026 Your Company Inc.
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * License: Business Source License 1.1
 * Licensor: Your Company Inc.
 * Change Date: 2029-01-01
 * Change License: Apache License 2.0
 *
 * For commercial use, contact: sales@yourcompany.com
 *
 * You may use this software for development and testing purposes.
 * Production use requires a commercial license.
 */
```

---

### Script 3: Clean Test Data

```bash
#!/bin/bash
# clean-test-data.sh
# Removes hardcoded test data and replaces with placeholders

set -e

echo "Cleaning test data..."

# Replace real emails with example.com
find . -name "*.java" -type f -exec sed -i \
    's/@realcompany\.com/@example.com/g' {} \;

# Replace real URLs
find . -name "*.java" -type f -exec sed -i \
    's/https:\/\/internal\.company\.com/https:\/\/example.com/g' {} \;

# Replace company names
find . -name "*.java" -type f -exec sed -i \
    's/YourCompanyInc/ExampleCorp/g' {} \;

echo "✅ Test data cleaned!"
```

---

### Script 4: Verify No Secrets

```bash
#!/bin/bash
# verify-no-secrets.sh
# Scans for potential secrets in code

set -e

echo "Scanning for potential secrets..."

SECRET_PATTERNS=(
    "api.key\s*=\s*['\"][^'\"]+['\"]"
    "password\s*=\s*['\"][^'\"]+['\"]"
    "secret\s*=\s*['\"][^'\"]+['\"]"
    "token\s*=\s*['\"][^'\"]+['\"]"
    "sk-[a-zA-Z0-9]{48}"           # OpenAI keys
    "AIza[0-9A-Za-z\\-_]{35}"       # Google API keys
)

FOUND=0

for pattern in "${SECRET_PATTERNS[@]}"; do
    echo "Checking pattern: $pattern"

    if grep -r -E "$pattern" \
        --include="*.java" \
        --include="*.yml" \
        --include="*.yaml" \
        --include="*.properties" \
        --exclude-dir=target \
        --exclude-dir=.git .; then

        echo "❌ Found potential secret!"
        FOUND=1
    fi
done

if [ $FOUND -eq 0 ]; then
    echo "✅ No secrets found!"
    exit 0
else
    echo "❌ Secrets detected! Please review and remove."
    exit 1
fi
```

---

## Appendix: Checklist Summary

### Week 1: Cleanup
- [ ] Remove secrets & credentials
- [ ] Remove internal references
- [ ] Clean debug code
- [ ] Remove test data
- [ ] Run code formatter
- [ ] Update documentation

### Week 2: Separation
- [ ] Identify enterprise modules
- [ ] Create enterprise repository
- [ ] Move enterprise modules
- [ ] Update dependencies
- [ ] Add license headers
- [ ] Verify builds

### Week 3: Quality & Release
- [ ] Run tests (80%+ coverage)
- [ ] Static analysis (SonarQube)
- [ ] Security scan (OWASP)
- [ ] Dependency audit
- [ ] Final documentation review
- [ ] Release preparation

---

## Timeline Summary

| Week | Phase | Key Deliverables |
|------|-------|------------------|
| **Week 1** | Cleanup | Removed secrets, internal refs, debug code |
| **Week 2** | Separation | Enterprise modules moved, licenses applied |
| **Week 3** | Quality | Tests passing, docs complete, ready to release |

**Total**: 3 weeks to production-ready open source release

---

**End of Document**
