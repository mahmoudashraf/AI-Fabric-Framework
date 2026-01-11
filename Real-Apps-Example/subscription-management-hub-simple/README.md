# Subscription Management Hub - Simple App

A minimal demonstration of the AI Fabric Framework with just the essentials.

## Features

- ✅ Single entity (Plan) with @AICapable annotation
- ✅ Semantic search via AI Fabric Framework
- ✅ Auto-configuration (zero manual setup)
- ✅ REST API endpoints

## Quick Start

### Build
```bash
cd Real-Apps-Example/subscription-management-hub-simple
mvn clean package -DskipTests
```

### Run
```bash
java -jar target/subscription-management-hub-simple-1.0.0-SNAPSHOT.jar
```

## API Endpoints

### Get All Plans
```bash
curl http://localhost:8080/api/plans
```

### Get Plan by ID
```bash
curl http://localhost:8080/api/plans/{id}
```

### Semantic Search
```bash
curl "http://localhost:8080/api/plans/search?query=cheap%20plans&limit=5"
```

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

## Framework Integration

This simple app demonstrates:
- ✅ Auto-configuration (no @Import needed)
- ✅ Entity indexing via @AICapable
- ✅ Semantic search via AICoreService
- ✅ Vector database (Lucene) auto-configured

## Configuration

See `src/main/resources/application.yml` for AI Fabric configuration.

---

**Status**: ✅ Running and tested
