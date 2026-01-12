# Subscription Management Hub

AI-Powered Subscription Management Platform using AI Fabric Framework

## Overview

This is a simplified version of the Subscription Management Hub that uses AI Fabric Core for LLM-powered natural language processing. It demonstrates:

- **Intent Action Handling** - Natural language to business logic via LLM queries
- **RAG Integration** - Context-aware Q&A using AI Fabric Core
- **Action Execution** - Natural language commands executed through action handlers

This version uses only `ai-fabric-core` and `ai-infrastructure-provider-cohere` for LLM queries, without vector search, behavior analytics, or automatic indexing dependencies.

## Features

### User Management
- **100 Pre-seeded Users** - Created automatically on startup
- **Guest Login** - Random user assignment (1-100) for demo purposes
- **User Profiles** - Each user has unique subscription state and information

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
- Natural language query processing via LLM
- Intent extraction and action execution
- Context-aware responses

### Data Seeding
On startup, the application automatically creates:
- **3 Subscription Plans** (Basic, Pro, Enterprise)
- **100 Users** (user_1 through user_100)
- **~90 Subscriptions** (70% active, 20% cancelled, 10% no subscription)
- **Random addresses** for subscribed users

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
- Cohere API Key (for LLM features)

### Local Development

1. **Clone and navigate:**
```bash
cd subscription-management-hub
```

2. **Build AI Infrastructure Module first:**
```bash
cd ../../ai-infrastructure-module
mvn clean install -DskipTests -pl ai-infrastructure-core,providers/ai-infrastructure-provider-cohere -am
```

3. **Build application:**
```bash
cd ../../Real-Apps-Example/subscription-management-hub-simple
mvn clean install
```

4. **Configure Cohere API Key:**
```bash
export COHERE_API_KEY=your-api-key
```

5. **Run:**
```bash
mvn spring-boot:run
```

6. **Access:**
- Application: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs (JSON): http://localhost:8080/api-docs
- H2 Console: http://localhost:8080/h2-console
- Health Check: http://localhost:8080/actuator/health

### 🚀 Quick Deployment

**Easiest: Railway (5 minutes)**
1. Push code to GitHub
2. Go to [railway.app](https://railway.app)
3. New Project → Deploy from GitHub
4. Add PostgreSQL database
5. Set `COHERE_API_KEY` environment variable
6. Done!

See [QUICK_DEPLOY.md](QUICK_DEPLOY.md) for detailed deployment instructions.

## API Endpoints

### Guest Login
- `POST /api/auth/guest/login` - Login as guest, get assigned random user (1-100)
- `GET /api/auth/guest/random-user` - Get random user info without login

### Natural Language Interface
- `POST /api/subscriptions/query` - Natural language query (supports numeric userId 1-100)
- `POST /api/subscriptions/query/actions/execute` - Execute confirmed actions

### Subscription Management
- `POST /api/subscriptions/subscribe?userId={1-100}&planId={uuid}` - Subscribe to plan
- `POST /api/subscriptions/{id}/unsubscribe` - Cancel subscription
- `POST /api/subscriptions/{id}/upgrade` - Upgrade plan
- `POST /api/subscriptions/{id}/downgrade` - Downgrade plan
- `GET /api/subscriptions/{id}` - Get subscription details
- `GET /api/subscriptions/user/{userId}/active` - Get active subscription (userId: 1-100)

### Plan Discovery
- `GET /api/subscriptions/plans` - List all plans
- `GET /api/subscriptions/plans/{id}` - Get plan details
- `POST /api/subscriptions/plans/search?query={text}` - Basic text search

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

See `src/main/resources/application.yml` for framework configuration. The application uses AI Fabric Core with Cohere provider for LLM queries.

## Documentation

See `Final_Documentation/Real_Apps/SUBSCRIPTION_MANAGEMENT_HUB_BRD.md` for complete business requirements and implementation details.
