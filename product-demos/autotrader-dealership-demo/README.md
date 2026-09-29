# LoomAI Dealership AI Experience

This product demo models how an ordinary dealership application can use one
assigned LoomAI deployment without embedding AI Fabric or proxying buyer chat
through the central Platform.

## Components

- Customer and staff UI: `Platfrom/loomai-site/src/pages/demos/dealership-ai`
- Dealership-owned backend: `product-demos/autotrader-dealership-demo/backend`
- Reusable chat shell: `max-mode-widget`
- Runtime composition files: `backend/deployment/runtime`
- Generic REST Connector routing: `backend/deployment/connector`

The included dealership, vehicles, people and requests are fictional. No Auto
Trader credential, API, sandbox or production data is used by this source
composition.

## Integration boundary

```text
browser -> dealership backend        structured inventory and staff workspace
browser -> assigned LoomAI runtime   anonymous Companion / Max Mode chat
dealership backend -> runtime        private inventory Data Sync and work status
runtime connector -> backend         protected authorization and confirmed actions
```

The browser receives no dealership internal key, runtime trusted-backend key,
private assertion key or provider credential.

## Current evidence

- backend integration tests cover inventory, safe runtime projection,
  fail-closed deployment authorization, staff login, encrypted lead storage and
  action idempotency;
- the Generic REST Connector accepts the supplied routing contract and reaches
  traffic-ready state;
- public-site type, content, static, accessibility, responsive and interaction
  browser gates pass; and
- both backend and public-site production containers build and start locally.

The hosted LoomAI indexing, retrieval and confirmed-action canary remains a
separate release gate. The public catalogue labels this experience `preview`
until that evidence exists.
