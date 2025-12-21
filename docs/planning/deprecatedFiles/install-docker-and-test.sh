#!/bin/bash
# Install Docker and Run Behavioral Integration Tests
# For Ubuntu 24.04 LTS

set -e

echo "=========================================="
echo "Docker Installation and Test Setup Script"
echo "=========================================="
echo ""

# Check if running as root or with sudo
if [ "$EUID" -ne 0 ]; then 
    echo "❌ This script needs to be run with sudo"
    echo "Usage: sudo bash install-docker-and-test.sh"
    exit 1
fi

echo "✅ Running with appropriate permissions"
echo ""

# Step 1: Install Docker
echo "📦 Step 1: Installing Docker Engine..."
echo ""

# Update package index
apt-get update -y

# Install prerequisites
apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "✅ Docker Engine installed"
echo ""

# Step 2: Start Docker service
echo "🚀 Step 2: Starting Docker service..."
systemctl start docker
systemctl enable docker

# Verify Docker is running
if systemctl is-active --quiet docker; then
    echo "✅ Docker service is running"
else
    echo "❌ Docker service failed to start"
    exit 1
fi

echo ""

# Step 3: Verify Docker installation
echo "🔍 Step 3: Verifying Docker installation..."
docker --version
docker ps

echo ""
echo "✅ Docker is working correctly"
echo ""

# Step 4: Add current user to docker group (if not root)
if [ -n "$SUDO_USER" ]; then
    echo "👤 Step 4: Adding user '$SUDO_USER' to docker group..."
    usermod -aG docker "$SUDO_USER"
    echo "✅ User added to docker group"
    echo "⚠️  Note: User needs to log out and back in for group changes to take effect"
    echo "    Or run: newgrp docker"
else
    echo "⚠️  Step 4: Skipping user group setup (running as root)"
fi

echo ""

# Step 5: Test Docker with hello-world
echo "🐳 Step 5: Testing Docker with hello-world..."
docker run --rm hello-world

echo ""
echo "✅ Docker test successful"
echo ""

# Step 6: Pull postgres image for tests
echo "🐘 Step 6: Pre-pulling PostgreSQL image for tests..."
docker pull postgres:15-alpine

echo ""
echo "✅ PostgreSQL image ready"
echo ""

# Summary
echo "=========================================="
echo "✅ Installation Complete!"
echo "=========================================="
echo ""
echo "Docker Version: $(docker --version)"
echo "Docker Socket: /var/run/docker.sock"
echo "PostgreSQL Image: postgres:15-alpine (ready)"
echo ""
echo "📋 Next Steps:"
echo ""
echo "1. If you're not root, log out and back in (or run: newgrp docker)"
echo ""
echo "2. Run a single behavioral test:"
echo "   cd /workspace/ai-infrastructure-module"
echo "   mvn test -Dtest=PatternAnalyzerInsightsIntegrationTest -pl integration-tests"
echo ""
echo "3. Run all behavioral tests:"
echo "   mvn test -Dtest=RealAPIProviderBehaviourMatrixIntegrationTest -pl integration-tests"
echo ""
echo "4. Check test results:"
echo "   cat ai-infrastructure-module/integration-Testing/integration-tests/target/surefire-reports/*.txt"
echo ""
echo "=========================================="
echo "Happy Testing! 🎉"
echo "=========================================="
