# AI Fabric Framework: Single-Tenant Cloud Deployment Technical Guide

## Overview

This guide provides step-by-step instructions to convert the AI Fabric Framework into a cloud-based managed service using the **Single-Tenant Architecture** (Option B).

**Architecture**: One isolated instance per customer
**Timeline**: 4-6 weeks to first paying customer
**Complexity**: Low to Medium

---

## Part 1: Architecture Overview

### 1.1 Single-Tenant Deployment Model

```
                          ┌─────────────────────────────────────┐
                          │         Control Plane               │
                          │  (Your Admin Dashboard/API)         │
                          │                                     │
                          │  ┌─────────────────────────────┐    │
                          │  │  Tenant Registry            │    │
                          │  │  (PostgreSQL/DynamoDB)      │    │
                          │  │                             │    │
                          │  │  - tenant_id                │    │
                          │  │  - customer_name            │    │
                          │  │  - plan_tier                │    │
                          │  │  - instance_url             │    │
                          │  │  - api_key_ref (Secrets)    │    │
                          │  │  - status                   │    │
                          │  │  - created_at               │    │
                          │  └─────────────────────────────┘    │
                          └──────────────┬──────────────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    │                    │                    │
                    ▼                    ▼                    ▼
        ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
        │   Customer A      │ │   Customer B      │ │   Customer C      │
        │   Instance        │ │   Instance        │ │   Instance        │
        │                   │ │                   │ │                   │
        │ ┌───────────────┐ │ │ ┌───────────────┐ │ │ ┌───────────────┐ │
        │ │  AI Fabric    │ │ │ │  AI Fabric    │ │ │ │  AI Fabric    │ │
        │ │  Spring Boot  │ │ │ │  Spring Boot  │ │ │ │  Spring Boot  │ │
        │ │  Application  │ │ │ │  Application  │ │ │ │  Application  │ │
        │ └───────┬───────┘ │ │ └───────┬───────┘ │ │ └───────┬───────┘ │
        │         │         │ │         │         │ │         │         │
        │ ┌───────▼───────┐ │ │ ┌───────▼───────┐ │ │ ┌───────▼───────┐ │
        │ │  PostgreSQL   │ │ │ │  PostgreSQL   │ │ │ │  PostgreSQL   │ │
        │ │  (RDS)        │ │ │ │  (RDS)        │ │ │ │  (RDS)        │ │
        │ └───────────────┘ │ │ └───────────────┘ │ │ └───────────────┘ │
        └─────────┬─────────┘ └─────────┬─────────┘ └─────────┬─────────┘
                  │                     │                     │
                  └─────────────────────┼─────────────────────┘
                                        │
                                        ▼
                          ┌─────────────────────────────────────┐
                          │    Shared Vector Database           │
                          │    (Qdrant Cloud / Pinecone)        │
                          │                                     │
                          │    Namespace Isolation:             │
                          │    - customer_a_products            │
                          │    - customer_a_orders              │
                          │    - customer_b_products            │
                          │    - customer_c_products            │
                          └─────────────────────────────────────┘
```

### 1.2 Component Responsibilities

| Component | Purpose | Technology |
|-----------|---------|------------|
| **Control Plane** | Tenant management, provisioning, billing | Your custom service |
| **AI Fabric Instance** | Customer's AI capabilities | Spring Boot (this framework) |
| **PostgreSQL** | Customer's relational data | AWS RDS / Cloud SQL |
| **Vector Database** | Semantic search vectors | Qdrant Cloud (namespace per customer) |
| **Secrets Manager** | API keys, credentials | AWS Secrets Manager |
| **Load Balancer** | HTTPS termination, routing | AWS ALB / Cloudflare |

---

## Part 2: Required Connectors & Integrations

### 2.1 External Service Connections (From Code Analysis)

The framework connects to these external services. Each customer needs their own credentials:

#### LLM Providers (Choose One)

| Provider | Configuration Keys | Endpoint |
|----------|-------------------|----------|
| **OpenAI** | `ai.providers.openai.api-key` | `https://api.openai.com/v1` |
| **Azure OpenAI** | `ai.providers.azure.api-key`, `ai.providers.azure.endpoint` | Customer's Azure endpoint |
| **Anthropic** | `ai.providers.anthropic.api-key` | `https://api.anthropic.com/v1` |
| **Cohere** | `ai.providers.cohere.api-key` | `https://api.cohere.ai/v1` |
| **Google Gemini** | `ai.providers.gemini.api-key` | `https://generativelanguage.googleapis.com/v1beta` |

#### Embedding Providers (Choose One)

| Provider | Configuration Keys | Cost |
|----------|-------------------|------|
| **ONNX (Recommended)** | `ai.providers.onnx.model-path` | $0 (local) |
| **OpenAI** | `ai.providers.openai.api-key`, `ai.providers.openai.embedding-model` | API cost |
| **Cohere** | `ai.providers.cohere.api-key` | API cost |
| **Gemini** | `ai.providers.gemini.api-key` | API cost |

#### Vector Databases (Choose One)

| Database | Configuration Keys | Best For |
|----------|-------------------|----------|
| **Qdrant Cloud** | `ai.vector-databases.qdrant.host`, `grpc-port`, `api-key` | Recommended for cloud |
| **Pinecone** | `ai.vector-databases.pinecone.api-key`, `index-name`, `environment` | Serverless option |
| **Milvus** | `ai.vector-databases.milvus.host`, `port`, `username`, `password` | Self-hosted option |

### 2.2 Database Connection

```yaml
# PostgreSQL (Required)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

### 2.3 Complete Environment Variables

```bash
# Database (Required)
DATABASE_URL=jdbc:postgresql://host:5432/dbname
DB_USERNAME=postgres
DB_PASSWORD=secret

# LLM Provider (Choose One)
OPENAI_API_KEY=sk-...
# OR
ANTHROPIC_API_KEY=sk-ant-...
# OR
AZURE_API_KEY=...
AZURE_ENDPOINT=https://your-resource.openai.azure.com

# Embedding Provider (Choose One)
# ONNX needs no API key (local)
# OR use same key as LLM provider

# Vector Database (Choose One)
QDRANT_HOST=your-cluster.cloud.qdrant.io
QDRANT_PORT=6334
QDRANT_API_KEY=your-api-key
# OR
PINECONE_API_KEY=your-key
PINECONE_ENVIRONMENT=us-west4-gcp
PINECONE_INDEX=your-index

# Security
PII_ENCRYPTION_SECRET=32-char-secret-key-here

# Application
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

---

## Part 3: Cloud Deployment Configuration

### 3.1 Production Application Configuration

Create `application-cloud.yml`:

```yaml
# AI Fabric Cloud Configuration
# Profile: cloud

server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /

spring:
  application:
    name: ai-fabric-${TENANT_ID:default}

  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
      minimum-idle: ${DB_POOL_MIN:5}
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway for migrations
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true

  flyway:
    enabled: true
    baseline-on-migrate: true

# AI Configuration
ai:
  enabled: true
  tenant-id: ${TENANT_ID:default}

  # Provider Selection
  providers:
    llm-provider: ${LLM_PROVIDER:openai}
    embedding-provider: ${EMBEDDING_PROVIDER:onnx}

    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:gpt-4o-mini}
      embedding-model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
      timeout: 60
      max-tokens: 4096

    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      model: ${ANTHROPIC_MODEL:claude-3-haiku-20240307}
      timeout: 60
      max-tokens: 4096

    onnx:
      model-path: classpath:models/all-MiniLM-L6-v2.onnx
      tokenizer-path: classpath:models/tokenizer.json
      max-sequence-length: 512

  # Vector Database
  vector-db:
    type: ${VECTOR_DB_TYPE:qdrant}

  vector-databases:
    qdrant:
      enabled: ${QDRANT_ENABLED:true}
      host: ${QDRANT_HOST:localhost}
      grpc-port: ${QDRANT_PORT:6334}
      api-key: ${QDRANT_API_KEY:}
      timeout: 30
      collection-prefix: ${TENANT_ID:default}_

    pinecone:
      enabled: ${PINECONE_ENABLED:false}
      api-key: ${PINECONE_API_KEY:}
      index-name: ${PINECONE_INDEX:}
      environment: ${PINECONE_ENVIRONMENT:}
      project-id: ${PINECONE_PROJECT_ID:}

  # Orchestration
  orchestration:
    enabled: true
    information-mode: DETERMINISTIC_RAG_GENERATE
    action-mode: EXECUTE_WITH_CONFIRMATION
    max-concurrent-requests: 50
    request-timeout-ms: 30000

  # Security
  security:
    enabled: true
    rate-limit:
      enabled: true
      max-requests-per-minute: ${RATE_LIMIT:100}

  # PII Detection
  pii-detection:
    enabled: true
    mode: REDACT
    detection-direction: INPUT_OUTPUT
    store-encrypted-original: true
    encryption-secret: ${PII_ENCRYPTION_SECRET}
    audit-logging-enabled: true

  # Response Sanitization
  response-sanitizer:
    enabled: true
    risk-level: MEDIUM

  # Chat Sessions
  chat:
    enabled: true
    window-size: 12
    max-context-chars: 8000

  # Data Migration
  migration:
    enabled: true
    batch-size: 100
    rate-limit: 50

  # Web API
  web:
    enabled: true
    base-path: /api/ai

# Actuator for Health Checks
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
  health:
    db:
      enabled: true

# Logging
logging:
  level:
    com.ai.infrastructure: INFO
    org.springframework: WARN
    org.hibernate: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### 3.2 Tenant-Specific Entity Configuration

Create `ai-entity-config.yml`:

```yaml
# AI Entity Configuration
# Each customer defines their own entities

ai-entities:
  # Example: Product entity
  product:
    entity-type: product
    description: "Product catalog items"
    searchable-fields:
      - name: name
        weight: 1.0
        boost: 2.0
      - name: description
        weight: 0.8
      - name: category
        weight: 0.6
    embeddable-fields:
      - name: description
        auto-generate: true
    metadata-fields:
      - name: sku
      - name: price
      - name: stock
    indexing-strategy: ASYNC

  # Example: Order entity
  order:
    entity-type: order
    description: "Customer orders"
    searchable-fields:
      - name: orderNumber
        weight: 1.0
      - name: status
        weight: 0.5
    metadata-fields:
      - name: customerId
      - name: totalAmount
      - name: createdAt
    indexing-strategy: SYNC
```

---

## Part 4: Docker Configuration

### 4.1 Multi-Stage Dockerfile

```dockerfile
# AI Fabric Framework - Cloud Deployment
# Multi-stage build for minimal image size

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom files first for dependency caching
COPY pom.xml .
COPY ai-infrastructure-module/pom.xml ai-infrastructure-module/
COPY ai-provider-module/pom.xml ai-provider-module/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY ai-infrastructure-module ai-infrastructure-module
COPY ai-provider-module ai-provider-module

# Build application
RUN mvn clean package -DskipTests -pl ai-infrastructure-module/ai-infrastructure-web -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Security: Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/ai-infrastructure-module/ai-infrastructure-web/target/*.jar app.jar

# Copy ONNX model for local embeddings
COPY --from=builder /app/ai-infrastructure-module/providers/ai-infrastructure-onnx-starter/src/main/resources/models /app/models

# Set ownership
RUN chown -R spring:spring /app

USER spring:spring

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 4.2 Docker Compose for Local Testing

```yaml
# docker-compose.yml
# Local development and testing environment

version: '3.8'

services:
  # AI Fabric Application
  ai-fabric:
    build: .
    container_name: ai-fabric
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=cloud
      - TENANT_ID=local-dev
      - DATABASE_URL=jdbc:postgresql://postgres:5432/aifabric
      - DB_USERNAME=postgres
      - DB_PASSWORD=postgres
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QDRANT_HOST=qdrant
      - QDRANT_PORT=6334
      - PII_ENCRYPTION_SECRET=dev-secret-key-32-chars-long!!
    depends_on:
      postgres:
        condition: service_healthy
      qdrant:
        condition: service_healthy
    networks:
      - ai-fabric-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  # PostgreSQL Database
  postgres:
    image: postgres:16-alpine
    container_name: ai-fabric-postgres
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
      - POSTGRES_DB=aifabric
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - ai-fabric-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Qdrant Vector Database
  qdrant:
    image: qdrant/qdrant:v1.12.1
    container_name: ai-fabric-qdrant
    ports:
      - "6333:6333"  # REST API
      - "6334:6334"  # gRPC
    volumes:
      - qdrant_data:/qdrant/storage
    networks:
      - ai-fabric-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:6333/"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  qdrant_data:

networks:
  ai-fabric-network:
    driver: bridge
```

---

## Part 5: AWS Infrastructure (Terraform)

### 5.1 Main Infrastructure

```hcl
# main.tf
# AI Fabric Single-Tenant AWS Infrastructure

terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Variables
variable "aws_region" {
  default = "us-east-1"
}

variable "tenant_id" {
  description = "Unique tenant identifier"
  type        = string
}

variable "environment" {
  default = "production"
}

variable "instance_size" {
  default = "small"  # small, medium, large
}

# Local values
locals {
  name_prefix = "ai-fabric-${var.tenant_id}"

  instance_configs = {
    small  = { cpu = 512,  memory = 1024, db_instance = "db.t4g.micro" }
    medium = { cpu = 1024, memory = 2048, db_instance = "db.t4g.small" }
    large  = { cpu = 2048, memory = 4096, db_instance = "db.t4g.medium" }
  }

  config = local.instance_configs[var.instance_size]
}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name      = "${local.name_prefix}-vpc"
    Tenant    = var.tenant_id
    ManagedBy = "terraform"
  }
}

# Subnets
resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 1}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name   = "${local.name_prefix}-private-${count.index}"
    Tenant = var.tenant_id
  }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index + 10}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name   = "${local.name_prefix}-public-${count.index}"
    Tenant = var.tenant_id
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name   = "${local.name_prefix}-igw"
    Tenant = var.tenant_id
  }
}

# NAT Gateway
resource "aws_eip" "nat" {
  domain = "vpc"
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name   = "${local.name_prefix}-nat"
    Tenant = var.tenant_id
  }
}

# Route Tables
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = {
    Name   = "${local.name_prefix}-private-rt"
    Tenant = var.tenant_id
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name   = "${local.name_prefix}-public-rt"
    Tenant = var.tenant_id
  }
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}
```

### 5.2 Database (RDS)

```hcl
# rds.tf
# PostgreSQL Database for tenant

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnet"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name   = "${local.name_prefix}-db-subnet"
    Tenant = var.tenant_id
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Security group for RDS"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  tags = {
    Name   = "${local.name_prefix}-rds-sg"
    Tenant = var.tenant_id
  }
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-db"

  engine         = "postgres"
  engine_version = "16.1"
  instance_class = local.config.db_instance

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "aifabric"
  username = "aifabric"
  password = random_password.db_password.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  skip_final_snapshot = var.environment != "production"
  deletion_protection = var.environment == "production"

  performance_insights_enabled = true

  tags = {
    Name   = "${local.name_prefix}-db"
    Tenant = var.tenant_id
  }
}

# Store DB credentials in Secrets Manager
resource "aws_secretsmanager_secret" "db_credentials" {
  name = "${local.name_prefix}/db-credentials"

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = aws_db_instance.main.username
    password = random_password.db_password.result
    host     = aws_db_instance.main.address
    port     = aws_db_instance.main.port
    database = aws_db_instance.main.db_name
    url      = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}"
  })
}
```

### 5.3 ECS Fargate Service

```hcl
# ecs.tf
# ECS Fargate for AI Fabric application

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_security_group" "ecs_tasks" {
  name        = "${local.name_prefix}-ecs-sg"
  description = "Security group for ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name   = "${local.name_prefix}-ecs-sg"
    Tenant = var.tenant_id
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "${local.name_prefix}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_secrets" {
  name = "${local.name_prefix}-secrets-policy"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue"
      ]
      Resource = [
        aws_secretsmanager_secret.db_credentials.arn,
        aws_secretsmanager_secret.app_secrets.arn
      ]
    }]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "${local.name_prefix}-ecs-task"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })
}

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = 30

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name_prefix}-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = local.config.cpu
  memory                   = local.config.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = "ai-fabric"
    image = var.container_image

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "cloud" },
      { name = "TENANT_ID", value = var.tenant_id },
      { name = "QDRANT_HOST", value = var.qdrant_host },
      { name = "QDRANT_PORT", value = "6334" },
      { name = "JAVA_OPTS", value = "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" }
    ]

    secrets = [
      {
        name      = "DATABASE_URL"
        valueFrom = "${aws_secretsmanager_secret.db_credentials.arn}:url::"
      },
      {
        name      = "DB_USERNAME"
        valueFrom = "${aws_secretsmanager_secret.db_credentials.arn}:username::"
      },
      {
        name      = "DB_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.db_credentials.arn}:password::"
      },
      {
        name      = "OPENAI_API_KEY"
        valueFrom = "${aws_secretsmanager_secret.app_secrets.arn}:openai_api_key::"
      },
      {
        name      = "QDRANT_API_KEY"
        valueFrom = "${aws_secretsmanager_secret.app_secrets.arn}:qdrant_api_key::"
      },
      {
        name      = "PII_ENCRYPTION_SECRET"
        valueFrom = "${aws_secretsmanager_secret.app_secrets.arn}:pii_encryption_secret::"
      }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ai-fabric"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget --spider -q http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_ecs_service" "app" {
  name            = "${local.name_prefix}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "ai-fabric"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.https]

  tags = {
    Tenant = var.tenant_id
  }
}
```

### 5.4 Application Load Balancer

```hcl
# alb.tf
# Application Load Balancer

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Security group for ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name   = "${local.name_prefix}-alb-sg"
    Tenant = var.tenant_id
  }
}

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_lb_target_group" "app" {
  name        = "${local.name_prefix}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 10
    unhealthy_threshold = 3
  }

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}
```

### 5.5 Secrets Management

```hcl
# secrets.tf
# Application secrets

variable "openai_api_key" {
  description = "Customer's OpenAI API key"
  type        = string
  sensitive   = true
}

variable "qdrant_api_key" {
  description = "Qdrant Cloud API key"
  type        = string
  sensitive   = true
}

resource "random_password" "pii_encryption" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "app_secrets" {
  name = "${local.name_prefix}/app-secrets"

  tags = {
    Tenant = var.tenant_id
  }
}

resource "aws_secretsmanager_secret_version" "app_secrets" {
  secret_id = aws_secretsmanager_secret.app_secrets.id
  secret_string = jsonencode({
    openai_api_key        = var.openai_api_key
    qdrant_api_key        = var.qdrant_api_key
    pii_encryption_secret = random_password.pii_encryption.result
  })
}
```

### 5.6 Outputs

```hcl
# outputs.tf
# Terraform outputs

output "tenant_id" {
  value = var.tenant_id
}

output "api_endpoint" {
  value = "https://${aws_lb.main.dns_name}"
}

output "database_endpoint" {
  value     = aws_db_instance.main.address
  sensitive = true
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.ecs.name
}
```

---

## Part 6: Provisioning Script

### 6.1 Tenant Provisioning Script

```bash
#!/bin/bash
# provision-tenant.sh
# Provisions a new AI Fabric tenant

set -e

# Configuration
TENANT_ID="$1"
CUSTOMER_NAME="$2"
PLAN_TIER="${3:-small}"
OPENAI_API_KEY="$4"

if [ -z "$TENANT_ID" ] || [ -z "$CUSTOMER_NAME" ] || [ -z "$OPENAI_API_KEY" ]; then
    echo "Usage: ./provision-tenant.sh <tenant_id> <customer_name> <plan_tier> <openai_api_key>"
    exit 1
fi

echo "Provisioning tenant: $TENANT_ID"
echo "Customer: $CUSTOMER_NAME"
echo "Plan: $PLAN_TIER"

# Create Qdrant namespace (if using Qdrant Cloud)
echo "Creating Qdrant namespace..."
# Qdrant uses collection prefixes for namespace isolation

# Deploy with Terraform
echo "Deploying infrastructure..."
cd terraform

terraform init

terraform apply \
    -var="tenant_id=${TENANT_ID}" \
    -var="instance_size=${PLAN_TIER}" \
    -var="openai_api_key=${OPENAI_API_KEY}" \
    -var="qdrant_api_key=${QDRANT_CLOUD_API_KEY}" \
    -var="qdrant_host=${QDRANT_CLOUD_HOST}" \
    -var="certificate_arn=${ACM_CERTIFICATE_ARN}" \
    -var="container_image=${ECR_REPOSITORY_URL}:latest" \
    -auto-approve

# Get outputs
API_ENDPOINT=$(terraform output -raw api_endpoint)

echo ""
echo "=========================================="
echo "Tenant provisioned successfully!"
echo "=========================================="
echo "Tenant ID: $TENANT_ID"
echo "API Endpoint: $API_ENDPOINT"
echo "=========================================="

# Register tenant in control plane database
echo "Registering tenant in control plane..."
psql "$CONTROL_PLANE_DB_URL" << EOF
INSERT INTO tenants (
    tenant_id,
    customer_name,
    plan_tier,
    api_endpoint,
    status,
    created_at
) VALUES (
    '${TENANT_ID}',
    '${CUSTOMER_NAME}',
    '${PLAN_TIER}',
    '${API_ENDPOINT}',
    'active',
    NOW()
);
EOF

echo "Done!"
```

---

## Part 7: API Endpoints Reference

Once deployed, each tenant instance exposes these endpoints:

### 7.1 Core AI Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ai/advanced-rag/search` | POST | Semantic search with RAG |
| `/api/ai/advanced-rag/health` | GET | Health check |
| `/api/ai/security/analyze` | POST | Security threat analysis |
| `/api/ai/compliance/check` | POST | Compliance validation |
| `/api/ai/profiles` | CRUD | AI user profiles |

### 7.2 Migration Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ai/migration/start` | POST | Start data migration |
| `/api/ai/migration/jobs` | GET | List migration jobs |
| `/api/ai/migration/jobs/{id}` | GET | Get job progress |
| `/api/ai/migration/jobs/{id}/pause` | POST | Pause job |
| `/api/ai/migration/jobs/{id}/resume` | POST | Resume job |

### 7.3 Behavior Analytics

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/behavior/analytics/rapid-decline` | GET | Churn risk alerts |
| `/api/behavior/analytics/trend-distribution` | GET | User trends |
| `/api/behavior/processing/users/{userId}` | POST | Analyze user |

### 7.4 Health & Monitoring

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Application health |
| `/actuator/info` | GET | Application info |
| `/actuator/metrics` | GET | Metrics |
| `/actuator/prometheus` | GET | Prometheus metrics |

---

## Part 8: Customer Onboarding Flow

### 8.1 Onboarding Steps

```
1. Customer Signs Up
   ├─ Collect: Email, Company Name, Use Case
   └─ Select Plan: Starter ($149), Growth ($499), Scale ($1,499)

2. API Key Collection
   ├─ Customer provides their OpenAI API key
   ├─ (Optional) Customer provides their own vector DB
   └─ We validate keys work

3. Instance Provisioning (Automated)
   ├─ Run Terraform for tenant
   ├─ Wait for health check
   └─ DNS record creation

4. Data Migration Setup
   ├─ Customer integrates SDK
   ├─ Annotates entities with @AICapable
   └─ Triggers initial migration

5. Go Live
   ├─ API endpoint provided
   ├─ Dashboard access granted
   └─ Support channel opened
```

### 8.2 Customer Integration Code

```java
// Customer's Spring Boot Application

// 1. Add dependency
// <dependency>
//   <groupId>com.ai.infrastructure</groupId>
//   <artifactId>ai-infrastructure-core</artifactId>
//   <version>1.0.0</version>
// </dependency>

// 2. Annotate entities
@Entity
@AICapable(
    entityType = "product",
    indexingStrategy = IndexingStrategy.ASYNC
)
public class Product {
    @Id
    private UUID id;

    private String name;

    @AIEmbeddable  // This field gets embedded
    private String description;

    private BigDecimal price;
    private Integer stock;
}

// 3. Configure application.yml
// (Point to their provisioned AI Fabric instance)

// 4. Use the AI capabilities
@Service
public class ProductSearchService {

    @Autowired
    private RAGOrchestrator orchestrator;

    public SearchResult search(String query, String userId) {
        OrchestrationRequest request = OrchestrationRequest.builder()
            .query(query)
            .identifier(userId)
            .entityTypes(List.of("product"))
            .build();

        return orchestrator.process(request);
    }
}
```

---

## Part 9: Pricing Model

### 9.1 Tiered Pricing

| Tier | Monthly Price | Includes | Overage |
|------|--------------|----------|---------|
| **Starter** | $149 | 10K queries, 100K vectors | $0.01/query |
| **Growth** | $499 | 50K queries, 500K vectors | $0.008/query |
| **Scale** | $1,499 | 200K queries, 2M vectors | $0.005/query |
| **Enterprise** | Custom | Unlimited | Custom |

### 9.2 Cost Structure

| Component | Your Cost | Margin |
|-----------|-----------|--------|
| ECS Fargate (small) | $35/mo | - |
| RDS PostgreSQL | $25/mo | - |
| Qdrant Cloud namespace | $25/mo | - |
| Monitoring/Logs | $10/mo | - |
| **Total Cost** | **$95/mo** | - |
| **Starter Price** | $149/mo | **36% margin** |
| **Growth Price** | $499/mo | **81% margin** |

---

## Part 10: Next Steps Checklist

### Week 1
- [ ] Set up AWS account with proper IAM
- [ ] Create ECR repository for container images
- [ ] Build and push first Docker image
- [ ] Deploy first test instance manually

### Week 2
- [ ] Set up Qdrant Cloud account
- [ ] Complete Terraform modules
- [ ] Test automated provisioning
- [ ] Create monitoring dashboards

### Week 3
- [ ] Build simple control plane API
- [ ] Create tenant database schema
- [ ] Implement provisioning endpoint
- [ ] Set up Stripe for billing

### Week 4
- [ ] Onboard first pilot customer
- [ ] Document customer integration guide
- [ ] Set up support channel
- [ ] Monitor and iterate

---

## Summary

This guide provides everything needed to convert the AI Fabric Framework into a cloud service:

1. **Architecture**: Single-tenant with namespace isolation
2. **Connectors**: LLM providers, embedding providers, vector databases
3. **Configuration**: Production-ready YAML configurations
4. **Infrastructure**: Complete Terraform for AWS deployment
5. **Provisioning**: Automated tenant setup scripts
6. **Pricing**: Sustainable pricing model with healthy margins

The key insight: **Don't build multi-tenancy yet.** Use infrastructure isolation (separate instances per customer) to move fast and validate the market. Add multi-tenancy when you have 50+ customers and the economics justify the complexity.

---

*Document Version: 1.0*
*Last Updated: January 2026*
