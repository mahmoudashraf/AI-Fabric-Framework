# Basic LoomAI Inbox

Basic LoomAI Inbox is the low-volume reference provider for LoomAI's generic
`CONVERSATION_CHANNEL` contract. It proves queue, claim, human reply, return to
AI, callback retry, and close behavior without putting operator workflows in
the deployment runtime or Platform control plane.

It is intentionally small. It is suitable for demos, testing, and customers
with a very low conversation volume. It is not a workforce-management, CRM,
skills-routing, or omnichannel product.

## Isolation Model

Deploy one inbox instance for exactly one trusted tenant/deployment pair.
`BASIC_INBOX_TENANT_ID`, `BASIC_INBOX_DEPLOYMENT_ID`, and
`BASIC_INBOX_BINDING_ID` are mandatory. A dispatch whose trusted scope does not
match that binding fails closed with `DEPLOYMENT_SCOPE_MISMATCH`.

The deployment runtime remains authoritative for conversation control and the
canonical safe transcript. This service keeps only the bounded provider copy
needed by an operator and its callback outbox.

## Production Configuration

Required:

```text
PORT
BASIC_INBOX_BINDING_ID
BASIC_INBOX_TENANT_ID
BASIC_INBOX_DEPLOYMENT_ID
BASIC_INBOX_PROVIDER_API_KEY
BASIC_INBOX_CONNECTOR_CALLBACK_URL
BASIC_INBOX_CALLBACK_SIGNING_SECRET
BASIC_INBOX_DATA_ENCRYPTION_SECRET
BASIC_INBOX_OPERATORS
BASIC_INBOX_JDBC_URL
BASIC_INBOX_JDBC_USERNAME
BASIC_INBOX_JDBC_PASSWORD
```

Use distinct random values of at least 32 characters for provider,
callback-signing, encryption, and operator API keys. Store them in the hosting
secret manager. Never place values in Marketplace manifests, source control,
browser configuration, or deployment audit payloads.

`BASIC_INBOX_OPERATORS` has this provider-local format:

```text
operatorId|displayName|operatorApiKey|bindingId[,bindingId];...
```

Use PostgreSQL for production. The default file-backed H2 database exists only
for local proof and must not be used for a production inbox.

Optional tuning:

```text
BASIC_INBOX_REQUEST_TIMEOUT=10s
BASIC_INBOX_MAX_CALLBACK_ATTEMPTS=8
BASIC_INBOX_CALLBACK_RETRY_DELAY=5s
BASIC_INBOX_RETENTION=30d
JAVA_OPTS=-Xms256m -Xmx768m
```

## Connector Contract

The deployment connector posts committed dispatches to:

```text
POST /api/conversation-channel/v1/dispatches
X-LoomAI-Inbox-Key: <deployment-bound provider key>
```

The inbox signs normalized callbacks to the configured connector callback URL.
Return-to-AI and close remain retryable while an earlier human-message callback
is incomplete, preserving FIFO delivery before authority changes.

Operator endpoints are under `/api/operator` and require
`X-LoomAI-Operator-Key`. The static operator UI uses only those public provider
endpoints; it has no private Platform or runtime path.

## Build And Verify

```bash
mvn -B -V --no-transfer-progress clean test
docker build -f deploy/container/Dockerfile -t basic-loomai-inbox:0.1.0 .
```

Health probes:

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
```

See [Conversation Handover and Basic Inbox Operations](../../Final_Documentation/Development_Guides/CONVERSATION_HANDOVER_AND_BASIC_INBOX_OPERATIONS_GUIDE.md)
for the complete deployment composition and release checks.
