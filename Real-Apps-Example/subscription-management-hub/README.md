# Subscription Management Hub

AI-Powered Subscription Management Platform using AI Fabric Framework

## Overview

This application demonstrates the full capabilities of AI Fabric Framework through a real-world subscription management use case. It showcases:

- **Intent Action Handling** - Natural language to business logic
- **Semantic Search** - Natural language plan discovery
- **Behavior Analytics** - Churn prediction and recommendations
- **RAG Integration** - Context-aware Q&A
- **Automatic Indexing** - Via @AICapable and @AIProcess annotations

## Features

### Natural Language Interface
- Ask questions: "When does my subscription renew?"
- Give commands: "Cancel my subscription"
- Search semantically: "plans under $50 with unlimited storage"

### Subscription Actions
- Subscribe to plans
- Cancel subscriptions
- Upgrade/Downgrade plans
- Update billing/shipping addresses

### AI-Powered Features
- Semantic plan search
- Churn risk prediction
- Personalized upgrade recommendations
- Address validation with PII detection

## Technology Stack

- **Java 21**
- **Spring Boot 3.2.0**
- **AI Fabric Framework 1.0.0**
- **H2 Database** (development)
- **PostgreSQL** (production)

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+
- OpenAI API Key (optional, for LLM features)

### Setup

1. **Clone and navigate:**
```bash
cd subscription-management-hub
```

2. **Configure OpenAI (optional):**
```bash
export OPENAI_API_KEY=your-api-key
```

3. **Build:**
```bash
mvn clean install
```

4. **Run:**
```bash
mvn spring-boot:run
```

5. **Access:**
- Application: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console

## API Endpoints

### Natural Language Interface
- `POST /api/subscriptions/query` - Natural language query
- `POST /api/subscriptions/query/actions/execute` - Execute confirmed actions

### Subscription Management
- `POST /api/subscriptions/subscribe` - Subscribe to plan
- `POST /api/subscriptions/{id}/unsubscribe` - Cancel subscription
- `POST /api/subscriptions/{id}/upgrade` - Upgrade plan
- `POST /api/subscriptions/{id}/downgrade` - Downgrade plan
- `GET /api/subscriptions/{id}` - Get subscription details

### Plan Discovery
- `GET /api/subscriptions/plans` - List all plans
- `GET /api/subscriptions/plans/{id}` - Get plan details
- `POST /api/subscriptions/plans/search` - Semantic search

## Example Usage

### Natural Language Query
```bash
curl -X POST http://localhost:8080/api/subscriptions/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Cancel my subscription",
    "userId": "user-123"
  }'
```

### Semantic Plan Search
```bash
curl -X POST http://localhost:8080/api/subscriptions/plans/search?query=plans%20under%20%2450
```

## Framework Integration

### Entities with @AICapable
- `SubscriptionPlan` - Auto-indexed for semantic search
- `Subscription` - Tracked for behavior analytics

### Services with @AIProcess
- `SubscriptionService` - All CRUD operations use @AIProcess for vector sync

### Action Handlers
- `CancelSubscriptionActionHandler`
- `SubscribeActionHandler`
- `UpgradeSubscriptionActionHandler`
- `UpdateAddressActionHandler`

## Configuration

See `src/main/resources/application.yml` and `ai-entity-config.yml` for framework configuration.

## Documentation

See `Final_Documentation/Real_Apps/SUBSCRIPTION_MANAGEMENT_HUB_BRD.md` for complete business requirements and implementation details.
