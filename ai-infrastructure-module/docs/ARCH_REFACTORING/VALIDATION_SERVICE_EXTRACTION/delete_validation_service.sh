#!/bin/bash

##############################################################################
# AI Validation Service Deletion Script
# 
# Deletes the unused AIValidationService from ai-infrastructure-core
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

# Print functions
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

##############################################################################
# PHASE 1: Pre-Deletion Verification
##############################################################################

print_header "PHASE 1: Pre-Deletion Verification"

print_info "Checking for AIValidationService usages..."

# Search for usages (excluding the service itself and tests)
USAGE_COUNT=$(grep -r "AIValidationService" ai-infrastructure-core/src --include="*.java" | \
    grep -v "validation/AIValidationService" | \
    grep -v "AIValidationServiceTest" | \
    wc -l)

if [ "$USAGE_COUNT" -gt 0 ]; then
    print_error "Found $USAGE_COUNT usage(s) of AIValidationService!"
    print_error "Manual review required before deletion."
    grep -r "AIValidationService" ai-infrastructure-core/src --include="*.java" | \
        grep -v "validation/AIValidationService" | \
        grep -v "AIValidationServiceTest"
    exit 1
fi

print_success "Zero usages found (as expected)"

print_info "Checking if files exist..."

SERVICE_FILE="ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIValidationService.java"
TEST_FILE="ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/AIValidationServiceTest.java"

if [ ! -f "$SERVICE_FILE" ]; then
    print_error "Service file not found: $SERVICE_FILE"
    exit 1
fi

if [ ! -f "$TEST_FILE" ]; then
    print_warning "Test file not found: $TEST_FILE (may have been deleted already)"
fi

print_success "Files verified"

##############################################################################
# PHASE 2: Create Backup (Optional but Recommended)
##############################################################################

print_header "PHASE 2: Create Backup"

BACKUP_DIR="/tmp/validation-service-backup-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR/main"
mkdir -p "$BACKUP_DIR/test"

print_info "Creating backup in: $BACKUP_DIR"

cp "$SERVICE_FILE" "$BACKUP_DIR/main/" 2>/dev/null || true
cp "$TEST_FILE" "$BACKUP_DIR/test/" 2>/dev/null || true

print_success "Backup created"

##############################################################################
# PHASE 3: Delete Files
##############################################################################

print_header "PHASE 3: Delete Files"

print_info "Deleting AIValidationService.java..."
rm -f "$SERVICE_FILE"
print_success "Service deleted"

if [ -f "$TEST_FILE" ]; then
    print_info "Deleting AIValidationServiceTest.java..."
    rm -f "$TEST_FILE"
    print_success "Test deleted"
fi

# Remove empty directories
print_info "Cleaning up empty directories..."

MAIN_VAL_DIR="ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation"
TEST_VAL_DIR="ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation"

if [ -d "$MAIN_VAL_DIR" ] && [ -z "$(ls -A $MAIN_VAL_DIR)" ]; then
    rmdir "$MAIN_VAL_DIR"
    print_success "Removed empty directory: $MAIN_VAL_DIR"
fi

if [ -d "$TEST_VAL_DIR" ] && [ -z "$(ls -A $TEST_VAL_DIR)" ]; then
    rmdir "$TEST_VAL_DIR"
    print_success "Removed empty directory: $TEST_VAL_DIR"
fi

print_success "Files deleted successfully"

##############################################################################
# PHASE 4: Verify Build
##############################################################################

print_header "PHASE 4: Verify Build"

print_info "Running Maven clean compile..."

if mvn clean compile -q -DskipTests; then
    print_success "Build successful"
else
    print_error "Build failed! Check errors above."
    print_warning "Backup available at: $BACKUP_DIR"
    exit 1
fi

##############################################################################
# PHASE 5: Run Tests
##############################################################################

print_header "PHASE 5: Run Tests"

print_info "Running tests for ai-infrastructure-core..."

cd ai-infrastructure-core

if mvn test -q; then
    print_success "All tests passed"
else
    print_error "Tests failed! Check errors above."
    print_warning "Backup available at: $BACKUP_DIR"
    exit 1
fi

cd ..

##############################################################################
# PHASE 6: Update Documentation
##############################################################################

print_header "PHASE 6: Update Documentation"

print_info "Creating deletion record..."

DELETION_RECORD="docs/ARCH_REFACTORING/VALIDATION_SERVICE_EXTRACTION/DELETION_COMPLETE.md"

cat > "$DELETION_RECORD" << 'EOF'
# AI Validation Service - Deletion Complete ✅

## Status: DELETED

**Date**: $(date +"%B %d, %Y at %H:%M:%S")  
**Rationale**: Unused service with opinionated business logic  
**Impact**: ZERO (no usages found)  
**Decision**: DELETE (Option 1)  

---

## What Was Deleted

### Files Removed:
1. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIValidationService.java` (786 lines)
2. `ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/AIValidationServiceTest.java`

### Directories Removed:
- `ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/` (empty)
- `ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/` (empty)

**Total Impact**: ~900 lines removed from core module

---

## Why Deleted?

1. ✅ **Service was completely unused** (verified via grep)
2. ❌ **Opinionated business validation logic** (hardcoded rules)
3. ❌ **Application-level concerns** (not infrastructure)
4. ❌ **Hardcoded suspect values**: "n/a", "na", "unknown", etc.
5. ❌ **Fixed scoring weights**: ERROR = -0.2, WARNING = -0.1
6. ❌ **Business rule validation** (application layer, not infrastructure)

---

## Verification

### Build Status:
- ✅ Maven clean compile: SUCCESS
- ✅ Maven test: SUCCESS
- ✅ Zero compilation errors
- ✅ All tests pass

### Usage Verification:
- ✅ Zero production usages found
- ✅ Zero controller dependencies
- ✅ Zero service dependencies

---

## Recovery Instructions

If the service is needed in the future:

\`\`\`bash
# Find the deletion commit
git log --all --full-history -- "**/AIValidationService.java"

# View the file at a specific commit
git show <commit-hash>:ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIValidationService.java

# Restore the file
git checkout <commit-hash> -- ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIValidationService.java
git checkout <commit-hash> -- ai-infrastructure-core/src/test/java/com/ai/infrastructure/validation/AIValidationServiceTest.java
\`\`\`

---

## Alternatives for Validation

For validation needs, use:

### 1. Spring Validation
\`\`\`java
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
public class MyController {
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody MyDto dto) {
        // Spring handles validation
    }
}
\`\`\`

### 2. Hibernate Validator
\`\`\`xml
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
</dependency>
\`\`\`

### 3. Custom Application-Level Validation
Create validation services in your application layer (not infrastructure)

---

## Benefits Achieved

### Core Module:
- ✅ **Files reduced**: -2 files
- ✅ **Lines reduced**: ~900 lines
- ✅ **Complexity**: Reduced
- ✅ **Maintenance**: Reduced

### Project:
- ✅ **Cleaner architecture**: No opinionated business logic in infrastructure
- ✅ **Zero breaking changes**: Service was unused
- ✅ **Zero maintenance burden**: No module to maintain

---

## Timeline

- **Analysis**: 2 hours (comprehensive)
- **Documentation**: 6 documents created
- **Decision**: DELETE (Option 1 chosen)
- **Execution**: ~5 minutes (automated)
- **Total**: Completed on November 25, 2025

---

## References

- **Analysis**: `VALIDATION_SERVICE_ANALYSIS.md`
- **Usage Verification**: `USAGE_ANALYSIS.md`
- **Decision Comparison**: `DECISION_COMPARISON.md`
- **Execution Plan**: `EXECUTION_PLAN_OPTION1_DELETE.md`

---

**Status**: ✅ COMPLETE  
**Result**: SUCCESS  
**Impact**: Positive (cleaner core module)  
**Maintainer**: AI Infrastructure Team  
EOF

print_success "Documentation updated"

##############################################################################
# PHASE 7: Summary
##############################################################################

print_header "DELETION COMPLETE ✅"

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                                            ║${NC}"
echo -e "${GREEN}║  ✅  AI Validation Service Successfully Deleted                            ║${NC}"
echo -e "${GREEN}║                                                                            ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
echo ""

print_info "Summary:"
echo "  • Files deleted: 2 (service + test)"
echo "  • Lines removed: ~900 lines"
echo "  • Build status: ✅ SUCCESS"
echo "  • Test status: ✅ ALL PASSED"
echo "  • Breaking changes: ✅ NONE"
echo ""

print_info "Backup location:"
echo "  $BACKUP_DIR"
echo ""

print_info "Deletion record:"
echo "  $DELETION_RECORD"
echo ""

print_success "Core module is now cleaner! 🎉"
echo ""

print_header "NEXT STEPS"

echo "1. Review changes:"
echo "   git status"
echo ""
echo "2. Commit changes:"
echo "   git add -A"
echo "   git commit -m \"refactor(core): remove unused AIValidationService"
echo ""
echo "      - Deleted AIValidationService.java (786 lines)"
echo "      - Deleted AIValidationServiceTest.java"
echo "      - Service was completely unused (zero production usages)"
echo "      - Opinionated implementation not suitable for infrastructure"
echo "      - Can be recovered from git history if needed"
echo ""
echo "      BREAKING CHANGE: None (service was unused)\""
echo ""
echo "3. Update CHANGE_REQUESTS_LOG.md:"
echo "   Mark Request #4 as COMPLETED ✅"
echo ""

print_success "Script completed successfully!"
