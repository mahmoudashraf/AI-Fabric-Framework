# ==============================================================================
# Relationship Query Integration Test Runner (PowerShell)
# ==============================================================================
# This script runs a specific integration test with configurable parameters
# Usage: .\run-single-test.ps1
# ==============================================================================

# ==================== CONFIGURATION (Edit these) ====================

# Test Configuration
$TEST_CLASS = "OrchestratorAccessPolicyRealApiIntegrationTest"  # Change this to your test class name
$OPENAI_KEY = "sk-proj-YOUR-API-KEY-HERE"                       # Change this to your OpenAI API key

# Provider Configuration
$LLM_PROVIDER = "openai"
$EMBEDDING_PROVIDER = "onnx"
$VECTOR_DB = "lucene"
$STORAGE_STRATEGY = "SINGLE_TABLE"

# Maven Configuration
$MAVEN_PROFILE = "realapi"
$FORK_COUNT = "1"
$REUSE_FORKS = "false"
$LOG_LEVEL = "WARN"

# Maven Home (auto-detect or set manually)
$MAVEN_HOME = "C:\Users\$env:USERNAME\Tools\apache-maven-3.9.6"

# ==================== END CONFIGURATION ====================

# Colors for output
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

# Header
Write-ColorOutput "`n═══════════════════════════════════════════════════════════════" "Cyan"
Write-ColorOutput " Relationship Query Integration Test Runner" "Cyan"
Write-ColorOutput "═══════════════════════════════════════════════════════════════`n" "Cyan"

# Validate Maven
if (-not (Test-Path "$MAVEN_HOME\bin\mvn.cmd")) {
    Write-ColorOutput "❌ Maven not found at: $MAVEN_HOME" "Red"
    Write-ColorOutput "   Please install Maven or update MAVEN_HOME in the script" "Yellow"
    exit 1
}

# Set Maven in PATH
$env:Path = "$MAVEN_HOME\bin;" + $env:Path

# Display Configuration
Write-ColorOutput "📋 Test Configuration:" "Cyan"
Write-ColorOutput "   Test Class: $TEST_CLASS" "White"
Write-ColorOutput "   Maven Profile: $MAVEN_PROFILE" "White"
Write-ColorOutput "   LLM Provider: $LLM_PROVIDER" "White"
Write-ColorOutput "   Embedding: $EMBEDDING_PROVIDER" "White"
Write-ColorOutput "   Vector DB: $VECTOR_DB" "White"
Write-ColorOutput "   Storage: $STORAGE_STRATEGY`n" "White"

# Set environment variables
$env:OPENAI_API_KEY = $OPENAI_KEY

# Navigate to test directory
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $SCRIPT_DIR

Write-ColorOutput "📁 Working Directory: $(Get-Location)`n" "Cyan"

# Build Maven command
$MAVEN_CMD = @(
    "test",
    "-Dtest=$TEST_CLASS",
    "-Dspring.profiles.active=$MAVEN_PROFILE",
    "-Dai.providers.llm-provider=$LLM_PROVIDER",
    "-Dai.providers.embedding-provider=$EMBEDDING_PROVIDER",
    "-Dai.vector-db.type=$VECTOR_DB",
    "-Dai-infrastructure.storage.strategy=$STORAGE_STRATEGY",
    "-DforkCount=$FORK_COUNT",
    "-DreuseForks=$REUSE_FORKS",
    "-Dlogging.level.root=$LOG_LEVEL"
)

Write-ColorOutput "🚀 Executing Maven Test..." "Green"
Write-ColorOutput "   Command: mvn $($MAVEN_CMD -join ' ')`n" "Gray"

# Run Maven test
$START_TIME = Get-Date

try {
    & mvn $MAVEN_CMD
    $EXIT_CODE = $LASTEXITCODE
    
    $END_TIME = Get-Date
    $DURATION = ($END_TIME - $START_TIME).TotalSeconds
    
    Write-ColorOutput "`n═══════════════════════════════════════════════════════════════" "Cyan"
    
    if ($EXIT_CODE -eq 0) {
        Write-ColorOutput "✅ Tests PASSED!" "Green"
        Write-ColorOutput "   Duration: $([math]::Round($DURATION, 2))s" "White"
    } else {
        Write-ColorOutput "❌ Tests FAILED!" "Red"
        Write-ColorOutput "   Duration: $([math]::Round($DURATION, 2))s" "White"
        Write-ColorOutput "   Check logs above for details" "Yellow"
    }
    
    Write-ColorOutput "═══════════════════════════════════════════════════════════════`n" "Cyan"
    
    exit $EXIT_CODE
    
} catch {
    Write-ColorOutput "`n❌ Error executing Maven: $_" "Red"
    exit 1
}

