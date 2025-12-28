# AI Infrastructure Web Module - User Guide

## Overview

The AI Infrastructure Web Module provides production-ready REST API controllers that expose AI capabilities through HTTP endpoints. It transforms the power of the AI Infrastructure Core into a RESTful API, making it easy to integrate AI features into web applications, mobile apps, and microservices.

### What This Module Does

- **REST API Exposure**: 59 production-ready REST endpoints for all AI features
- **Advanced RAG**: Query expansion, re-ranking, and hybrid search APIs
- **Migration Management**: Control bulk data migration via REST
- **AI Profile Management**: CRUD operations for AI profiles
- **Security & Compliance**: Security analysis and compliance checking
- **Health & Monitoring**: Health checks and statistics for all services
- **Automatic Configuration**: Auto-discovers and exposes available services

### Target Audience

Developers building:
- Frontend applications consuming AI services
- Microservices architectures
- API-first applications
- Mobile app backends
- Third-party integrations

---

## Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-web</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Requires core module -->
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure (Optional)

```yaml
ai:
  web:
    enabled: true              # Enable web module (default: true)
    base-path: /api/ai         # API base path (default: /api/ai)
    controllers:
      advanced-rag: true       # Enable/disable specific controllers
      profile: true
      compliance: true
      security: true
```

### 3. Call the APIs

```bash
# Advanced RAG search
curl -X POST http://localhost:8080/api/ai/advanced-rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "How do I reset my password?",
    "entityType": "help-article",
    "limit": 5,
    "enableQueryExpansion": true,
    "enableReRanking": true
  }'

# Start data migration
curl -X POST http://localhost:8080/api/ai/migration/start \
  -H "Content-Type: application/json" \
  -d '{
    "entityType": "product",
    "batchSize": 500,
    "rateLimit": 100
  }'
```

**That's it.** AI features are now accessible via REST API.

---

## API Endpoints Reference

### Advanced RAG Endpoints

Base path: `/api/ai/advanced-rag`

#### POST /search

Perform advanced RAG with query expansion and re-ranking.

**Request Body**:
```json
{
  "query": "What are the benefits of AI?",
  "entityType": "article",
  "limit": 10,
  "threshold": 0.7,
  "enableQueryExpansion": true,
  "enableReRanking": true,
  "enableHybridSearch": false,
  "context": {
    "userRole": "admin",
    "department": "engineering"
  },
  "requestId": "req-123"
}
```

**Response**:
```json
{
  "query": "What are the benefits of AI?",
  "expandedQueries": [
    "What are the benefits of artificial intelligence?",
    "AI advantages and use cases"
  ],
  "documents": [
    {
      "id": "doc-123",
      "content": "AI provides automation...",
      "score": 0.92,
      "metadata": {
        "category": "technology"
      }
    }
  ],
  "totalDocuments": 15,
  "usedDocuments": 10,
  "reRankingApplied": true,
  "success": true,
  "processingTimeMs": 245
}
```

#### GET /stats

Get advanced RAG statistics.

**Response**:
```json
{
  "totalQueries": 1523,
  "averageProcessingTime": 187.5,
  "successRate": 0.98,
  "timestamp": 1703779200000
}
```

#### GET /health

Health check for advanced RAG service.

**Response**:
```json
{
  "status": "UP",
  "service": "AdvancedRAGService",
  "timestamp": 1703779200000
}
```

---

### Migration Endpoints

Base path: `/api/ai/migration`

#### POST /start

Start a new migration job.

**Request Body**:
```json
{
  "entityType": "product",
  "batchSize": 500,
  "rateLimit": 100,
  "reindexExisting": false,
  "filters": {
    "createdAfter": "2024-01-01",
    "createdBefore": "2024-12-31",
    "entityIds": ["prod-1", "prod-2"]
  },
  "createdBy": "admin"
}
```

**Response**:
```json
{
  "id": "mig-a1b2c3d4",
  "entityType": "product",
  "status": "RUNNING",
  "totalEntities": 10000,
  "processedEntities": 0,
  "failedEntities": 0,
  "batchSize": 500,
  "rateLimit": 100,
  "reindexExisting": false,
  "startedAt": "2024-12-28T10:00:00",
  "createdBy": "admin"
}
```

#### GET /jobs

List all migration jobs.

**Response**:
```json
[
  {
    "id": "mig-a1b2c3d4",
    "entityType": "product",
    "status": "RUNNING",
    "totalEntities": 10000,
    "processedEntities": 2500,
    "failedEntities": 3,
    "startedAt": "2024-12-28T10:00:00"
  },
  {
    "id": "mig-e5f6g7h8",
    "entityType": "user",
    "status": "COMPLETED",
    "totalEntities": 50000,
    "processedEntities": 50000,
    "failedEntities": 0,
    "startedAt": "2024-12-27T15:00:00",
    "completedAt": "2024-12-27T16:30:00"
  }
]
```

#### GET /jobs/{id}

Get migration progress.

**Response**:
```json
{
  "jobId": "mig-a1b2c3d4",
  "status": "RUNNING",
  "total": 10000,
  "processed": 7500,
  "failed": 12,
  "percentComplete": 75.0,
  "estimatedTimeRemaining": "PT15M"
}
```

#### POST /jobs/{id}/pause

Pause a running migration.

**Response**: `200 OK`

#### POST /jobs/{id}/resume

Resume a paused migration.

**Response**: `200 OK`

#### DELETE /jobs/{id}

Cancel a migration job.

**Response**: `204 No Content`

---

### AI Profile Endpoints

Base path: `/api/ai/profiles`

#### POST /

Create a new AI profile.

**Request Body**:
```json
{
  "userId": "user-123",
  "profileData": {
    "preferences": "technical content",
    "experience_level": "expert"
  },
  "version": 1
}
```

**Response**: `201 Created`
```json
{
  "id": "profile-456",
  "userId": "user-123",
  "status": "ACTIVE",
  "confidenceScore": 0.85,
  "version": 1,
  "createdAt": "2024-12-28T10:00:00"
}
```

#### GET /{id}

Get AI profile by ID.

**Response**: `200 OK`

#### GET /user/{userId}

Get AI profile by user ID.

**Response**: `200 OK`

#### GET /user/{userId}/latest

Get latest AI profile for a user.

**Response**: `200 OK`

#### GET /status/{status}

Get profiles by status (ACTIVE, INACTIVE, PENDING).

**Response**: `200 OK` with list of profiles

#### GET /status/{status}/page

Get profiles by status with pagination.

**Query Parameters**: Standard Spring Data pagination

**Response**: `200 OK` with paginated results

#### GET /confidence-score

Get profiles by confidence score range.

**Query Parameters**:
- `minScore`: Minimum score (0.0 - 1.0)
- `maxScore`: Maximum score (0.0 - 1.0)

**Response**: `200 OK`

#### GET /version/{version}

Get profiles by version.

**Response**: `200 OK`

#### GET /date-range

Get profiles by date range.

**Query Parameters**:
- `startDate`: Start date (ISO 8601)
- `endDate`: End date (ISO 8601)

**Response**: `200 OK`

#### PUT /{id}

Update AI profile.

**Request Body**: Same as create

**Response**: `200 OK`

#### PUT /user/{userId}

Update profile by user ID.

**Response**: `200 OK`

#### DELETE /{id}

Delete AI profile.

**Response**: `204 No Content`

#### DELETE /user/{userId}

Delete profile by user ID.

**Response**: `204 No Content`

---

### Compliance Endpoints

Base path: `/api/ai/compliance`

#### POST /check

Check compliance for a request.

**Request Body**:
```json
{
  "requestId": "req-789",
  "userId": "user-123",
  "content": "Patient data: John Doe, DOB: 1990-01-01",
  "regulations": ["GDPR", "HIPAA"],
  "operation": "store",
  "metadata": {
    "category": "medical"
  }
}
```

**Response**:
```json
{
  "requestId": "req-789",
  "userId": "user-123",
  "compliant": false,
  "violations": [
    {
      "regulation": "HIPAA",
      "reason": "Unencrypted PHI detected",
      "severity": "HIGH"
    }
  ],
  "recommendations": [
    "Encrypt patient data before storage",
    "Apply de-identification"
  ],
  "timestamp": "2024-12-28T10:00:00",
  "success": true
}
```

#### GET /health

Health check for compliance service.

**Response**:
```json
{
  "status": "UP",
  "service": "AIComplianceService",
  "timestamp": 1703779200000
}
```

---

### Security Endpoints

Base path: `/api/ai/security`

#### POST /analyze

Analyze request for security threats.

**Request Body**:
```json
{
  "requestId": "req-101",
  "userId": "user-456",
  "content": "User prompt or content to analyze",
  "operation": "generate",
  "ipAddress": "192.168.1.1",
  "userAgent": "Mozilla/5.0..."
}
```

**Response**:
```json
{
  "requestId": "req-101",
  "userId": "user-456",
  "secure": true,
  "threats": [],
  "riskLevel": "LOW",
  "recommendations": ["Content is safe to process"],
  "timestamp": "2024-12-28T10:00:00",
  "success": true
}
```

#### GET /events/{userId}

Get security events for a user.

**Response**:
```json
[
  {
    "eventId": "evt-789",
    "userId": "user-456",
    "eventType": "THREAT_DETECTED",
    "severity": "MEDIUM",
    "description": "Suspicious pattern detected",
    "timestamp": "2024-12-28T09:30:00"
  }
]
```

#### GET /events

Get all security events.

**Response**: List of security events

#### DELETE /events/{userId}

Clear security events for a user.

**Response**: `200 OK`

#### GET /stats

Get security statistics.

**Response**:
```json
{
  "totalRequests": 15234,
  "threatsDetected": 45,
  "threatRate": 0.003,
  "averageRiskLevel": "LOW"
}
```

#### GET /health

Health check for security service.

**Response**:
```json
{
  "status": "UP",
  "service": "AISecurityService",
  "timestamp": 1703779200000
}
```

---

## Configuration

### Enable/Disable Controllers

```yaml
ai:
  web:
    enabled: true
    controllers:
      advanced-rag: true       # Enable Advanced RAG endpoints
      profile: true            # Enable Profile endpoints
      compliance: true         # Enable Compliance endpoints
      security: true           # Enable Security endpoints
      audit: false             # Disable Audit endpoints
      monitoring: false        # Disable Monitoring endpoints
```

### Custom Base Path

```yaml
ai:
  web:
    base-path: /v1/ai  # Change from /api/ai to /v1/ai
```

**Result**: All endpoints accessible at `/v1/ai/*` instead of `/api/ai/*`

### CORS Configuration

```yaml
spring:
  web:
    cors:
      allowed-origins: "*"
      allowed-methods: GET,POST,PUT,DELETE
      allowed-headers: "*"
```

---

## Integration Examples

### Example 1: Frontend Search Integration

```javascript
// React/JavaScript example
async function searchDocuments(query) {
  const response = await fetch('/api/ai/advanced-rag/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      query: query,
      entityType: 'document',
      limit: 10,
      enableQueryExpansion: true,
      enableReRanking: true
    })
  });
  
  const data = await response.json();
  
  return data.documents.map(doc => ({
    id: doc.id,
    title: doc.title || doc.id,
    content: doc.content,
    score: doc.score
  }));
}
```

### Example 2: Migration Dashboard

```javascript
// Angular/TypeScript example
export class MigrationDashboard {
  
  async startMigration(entityType: string): Promise<MigrationJob> {
    const response = await this.http.post('/api/ai/migration/start', {
      entityType: entityType,
      batchSize: 1000,
      rateLimit: 200
    }).toPromise();
    
    return response;
  }
  
  async monitorProgress(jobId: string): Promise<MigrationProgress> {
    const response = await this.http.get(`/api/ai/migration/jobs/${jobId}`)
      .toPromise();
    
    return response;
  }
  
  async pauseMigration(jobId: string): Promise<void> {
    await this.http.post(`/api/ai/migration/jobs/${jobId}/pause`, {})
      .toPromise();
  }
}
```

### Example 3: Mobile App Integration

```swift
// iOS Swift example
struct AISearchService {
    
    func search(query: String) async throws -> [Document] {
        let url = URL(string: "https://api.example.com/api/ai/advanced-rag/search")!
        
        let request = AdvancedRAGRequest(
            query: query,
            entityType: "article",
            limit: 20,
            enableReRanking: true
        )
        
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try JSONEncoder().encode(request)
        
        let (data, _) = try await URLSession.shared.data(for: urlRequest)
        let response = try JSONDecoder().decode(AdvancedRAGResponse.self, from: data)
        
        return response.documents
    }
}
```

### Example 4: Python Client

```python
import requests

class AIWebClient:
    def __init__(self, base_url):
        self.base_url = base_url
    
    def search(self, query, entity_type="document", limit=10):
        """Perform advanced RAG search"""
        response = requests.post(
            f"{self.base_url}/api/ai/advanced-rag/search",
            json={
                "query": query,
                "entityType": entity_type,
                "limit": limit,
                "enableQueryExpansion": True,
                "enableReRanking": True
            }
        )
        response.raise_for_status()
        return response.json()
    
    def start_migration(self, entity_type, batch_size=500):
        """Start data migration"""
        response = requests.post(
            f"{self.base_url}/api/ai/migration/start",
            json={
                "entityType": entity_type,
                "batchSize": batch_size,
                "rateLimit": 100
            }
        )
        response.raise_for_status()
        return response.json()
    
    def get_migration_progress(self, job_id):
        """Get migration progress"""
        response = requests.get(
            f"{self.base_url}/api/ai/migration/jobs/{job_id}"
        )
        response.raise_for_status()
        return response.json()

# Usage
client = AIWebClient("http://localhost:8080")
results = client.search("machine learning tutorials")
print(f"Found {len(results['documents'])} results")
```

---

## Error Handling

### Standard Error Responses

All endpoints return consistent error formats:

```json
{
  "success": false,
  "errorMessage": "Detailed error description",
  "requestId": "req-123",
  "timestamp": "2024-12-28T10:00:00"
}
```

### HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Request successful |
| 201 | Created | Resource created |
| 204 | No Content | Resource deleted |
| 400 | Bad Request | Invalid request data |
| 404 | Not Found | Resource not found |
| 500 | Internal Server Error | Server error |

### Example Error Handling

```javascript
async function searchWithErrorHandling(query) {
  try {
    const response = await fetch('/api/ai/advanced-rag/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        query: query,
        entityType: 'document'
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      console.error('Search failed:', error.errorMessage);
      return [];
    }
    
    const data = await response.json();
    return data.documents;
    
  } catch (error) {
    console.error('Network error:', error);
    return [];
  }
}
```

---

## Security & Authentication

### Basic Authentication

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ai/*/health").permitAll()
                .requestMatchers("/api/ai/**").authenticated()
            )
            .httpBasic();
        
        return http.build();
    }
}
```

### JWT Authentication

```java
@Configuration
public class JWTSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ai/**").hasRole("AI_USER")
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt()
            );
        
        return http.build();
    }
}
```

### API Key Authentication

```java
@Component
public class APIKeyFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        String apiKey = request.getHeader("X-API-Key");
        
        if (!isValidAPIKey(apiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

---

## Testing

### Integration Testing

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MigrationControllerTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldStartMigration() {
        // Given
        MigrationRequestDTO request = MigrationRequestDTO.builder()
            .entityType("product")
            .batchSize(100)
            .build();
        
        // When
        ResponseEntity<MigrationJobDTO> response = restTemplate.postForEntity(
            "/api/ai/migration/start",
            request,
            MigrationJobDTO.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(MigrationStatus.RUNNING);
    }
}
```

### API Testing with cURL

```bash
# Test Advanced RAG
curl -X POST http://localhost:8080/api/ai/advanced-rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "test query",
    "entityType": "document",
    "limit": 5
  }'

# Test Migration
curl -X POST http://localhost:8080/api/ai/migration/start \
  -H "Content-Type: application/json" \
  -d '{
    "entityType": "product",
    "batchSize": 100
  }'

# Check health
curl http://localhost:8080/api/ai/advanced-rag/health
```

---

## Troubleshooting

### Issue: 404 Not Found on endpoints

**Cause**: Web module not enabled or controllers disabled

**Solution**:
```yaml
ai:
  web:
    enabled: true
    controllers:
      advanced-rag: true
```

### Issue: 500 Internal Server Error

**Cause**: Missing core dependencies

**Solution**: Verify core module is included
```xml
<dependency>
    <artifactId>ai-fabric-core</artifactId>
</dependency>
```

### Issue: CORS errors

**Solution**: Configure CORS
```yaml
spring:
  web:
    cors:
      allowed-origins: "http://localhost:3000"
      allowed-methods: "*"
```

---

## Best Practices

### ✅ DO

- **Enable only needed controllers** to reduce attack surface
- **Implement authentication** for production
- **Use pagination** for large result sets
- **Handle errors gracefully** in clients
- **Monitor health endpoints** regularly
- **Set appropriate rate limits** for migration
- **Use request IDs** for tracing

### ❌ DON'T

- Don't expose all endpoints publicly
- Don't skip input validation
- Don't ignore error responses
- Don't migrate without testing first
- Don't use default credentials in production
- Don't disable HTTPS in production

---

## API Versioning

### Path-Based Versioning

```yaml
ai:
  web:
    base-path: /v1/ai
```

**Result**: `/v1/ai/advanced-rag/search`, `/v1/ai/migration/start`, etc.

### Header-Based Versioning

```java
@RestController
@RequestMapping("/api/ai")
public class VersionedController {
    
    @PostMapping(value = "/search", headers = "API-Version=1")
    public ResponseEntity<?> searchV1() {
        // Version 1 implementation
    }
    
    @PostMapping(value = "/search", headers = "API-Version=2")
    public ResponseEntity<?> searchV2() {
        // Version 2 implementation
    }
}
```

---

## Monitoring

### Health Endpoints

All services expose health endpoints:

```bash
# Check all services
curl http://localhost:8080/api/ai/advanced-rag/health
curl http://localhost:8080/api/ai/compliance/health
curl http://localhost:8080/api/ai/security/health
```

### Actuator Integration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

**Access**: `GET /actuator/health`

---

## FAQ

**Q: Can I disable specific controllers?**
A: Yes, use `ai.web.controllers.*` properties.

**Q: How do I secure the API?**
A: Implement Spring Security with authentication/authorization.

**Q: Can I customize endpoint paths?**
A: Yes, use `ai.web.base-path` or extend controllers.

**Q: What's the performance impact?**
A: Minimal. Controllers are thin wrappers around services.

**Q: Can I use this with microservices?**
A: Yes. Perfect for exposing AI features via REST.

**Q: Are WebSocket endpoints supported?**
A: Not currently. REST only.

**Q: How do I handle large payloads?**
A: Use pagination and streaming where appropriate.

---

## Version Information

- **Module Version**: 1.0.0
- **Minimum Java**: 17
- **Spring Boot**: 3.x
- **Dependencies**: ai-fabric-core (required), ai-infrastructure-migration-core (optional)

---

## Support & Resources

- **Source Code**: `com.ai.infrastructure.web`
- **Controllers**: `controller/` and `migration/`
- **Configuration**: `AIWebProperties.java`
- **Tests**: `src/test/java`

---

*This guide reflects the actual implementation in the codebase. For underlying services, refer to the Core module documentation.*

