# AI Fabric Relay

Platform-owned deployable Customer Connector API runtime for actions and documents-only retrieval.

This service was moved out of the public AI Fabric framework reactor. The framework owns the portable
connector contracts and caller libraries; this repository owns relay packaging, deployment, smoke
verification, and production operations.

## Build And Test

From the platform repository root:

```bash
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml -pl ai-fabric-relay -am test
```

Package the executable Spring Boot jar:

```bash
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml -pl ai-fabric-relay -am package
```

Run the packaged local smoke:

```bash
.github/scripts/smoke-p1-relay-local.sh
```

The smoke starts a local internal stub plus the packaged relay jar and verifies API-key rejection,
action forwarding, idempotency replay/conflict, retrieval forwarding, and generated-response
rejection for documents-only retrieval.
