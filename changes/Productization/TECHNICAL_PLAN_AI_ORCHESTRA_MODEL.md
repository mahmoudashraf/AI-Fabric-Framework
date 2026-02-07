# AI Fabric Framework: Technical Architecture

## The Vision

**"We are the orchestra of AI."**

AI Fabric is the orchestration layer that gives any application intelligent capabilities. We don't replace your infrastructure - we connect to it and add intelligence.

---

## Core Architecture

### What AI Fabric IS

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AI FABRIC ORCHESTRATION                       │
│                                                                      │
│   ┌───────────────┐  ┌───────────────┐  ┌───────────────┐          │
│   │    Intent     │  │    Action     │  │     RAG       │          │
│   │  Extraction   │──│   Execution   │──│   Provider    │          │
│   │               │  │               │  │  (Optional)   │          │
│   └───────────────┘  └───────────────┘  └───────────────┘          │
│           │                  │                  │                   │
│   ┌───────────────┐  ┌───────────────┐  ┌───────────────┐          │
│   │   Security    │  │     PII       │  │     Chat      │          │
│   │    Layer      │  │  Detection    │  │   Sessions    │          │
│   └───────────────┘  └───────────────┘  └───────────────┘          │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
                                │
                         CONNECTS TO
                                │
┌───────────────────────────────┼───────────────────────────────────┐
│                               │                                    │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│    │  Customer's  │    │  Customer's  │    │  Customer's  │       │
│    │  LLM Keys    │    │  Vector DB   │    │  Actions     │       │
│    │              │    │  (Optional)  │    │  (REST API)  │       │
│    └──────────────┘    └──────────────┘    └──────────────┘       │
│                                                                    │
│                      CUSTOMER PROVIDES                             │
└────────────────────────────────────────────────────────────────────┘
```

### What AI Fabric IS NOT

- **Not a database** - Customer provides their own (relational + vector)
- **Not an LLM provider** - Customer provides their API keys
- **Not a chatbot** - We orchestrate intelligence, not just chat

---

## Deployment Architecture

### Per-Customer Isolated Deployment

Every customer gets a **completely isolated deployment**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CUSTOMER DEPLOYMENT (Isolated)                    │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    AI FABRIC (We Deploy)                        │ │
│  │                                                                 │ │
│  │    ┌──────────────────┐      ┌──────────────────┐              │ │
│  │    │   Orchestration  │      │   Internal DB    │              │ │
│  │    │     Runtime      │──────│   (Metadata)     │              │ │
│  │    │                  │      │                  │              │ │
│  │    │  • Intent        │      │  • Profiles      │              │ │
│  │    │  • Actions       │      │  • History       │              │ │
│  │    │  • RAG           │      │  • Sessions      │              │ │
│  │    │  • Security      │      │  • Sync State    │              │ │
│  │    └──────────────────┘      └──────────────────┘              │ │
│  │                                                                 │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                              │                                       │
│                       CONNECTS TO                                    │
│                              │                                       │
│  ┌───────────────┬───────────┴───────────┬───────────────┐         │
│  │               │                       │               │         │
│  ▼               ▼                       ▼               ▼         │
│ ┌─────────┐  ┌─────────┐          ┌─────────────┐  ┌──────────┐   │
│ │Customer │  │Customer │          │ Vector DB   │  │Customer  │   │
│ │LLM Keys │  │Actions  │          │ (Standalone)│  │Business  │   │
│ │         │  │(REST)   │          │             │  │Database  │   │
│ │OpenAI   │  │         │          │ Customer's  │  │(Optional)│   │
│ │Anthropic│  │Webhooks │          │ OR we deploy│  │          │   │
│ └─────────┘  └─────────┘          └─────────────┘  └──────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Ownership

| Component | Who Provides | Who Manages |
|-----------|--------------|-------------|
| **Orchestration Runtime** | AI Fabric | AI Fabric |
| **Internal Database** | AI Fabric | AI Fabric |
| **Vector Database** | Customer OR AI Fabric | Owner |
| **LLM API Keys** | Customer | Customer |
| **Actions (REST API)** | Customer | Customer |
| **Business Database** | Customer | Customer |

---

## Action Architecture

### How Actions Work

Actions are the bridge between AI understanding and real business operations. Customer defines actions as **REST API endpoints** that AI Fabric calls.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ACTION FLOW                                  │
│                                                                      │
│   User: "Cancel my subscription"                                     │
│                    │                                                 │
│                    ▼                                                 │
│   ┌────────────────────────────────┐                                │
│   │     AI Fabric Orchestrator     │                                │
│   │                                │                                │
│   │  1. Extract Intent             │                                │
│   │     → ACTION: cancel_subscription                               │
│   │     → Params: { userId: "123" }│                                │
│   │                                │                                │
│   │  2. Check if confirmation needed                                │
│   │     → Yes, requires confirmation                                │
│   │                                │                                │
│   │  3. Return confirmation prompt │                                │
│   └────────────────────────────────┘                                │
│                    │                                                 │
│                    ▼                                                 │
│   User: "Yes, confirm"                                              │
│                    │                                                 │
│                    ▼                                                 │
│   ┌────────────────────────────────┐                                │
│   │  4. Call Customer's Action API │                                │
│   │                                │                                │
│   │  POST https://customer.com/actions/cancel_subscription          │
│   │  Body: { userId: "123" }       │                                │
│   └────────────────────────────────┘                                │
│                    │                                                 │
│                    ▼                                                 │
│   ┌────────────────────────────────┐                                │
│   │  5. Return result to user      │                                │
│   │     "Your subscription has     │                                │
│   │      been cancelled."          │                                │
│   └────────────────────────────────┘                                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Action Registration

Customer registers their actions via **REST API** or **Dashboard**:

```
POST /api/actions/register

{
  "name": "cancel_subscription",
  "description": "Cancels user's subscription",
  "endpoint": "https://customer.com/api/actions/cancel-subscription",
  "method": "POST",
  "parameters": [
    { "name": "userId", "type": "string", "required": true },
    { "name": "reason", "type": "string", "required": false }
  ],
  "requiresConfirmation": true,
  "confirmationMessage": "Cancel your subscription? You'll lose access immediately."
}
```

### Action Capabilities

| Feature | Description |
|---------|-------------|
| **Confirmation** | Require user confirmation before execution |
| **Parameters** | Define required/optional parameters |
| **Authentication** | Webhook signature, API key, OAuth |
| **Retry** | Automatic retry on failure |
| **Timeout** | Configurable timeout per action |

---

## RAG Architecture (Optional)

RAG (Retrieval-Augmented Generation) is **optional**. Only needed if customer wants semantic search over their data.

### Two Modes

**Mode 1: Customer Manages Vector DB**
```
Customer syncs data to their vector DB
           │
           ▼
AI Fabric connects to customer's vector DB
           │
           ▼
Semantic search + generation
```

**Mode 2: AI Fabric Manages Vector DB**
```
Customer pushes data to AI Fabric Sync API
           │
           ▼
AI Fabric stores in standalone Qdrant (per customer)
           │
           ▼
Semantic search + generation
```

### Data Sync Options

| Mode | Description | Customer Responsibility |
|------|-------------|------------------------|
| **Push** | Customer calls our Sync API | Call API on data changes |
| **Pull** | We read from customer's DB | Provide read-only DB access |
| **Self-Managed** | Customer manages their vector DB | Full ownership |

---

## Security Architecture

### Per-Request Security Pipeline

```
Request → Security Analysis → Access Control → PII Detection → Process
              │                    │                │
              ▼                    ▼                ▼
        Block threats       Check permissions   Redact sensitive data
```

### Security Features

| Feature | Description |
|---------|-------------|
| **Threat Detection** | Injection attacks, prompt injection, data exfiltration |
| **Rate Limiting** | Per-user, per-operation limits |
| **PII Detection** | Detect and redact sensitive data (SSN, CC, etc.) |
| **Access Control** | Customer implements via policy hook |
| **Audit Logging** | All requests logged for compliance |

---

## Integration Points

### Customer Integrates Via

| Integration | Purpose |
|-------------|---------|
| **REST API** | Orchestration requests, action registration, data sync |
| **Webhooks** | Action execution callbacks |
| **SDK** (Optional) | Embedded in customer's app |

### API Overview

| Endpoint | Purpose |
|----------|---------|
| `POST /orchestrate` | Main AI orchestration |
| `POST /orchestrate/confirm` | Confirm pending action |
| `POST /actions/register` | Register customer action |
| `POST /sync/entity` | Sync data to vectors |
| `GET /health` | Health check |

---

## Deployment Options

### Option A: AI Fabric Managed (Recommended)

We deploy and manage everything:
- Orchestration runtime
- Internal database
- Vector database (standalone per customer)

Customer provides:
- LLM API keys
- Action endpoints
- Business database connection (optional, for sync)

### Option B: Customer Provides Vector DB

We deploy and manage:
- Orchestration runtime
- Internal database

Customer provides:
- LLM API keys
- Action endpoints
- Their own vector database

### Option C: Self-Hosted (SDK)

Customer deploys everything themselves using our SDK.

---

## Data Flow

### Orchestration Request

```
1. Customer app sends query
   POST /orchestrate { query: "Cancel my order", userId: "123" }

2. AI Fabric extracts intent via LLM (customer's API key)
   → Intent: ACTION
   → Action: cancel_order
   → Params: { userId: "123" }

3. If action requires confirmation
   → Return confirmation prompt to customer app

4. Customer confirms
   POST /orchestrate/confirm { requestId: "xxx", confirmed: true }

5. AI Fabric calls customer's action endpoint
   POST https://customer.com/actions/cancel-order { userId: "123" }

6. Return result to customer app
   { success: true, message: "Order cancelled" }
```

### Data Sync (If Using RAG)

```
1. Customer data changes

2. Customer syncs to AI Fabric
   POST /sync/entity { type: "product", id: "123", data: {...} }

3. AI Fabric generates embedding (customer's LLM key)

4. AI Fabric stores in vector database

5. Data available for semantic search
```

---

## Pricing Tiers

| Tier | Monthly | Includes |
|------|---------|----------|
| **Starter** | $199 | Isolated deployment, BYOD vector DB |
| **Growth** | $499 | + Managed standalone vector DB |
| **Scale** | $1,499 | + Higher limits, priority support |
| **Enterprise** | Custom | + SLA, dedicated support |

Customer always provides:
- LLM API keys (they control their costs)
- Action endpoints

---

## Key Principles

1. **Complete Isolation** - Every customer gets standalone infrastructure
2. **No Shared Databases** - Internal DB and Vector DB are per-customer
3. **Customer Owns Data** - We orchestrate, they own
4. **Actions via REST** - Simple webhook-style integration
5. **LLM Agnostic** - Customer chooses their LLM provider
6. **RAG Optional** - Use only if needed

---

## Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│   AI FABRIC = Orchestration + Intent + Actions + Optional RAG   │
│                                                                  │
│   We Provide:                                                    │
│   • Isolated orchestration runtime per customer                  │
│   • Internal database for framework metadata                     │
│   • Optional: Standalone vector database per customer            │
│   • Data sync module                                             │
│                                                                  │
│   Customer Provides:                                             │
│   • LLM API keys (OpenAI, Anthropic, etc.)                      │
│   • Action endpoints (REST API webhooks)                         │
│   • Vector database (optional - or we provide)                   │
│   • Business database connection (optional - for sync)           │
│                                                                  │
│   Value: "We orchestrate. You own your data."                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

*AI Fabric Framework - Technical Architecture v3.0*
