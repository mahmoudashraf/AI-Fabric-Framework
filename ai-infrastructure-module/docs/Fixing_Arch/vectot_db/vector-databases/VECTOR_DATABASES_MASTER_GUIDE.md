# Vector Databases - Master Implementation Guide

## Overview

This master guide provides a comprehensive overview of all vector database providers supported by the AI Fabric Framework, their current implementation status, migration plans, and selection criteria.

## Document Status
**Last Updated**: 2026-01-11
**Version**: 1.0
**Maintainer**: AI Infrastructure Team

---

## Supported Vector Database Providers

The framework supports **6 vector database providers** across different categories:

### Production Cloud Vector Databases
1. **Qdrant** - High-performance vector search engine
2. **Pinecone** - Managed vector database service
3. **Weaviate** - AI-native vector database
4. **Milvus** - Open-source vector database

### Local/Development Vector Databases
5. **Apache Lucene** - File-based vector search
6. **In-Memory** - Simple in-memory storage for testing

---

## Implementation Status Matrix

| Provider | Current Status | Client Type | Migration Needed | Documentation |
|----------|---------------|-------------|------------------|---------------|
| **Qdrant** | ✅ Official SDK | io.qdrant:client:1.16.1 | ❌ No | [Migration Plan](./QDRANT_OFFICIAL_CLIENT_MIGRATION_PLAN.md) |
| **Pinecone** | ✅ Official SDK | io.pinecone:pinecone-client:2.0.0 | ❌ No | [Migration Plan](./PINECONE_OFFICIAL_CLIENT_MIGRATION_PLAN.md) |
| **Weaviate** | ✅ Official SDK | io.weaviate:client:4.5.0 | ❌ No | [Migration Plan](./WEAVIATE_OFFICIAL_CLIENT_MIGRATION_PLAN.md) |
| **Milvus** | ✅ Official SDK | io.milvus:milvus-sdk-java:2.4.1 | ❌ No | [Best Practices](./MILVUS_BEST_PRACTICES_GUIDE.md) |
| **Lucene** | ✅ Apache Lucene | Direct Library Usage | ❌ No | N/A (Optimal) |
| **Memory** | ✅ Pure Java | In-Memory Implementation | ❌ No | N/A (Testing Only) |

### Status Indicators
- ✅ **Optimal**: Using best-practice implementation
- ⚠️ **Needs Migration**: Currently using REST API, should migrate to official SDK
- ❌ **No Migration**: Already optimal or not applicable

---

## Provider Comparison

### Quick Selection Guide

```
┌─────────────────────────────────────────────────────────────┐
│                    Choose Your Provider                      │
└─────────────────────────────────────────────────────────────┘

Production Deployment?
│
├─ YES → Cloud or Self-Hosted?
│   │
│   ├─ CLOUD → Managed Service?
│   │   │
│   │   ├─ YES → Budget?
│   │   │   │
│   │   │   ├─ High → PINECONE (Best managed experience)
│   │   │   └─ Medium → QDRANT Cloud (Good balance)
│   │   │
│   │   └─ NO → Self-Hosted Cloud?
│   │       │
│   │       ├─ Kubernetes → WEAVIATE or MILVUS
│   │       └─ Docker → QDRANT or MILVUS
│   │
│   └─ SELF-HOSTED → Scale?
│       │
│       ├─ Large (10M+ vectors) → MILVUS (Best for scale)
│       ├─ Medium (100K-10M) → QDRANT or WEAVIATE
│       └─ Small (<100K) → Any cloud provider
│
└─ NO → Development/Testing?
    │
    ├─ Need Persistence → LUCENE (File-based)
    └─ No Persistence → MEMORY (In-memory)
```

### Detailed Comparison

| Feature | Qdrant | Pinecone | Weaviate | Milvus | Lucene | Memory |
|---------|--------|----------|----------|--------|--------|--------|
| **Deployment** | Cloud/Self-hosted | Cloud Only | Cloud/Self-hosted | Self-hosted | Local | Local |
| **Pricing** | Free tier + Usage | Paid (Free starter) | Free tier + Usage | Open-source (Free) | Free | Free |
| **Scale** | Medium-Large | Small-Large | Medium-Large | Very Large | Small | Tiny |
| **Performance** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **Ease of Use** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Features** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐ |
| **Testcontainers** | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes | N/A | N/A |
| **Protocol** | gRPC/HTTP | gRPC/HTTP | gRPC/HTTP | gRPC | Native | Native |
| **Query Language** | REST/gRPC | REST/gRPC | GraphQL | REST/gRPC | Lucene Query | None |
| **Best For** | Balanced needs | Managed simplicity | AI workloads | Massive scale | Local dev | Unit tests |

---

## Detailed Provider Profiles

### 1. Qdrant

**Status**: ✅ Official SDK (gRPC)

**Overview**:
- High-performance vector search engine
- Excellent balance of features and performance
- Strong filtering capabilities
- Good documentation

**Current Implementation**:
- Official Qdrant Java client (gRPC)

**Migration Plan**: [QDRANT_OFFICIAL_CLIENT_MIGRATION_PLAN.md](./QDRANT_OFFICIAL_CLIENT_MIGRATION_PLAN.md)

**When to Use**:
- ✅ Need both cloud and self-hosted options
- ✅ Require advanced filtering
- ✅ Want good performance without complexity
- ✅ Need payload indexing
- ✅ Medium to large datasets

**When to Avoid**:
- ❌ Need simple managed solution (use Pinecone)
- ❌ Need GraphQL queries (use Weaviate)
- ❌ Need extreme scale (use Milvus)

**Configuration**:
```yaml
ai:
  vector-db:
    type: qdrant
  providers:
    qdrant:
      enabled: true
      host: localhost
      port: 6333
      grpc-port: 6334
      prefer-grpc: true  # Recommended
      api-key: ${QDRANT_API_KEY}
```

---

### 2. Pinecone

**Status**: ✅ Official SDK

**Overview**:
- Fully managed vector database service
- Excellent developer experience
- Auto-scaling and high availability
- Cloud-only

**Current Implementation**:
- Official Pinecone Java client

**Migration Plan**: [PINECONE_OFFICIAL_CLIENT_MIGRATION_PLAN.md](./PINECONE_OFFICIAL_CLIENT_MIGRATION_PLAN.md)

**When to Use**:
- ✅ Want fully managed solution
- ✅ Don't want to manage infrastructure
- ✅ Need guaranteed uptime
- ✅ Budget for managed service
- ✅ Want simple setup

**When to Avoid**:
- ❌ Need self-hosted deployment
- ❌ Cost-sensitive projects
- ❌ Need local development environment
- ❌ Require data locality guarantees

**Configuration**:
```yaml
ai:
  vector-db:
    type: pinecone
  providers:
    pinecone:
      enabled: true
      api-key: ${PINECONE_API_KEY}
      environment: us-east1-gcp
      index-name: my-vector-index
      namespace: default
```

**Important Notes**:
- No Testcontainers support (cloud-only)
- Requires pre-created index
- Uses namespaces for organization
- Paid service (free tier available)

---

### 3. Weaviate

**Status**: ✅ Official SDK

**Overview**:
- AI-native vector database
- GraphQL query interface
- Rich schema and data modeling
- Excellent for complex queries

**Current Implementation**:
- Official Weaviate Java client

**Migration Plan**: [WEAVIATE_OFFICIAL_CLIENT_MIGRATION_PLAN.md](./WEAVIATE_OFFICIAL_CLIENT_MIGRATION_PLAN.md)

**When to Use**:
- ✅ Need complex queries (GraphQL)
- ✅ Want rich schema features
- ✅ Need built-in AI modules
- ✅ Require graph-like relationships
- ✅ Want hybrid search (vector + keyword)

**When to Avoid**:
- ❌ Need simplest possible API
- ❌ Unfamiliar with GraphQL
- ❌ Need extreme performance

**Configuration**:
```yaml
ai:
  vector-db:
    type: weaviate
  providers:
    weaviate:
      enabled: true
      scheme: http
      host: localhost
      port: 8080
      api-key: ${WEAVIATE_API_KEY}
```

**Unique Features**:
- GraphQL query interface
- Built-in schema management
- Testcontainers support ✅
- Hybrid search capabilities

---

### 4. Milvus

**Status**: ✅ Already Using Official SDK

**Overview**:
- Open-source vector database
- Designed for massive scale
- Excellent performance
- Battle-tested in production

**Current Implementation**:
- Official SDK: `io.milvus:milvus-sdk-java:2.4.1`
- Mature implementation ✅

**Best Practices Guide**: [MILVUS_BEST_PRACTICES_GUIDE.md](./MILVUS_BEST_PRACTICES_GUIDE.md)

**When to Use**:
- ✅ Need to handle 10M+ vectors
- ✅ Require maximum performance
- ✅ Want open-source solution
- ✅ Can manage infrastructure
- ✅ Need advanced indexing (HNSW, IVF, etc.)

**When to Avoid**:
- ❌ Small datasets (<100K vectors)
- ❌ Want fully managed solution
- ❌ Limited ops resources

**Configuration**:
```yaml
ai:
  vector-db:
    type: milvus
  providers:
    milvus:
      enabled: true
      host: localhost
      port: 19530
      username: ${MILVUS_USERNAME}
      password: ${MILVUS_PASSWORD}
      database-name: default
      secure: false
```

**Strengths**:
- ✅ Already optimal implementation
- ✅ Excellent documentation
- ✅ Testcontainers support
- ✅ Multiple index types
- ✅ High performance at scale

---

### 5. Apache Lucene

**Status**: ✅ Optimal for Local Use

**Overview**:
- File-based vector search
- Part of Apache Lucene project
- Local development friendly
- No external dependencies

**When to Use**:
- ✅ Local development
- ✅ Small datasets (<100K)
- ✅ Need file persistence
- ✅ No network required
- ✅ Embedded search

**When to Avoid**:
- ❌ Production deployments
- ❌ Large datasets
- ❌ Need distributed search
- ❌ Require cloud deployment

**Configuration**:
```yaml
ai:
  vector-db:
    type: lucene
```

**Use Cases**:
- Local development
- Demo applications
- Embedded search
- Desktop applications

---

### 6. In-Memory

**Status**: ✅ Optimal for Testing

**Overview**:
- Pure Java in-memory implementation
- No persistence
- Simplest possible implementation
- Testing and demos only

**When to Use**:
- ✅ Unit tests
- ✅ Integration tests
- ✅ Quick prototypes
- ✅ Demos and examples

**When to Avoid**:
- ❌ Any persistence needed
- ❌ Production use
- ❌ Large datasets
- ❌ Multi-instance deployments

**Configuration**:
```yaml
ai:
  vector-db:
    type: memory
```

**Perfect For**:
- Unit testing
- CI/CD pipelines
- Learning and exploration
- Rapid prototyping

---

## Migration Priority Roadmap

### High Priority (Should Migrate Now)
1. **Qdrant** - Actively being migrated, significant performance gains
2. **Pinecone** - Official SDK provides better reliability

### Medium Priority (Plan Migration)
3. **Weaviate** - GraphQL complexity makes official client valuable

### Low Priority (Already Optimal)
4. **Milvus** - ✅ Already using official SDK
5. **Lucene** - ✅ Already optimal
6. **Memory** - ✅ Already optimal

---

## Implementation Timeline Summary

| Provider | Type | Estimated Effort | Risk | Priority |
|----------|------|-----------------|------|----------|
| Qdrant | Migration | 10-15 days | Low | High |
| Pinecone | Migration | 8-10 days | Medium | High |
| Weaviate | Migration | 9-10 days | Low-Medium | Medium |
| Milvus | Optimization | 4-5 days | Low | Low |
| Lucene | None | N/A | N/A | N/A |
| Memory | None | N/A | N/A | N/A |

**Total Migration Effort**: ~31-40 days across all providers

---

## Testing Strategy

### Testcontainers Support

| Provider | Testcontainers | Local Testing | Notes |
|----------|---------------|---------------|-------|
| Qdrant | ✅ Yes | ✅ Yes | `qdrant/qdrant:latest` |
| Pinecone | ❌ No | ⚠️ Free Tier | Cloud-only, use free tier |
| Weaviate | ✅ Yes | ✅ Yes | `semitechnologies/weaviate` |
| Milvus | ✅ Yes | ✅ Yes | `milvusdb/milvus` |
| Lucene | N/A | ✅ Yes | Native file-based |
| Memory | N/A | ✅ Yes | Pure Java |

### Recommended Testing Approach

**Unit Tests**:
- Mock client interfaces with Mockito
- Test business logic independently
- Fast execution (<1s per test)

**Integration Tests**:
- Use Testcontainers where available
- Use in-memory provider for simple tests
- Use free tiers for cloud-only providers

**Performance Tests**:
- Benchmark with realistic data volumes
- Test with Testcontainers or staging environments
- Monitor query latency and throughput

---

## Configuration Management

### Environment-Based Configuration

**Development**:
```yaml
ai:
  vector-db:
    type: memory  # or lucene for persistence
```

**Testing**:
```yaml
ai:
  vector-db:
    type: qdrant  # Using Testcontainers
```

**Staging**:
```yaml
ai:
  vector-db:
    type: qdrant  # or milvus
  providers:
    qdrant:
      host: qdrant-staging.internal
      prefer-grpc: true
```

**Production**:
```yaml
ai:
  vector-db:
    type: ${VECTOR_DB_TYPE:pinecone}
  providers:
    pinecone:
      api-key: ${PINECONE_API_KEY}
      environment: ${PINECONE_ENV}
      index-name: ${PINECONE_INDEX}
    qdrant:
      host: ${QDRANT_HOST}
      api-key: ${QDRANT_API_KEY}
      prefer-grpc: true
    weaviate:
      host: ${WEAVIATE_HOST}
      api-key: ${WEAVIATE_API_KEY}
    milvus:
      host: ${MILVUS_HOST}
      username: ${MILVUS_USERNAME}
      password: ${MILVUS_PASSWORD}
```

---

## Performance Characteristics

### Typical Performance Metrics

| Operation | Qdrant | Pinecone | Weaviate | Milvus | Lucene | Memory |
|-----------|--------|----------|----------|--------|--------|--------|
| **Insert (1K vectors)** | ~500ms | ~800ms | ~600ms | ~400ms | ~1s | <10ms |
| **Search (top 10)** | <50ms | <100ms | <80ms | <30ms | ~200ms | <5ms |
| **Batch Insert (10K)** | ~2s | ~4s | ~3s | ~1.5s | ~15s | ~50ms |
| **Delete (1 vector)** | <10ms | <50ms | <20ms | <10ms | ~50ms | <1ms |

*Note: Actual performance varies based on vector dimensions, dataset size, and hardware*

### Scalability Limits

| Provider | Recommended Max | Theoretical Max | Notes |
|----------|----------------|-----------------|-------|
| Qdrant | 10M vectors | 100M+ | Self-hosted scales better |
| Pinecone | 10M vectors | Unlimited | Auto-scaling (paid) |
| Weaviate | 10M vectors | 100M+ | Depends on resources |
| Milvus | 100M+ vectors | Billions | Best for large scale |
| Lucene | 1M vectors | 10M | File-based limits |
| Memory | 100K vectors | 1M | RAM-limited |

---

## Cost Considerations

### Approximate Monthly Costs

**Pinecone**:
- Starter: Free (1M vectors, 1 pod)
- Standard: $70/pod + storage
- Enterprise: Custom pricing

**Qdrant Cloud**:
- Free tier: 1GB RAM
- Starter: ~$25/month
- Production: ~$100+/month

**Self-Hosted (Qdrant/Weaviate/Milvus)**:
- Infrastructure costs only
- AWS/GCP: $50-500/month depending on scale
- Kubernetes cluster: Variable

**Local (Lucene/Memory)**:
- Free (uses local resources)

---

## Quick Start Guide

### 1. Choose Your Provider

Use the selection guide above or these defaults:
- **Production**: Pinecone (managed) or Milvus (self-hosted)
- **Development**: Lucene (persistence) or Memory (fastest)
- **Testing**: Memory or Testcontainers

### 2. Configure Application

Add to `application.yml`:
```yaml
ai:
  vector-db:
    type: <your-choice>
  providers:
    <provider>:
      enabled: true
      # ... provider-specific config
```

### 3. Verify Setup

```java
@Autowired
private VectorDatabaseService vectorService;

@Test
void testVectorStorage() {
    String vectorId = vectorService.storeVector(
        "test",
        "entity-1",
        "test content",
        List.of(0.1, 0.2, 0.3, ...),
        Map.of("key", "value")
    );
    assertNotNull(vectorId);
}
```

### 4. Monitor and Optimize

- Check logs for performance
- Monitor query latency
- Tune provider-specific parameters
- Review cost (for cloud providers)

---

## Common Questions

### Which provider should I use for production?

**For managed service**: Pinecone (easiest, auto-scaling)
**For self-hosted**: Milvus (best performance at scale)
**For balanced needs**: Qdrant (good mix of features/performance)
**For AI workloads**: Weaviate (GraphQL, rich features)

### Can I switch providers later?

Yes! The framework uses a common `VectorDatabaseService` interface. You can switch by:
1. Changing `ai.vector-db.type` configuration
2. Migrating data (export/import)
3. No code changes needed

### How do I test locally?

- **With Testcontainers**: Qdrant, Weaviate, Milvus
- **Without Docker**: Lucene (file-based) or Memory (in-memory)
- **Cloud Providers**: Use free tiers (Pinecone starter)

### What about data migration?

All providers support the same interface, so you can write a migration script:
```java
// Read from source
List<VectorRecord> records = sourceProvider.getAllVectors();

// Write to destination
records.forEach(record ->
    destProvider.storeVector(
        record.getEntityType(),
        record.getEntityId(),
        record.getContent(),
        record.getEmbedding(),
        record.getMetadata()
    )
);
```

---

## Related Documentation

### Provider-Specific Guides
- [Qdrant Migration Plan](./QDRANT_OFFICIAL_CLIENT_MIGRATION_PLAN.md)
- [Pinecone Migration Plan](./PINECONE_OFFICIAL_CLIENT_MIGRATION_PLAN.md)
- [Weaviate Migration Plan](./WEAVIATE_OFFICIAL_CLIENT_MIGRATION_PLAN.md)
- [Milvus Best Practices](./MILVUS_BEST_PRACTICES_GUIDE.md)

### Framework Documentation
- Core Framework Documentation (../README.md)
- Vector Database Interface (../api/VectorDatabaseService.md)
- Testing Guide (../testing/VECTOR_DB_TESTING.md)

---

## Changelog

### Version 1.0 (2026-01-11)
- Initial master guide created
- All 6 providers documented
- Migration plans for REST-based providers
- Best practices for Milvus
- Comparison matrices and selection guides

---

## Contributing

When adding a new vector database provider:

1. Create provider-specific documentation
2. Update this master guide
3. Add to comparison matrices
4. Update selection guide
5. Add configuration examples
6. Include in testing strategy

---

## Support and Questions

For questions about vector database selection or implementation:
1. Review this guide and provider-specific documentation
2. Check the comparison matrices
3. Consult with the AI Infrastructure team
4. Open a discussion in the team channel

**Last Updated**: 2026-01-11
**Maintainer**: AI Infrastructure Team
