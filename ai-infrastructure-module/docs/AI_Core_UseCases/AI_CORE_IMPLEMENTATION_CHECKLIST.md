# ✅ AI-Core Use Cases - Implementation Checklist

**Document Purpose:** Step-by-step checklist for building any AI-core use case app  
**Estimated Time:** 2-3 weeks per app  
**Template For:** All 10 use cases

---

## 📋 Pre-Implementation (Days 1-2)

### Planning Phase
- [ ] **Choose Use Case**
  - [ ] Review AI_CORE_REAL_LIFE_USE_CASES.md section
  - [ ] Understand architecture diagram
  - [ ] Verify alignment with team skills
  - [ ] Confirm business requirements

- [ ] **Define Success Metrics**
  - [ ] Throughput targets (ops/sec)
  - [ ] Latency targets (p50, p95, p99)
  - [ ] Accuracy targets (if applicable)
  - [ ] Cost targets (API spend)
  - [ ] Document in README

- [ ] **Setup Development Environment**
  - [ ] Java 21+ installed and verified
  - [ ] Maven 3.8+ installed
  - [ ] Node.js 18+ installed (for frontend)
  - [ ] Docker installed (optional, for Postgres)
  - [ ] IDE configured (IntelliJ, VS Code)

- [ ] **Clone & Branch**
  - [ ] Clone TheBaseRepo
  - [ ] Create feature branch: `git checkout -b feature/use-case-{name}`
  - [ ] Pull latest changes from main

### Database Planning
- [ ] **Analyze Schema**
  - [ ] Document all tables
  - [ ] Identify relationships
  - [ ] Plan indexes
  - [ ] Estimate data volume
  - [ ] Create migration scripts

- [ ] **Choose Storage**
  - [ ] Development: H2 (embedded)
  - [ ] Testing: H2 (in-memory)
  - [ ] Production: PostgreSQL or MySQL
  - [ ] Configure connection strings

---

## 🏗️ Phase 1: Backend Foundation (Days 3-5)

### Maven Project Setup
- [ ] **Create Project Structure**
  ```
  use-cases/{app-name}/
  ├── pom.xml
  ├── src/main/java/com/usecase/{app}/
  │   ├── entity/
  │   ├── repository/
  │   ├── service/
  │   ├── controller/
  │   ├── dto/
  │   ├── config/
  │   └── {AppName}Application.java
  ├── src/main/resources/
  │   ├── application.yml
  │   ├── application-dev.yml
  │   ├── application-test.yml
  │   └── schema.sql
  ├── src/test/java/
  └── README.md
  ```

- [ ] **Create pom.xml**
  - [ ] Copy from template in ai-infrastructure-module
  - [ ] Include ai-infrastructure-core dependency
  - [ ] Include ai-infrastructure-vector-lucene if search needed
  - [ ] Include test dependencies (JUnit 5, Mockito, Testcontainers)
  - [ ] Include H2 database dependency
  - [ ] Run `mvn clean install` to verify

### Database Layer
- [ ] **Create H2 Schema**
  - [ ] Write schema.sql with all tables
  - [ ] Create indexes for performance
  - [ ] Add constraints and relationships
  - [ ] Test schema creation with H2

- [ ] **Create JPA Entities**
  - [ ] One entity per table
  - [ ] Use @Entity, @Table annotations
  - [ ] Define all fields with proper types
  - [ ] Add @Id and @GeneratedValue
  - [ ] Define relationships (@OneToMany, @ManyToOne, etc.)
  - [ ] Add @CreatedDate, @LastModifiedDate if auditing needed
  - [ ] Example:
    ```java
    @Entity
    @Table(name = "products", indexes = {
        @Index(name = "idx_embedding", columnList = "embedding")
    })
    public class Product {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        
        @Column(nullable = false)
        private String title;
        
        @Lob
        private String description;
        
        @Column(columnDefinition = "BLOB")
        private byte[] embedding;  // ONNX or OpenAI vector
        
        @CreatedDate
        private LocalDateTime createdAt;
    }
    ```

- [ ] **Create Spring Data Repositories**
  - [ ] Extend JpaRepository<Entity, ID>
  - [ ] Define custom query methods if needed
  - [ ] Add @Query annotations for complex queries
  - [ ] Example:
    ```java
    @Repository
    public interface ProductRepository extends JpaRepository<Product, UUID> {
        List<Product> findByCategory(String category);
        
        @Query("SELECT p FROM Product p WHERE p.price BETWEEN :min AND :max")
        List<Product> findByPriceRange(@Param("min") BigDecimal min, 
                                       @Param("max") BigDecimal max);
    }
    ```

- [ ] **Test Database Layer**
  - [ ] Write @DataJpaTest for repositories
  - [ ] Test CRUD operations
  - [ ] Test custom queries
  - [ ] Verify indexes work
  - [ ] Run and pass all tests

### Configuration
- [ ] **Create Application Config**
  - [ ] application.yml (base config)
  - [ ] application-dev.yml (local development)
  - [ ] application-test.yml (unit tests)
  - [ ] application-prod.yml (production, if needed)
  - [ ] Example:
    ```yaml
    # application-dev.yml
    spring:
      datasource:
        url: jdbc:h2:mem:testdb
        driver-class-name: org.h2.Driver
      jpa:
        database-platform: org.hibernate.dialect.H2Dialect
        hibernate:
          ddl-auto: create-drop
      h2:
        console:
          enabled: true
    
    ai:
      embedding:
        provider: onnx  # or openai
        model-path: ./models/embeddings/model.onnx
      search:
        provider: lucene
        index-path: ./data/lucene-index
      openai:
        api-key: ${OPENAI_API_KEY}
        model: gpt-4-turbo
    ```

- [ ] **Create Configuration Classes**
  - [ ] Database configuration (if custom)
  - [ ] AI provider configuration
  - [ ] Search/Lucene configuration
  - [ ] Caching configuration (if needed)
  - [ ] Example:
    ```java
    @Configuration
    public class AIConfig {
        @Bean
        public VectorDatabaseService vectorDb() {
            return new LuceneVectorDatabaseService();
        }
        
        @Bean
        public EmbeddingService embeddingService() {
            return new ONNXEmbeddingService();
        }
    }
    ```

---

## 🤖 Phase 2: AI Services (Days 6-10)

### Embedding Service
- [ ] **Setup Embedding Provider**
  - [ ] If ONNX:
    - [ ] Download model from scripts/download-onnx-model.sh
    - [ ] Place in models/embeddings/ directory
    - [ ] Create ONNXEmbeddingService implementation
  - [ ] If OpenAI:
    - [ ] Use AIEmbeddingService from ai-infrastructure-core
    - [ ] Configure API key
    - [ ] Test API connection

- [ ] **Create Embedding Service**
  - [ ] Implement EmbeddingService interface
  - [ ] Method: `embedText(String text) → byte[]`
  - [ ] Add error handling
  - [ ] Add caching (@Cacheable)
  - [ ] Example:
    ```java
    @Service
    @RequiredArgsConstructor
    public class EmbeddingService {
        private final ONNXEmbeddingService onnxService;
        
        @Cacheable(value = "embeddings", key = "#text")
        public byte[] generateEmbedding(String text) {
            return onnxService.embed(text);
        }
    }
    ```

- [ ] **Test Embedding Generation**
  - [ ] Verify vector dimensions (usually 384 or 768)
  - [ ] Check for deterministic output (same input = same output)
  - [ ] Measure latency (<100ms per embedding)
  - [ ] Test batch embedding (if applicable)

### Search Service (Lucene)
- [ ] **Setup Lucene Indexing**
  - [ ] Create LuceneSearchService
  - [ ] Configure index directory
  - [ ] Define indexed fields (title, description, metadata)
  - [ ] Plan field types (STORED, INDEXED, ANALYZED, etc.)

- [ ] **Implement Semantic Search**
  - [ ] Index documents with embeddings
  - [ ] Create vector similarity scoring
  - [ ] Implement full-text search (BM25)
  - [ ] Combine hybrid ranking if applicable
  - [ ] Example:
    ```java
    @Service
    @RequiredArgsConstructor
    public class SearchService {
        private final LuceneIndexManager indexManager;
        private final EmbeddingService embeddingService;
        
        public List<SearchResult> search(String query, int topK) {
            // 1. Generate embedding for query
            byte[] queryVector = embeddingService.generateEmbedding(query);
            
            // 2. Search Lucene index
            List<Document> results = indexManager.searchBySemantic(queryVector, topK);
            
            // 3. Rank and return
            return results.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());
        }
    }
    ```

- [ ] **Test Search**
  - [ ] Index test documents
  - [ ] Verify relevance of results
  - [ ] Measure search latency
  - [ ] Test edge cases (empty index, special characters)

### Classification/Generation Service (if using OpenAI)
- [ ] **Setup OpenAI Integration**
  - [ ] Use AIGenerationRequest/Response from ai-infrastructure-core
  - [ ] Configure model (gpt-4-turbo, gpt-4o-mini, etc.)
  - [ ] Setup API key from environment

- [ ] **Create Classification Service** (if needed)
  - [ ] Define classification categories
  - [ ] Create prompt templates
  - [ ] Implement confidence scoring
  - [ ] Add prompt versioning
  - [ ] Example:
    ```java
    @Service
    @RequiredArgsConstructor
    public class ClassificationService {
        private final AIProviderManager providerManager;
        
        public Classification classify(String text) {
            String prompt = String.format(
                "Classify this text into categories: %s\n\nText: %s",
                String.join(", ", CATEGORIES), text
            );
            
            AIGenerationResponse response = providerManager
                .generateContent(new AIGenerationRequest(prompt));
            
            return parseResponse(response.getContent());
        }
    }
    ```

- [ ] **Test Classification**
  - [ ] Verify accuracy on test dataset
  - [ ] Check confidence scores
  - [ ] Measure latency
  - [ ] Test error handling (rate limits, API down)

---

## 🔌 Phase 3: REST API Layer (Days 11-13)

### Create Controllers
- [ ] **Ingestion Endpoints**
  - [ ] POST /api/{entity} - Create/index single item
  - [ ] POST /api/{entity}/batch - Batch ingestion
  - [ ] PUT /api/{entity}/{id} - Update
  - [ ] DELETE /api/{entity}/{id} - Delete
  - [ ] Example:
    ```java
    @RestController
    @RequestMapping("/api/products")
    @RequiredArgsConstructor
    public class ProductController {
        private final ProductService productService;
        
        @PostMapping
        public ResponseEntity<ProductDTO> create(@RequestBody CreateProductRequest req) {
            Product product = productService.create(req);
            return ResponseEntity.status(201).body(toDTO(product));
        }
        
        @GetMapping("/{id}")
        public ResponseEntity<ProductDTO> getById(@PathVariable UUID id) {
            return productService.findById(id)
                .map(p -> ResponseEntity.ok(toDTO(p)))
                .orElse(ResponseEntity.notFound().build());
        }
    }
    ```

- [ ] **Search Endpoints**
  - [ ] GET /api/{entity}/search?q=... - Semantic search
  - [ ] GET /api/{entity}/search/advanced - Advanced filtering
  - [ ] POST /api/{entity}/search - Complex queries in body

- [ ] **Analysis Endpoints** (if applicable)
  - [ ] GET /api/{entity}/{id}/analyze - Get analysis/classification
  - [ ] POST /api/batch-analyze - Bulk analysis

### Request/Response DTOs
- [ ] **Create DTOs**
  - [ ] Request DTOs (CreateXxxRequest, UpdateXxxRequest)
  - [ ] Response DTOs (XxxDTO, SearchResultDTO)
  - [ ] Error DTOs (ErrorResponse, ValidationError)
  - [ ] Include validation annotations (@NotNull, @Min, etc.)

- [ ] **Add OpenAPI/Swagger Documentation**
  - [ ] @Operation annotations
  - [ ] @ApiResponse annotations
  - [ ] @Schema for DTOs
  - [ ] Example:
    ```java
    @PostMapping
    @Operation(summary = "Create product", description = "Create new product and index")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    public ResponseEntity<ProductDTO> create(
        @RequestBody CreateProductRequest req) { ... }
    ```

### Error Handling
- [ ] **Global Exception Handler**
  - [ ] @ControllerAdvice class
  - [ ] Handle specific exceptions
  - [ ] Return proper HTTP status codes
  - [ ] Example:
    ```java
    @ControllerAdvice
    public class GlobalExceptionHandler {
        @ExceptionHandler(EntityNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException e) {
            return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        }
    }
    ```

- [ ] **Input Validation**
  - [ ] Add @Validated on controller
  - [ ] Validation annotations on DTOs
  - [ ] Custom validators if needed

### Integration Tests
- [ ] **Test All Endpoints**
  - [ ] Create @WebMvcTest or @SpringBootTest test class
  - [ ] Test successful operations (200, 201)
  - [ ] Test error cases (400, 404, 500)
  - [ ] Test with real database (H2)
  - [ ] Example:
    ```java
    @SpringBootTest
    @AutoConfigureMockMvc
    class ProductControllerTest {
        @Autowired
        private MockMvc mockMvc;
        
        @Test
        void testCreateProduct() throws Exception {
            mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Test\", ...}"))
                .andExpect(status().isCreated());
        }
    }
    ```

---

## 🎨 Phase 4: Frontend (Days 14-17)

### React Project Setup
- [ ] **Create Frontend App**
  - [ ] Use create-react-app or Next.js template
  - [ ] Install dependencies (React Query, Material-UI, etc.)
  - [ ] Setup TypeScript
  - [ ] Configure Tailwind or Material-UI

- [ ] **API Client Setup**
  - [ ] Create axios/fetch wrapper
  - [ ] Setup base URL (localhost:8080 for dev)
  - [ ] Add error interceptors
  - [ ] Add retry logic
  - [ ] Example:
    ```typescript
    const api = axios.create({
      baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080/api'
    });
    
    api.interceptors.response.use(
      response => response,
      error => {
        if (error.response?.status === 401) {
          // Handle unauthorized
        }
        return Promise.reject(error);
      }
    );
    ```

### Key Pages/Components
- [ ] **List/Search Page**
  - [ ] Display results from API
  - [ ] Add search input
  - [ ] Implement pagination
  - [ ] Add sorting/filtering
  - [ ] Use React Query for data fetching
  - [ ] Example:
    ```typescript
    const SearchPage = () => {
      const [query, setQuery] = useState('');
      const { data, isLoading, error } = useQuery({
        queryKey: ['search', query],
        queryFn: () => api.get('/search', { params: { q: query } }),
        enabled: query.length > 0
      });
      
      return (
        <div>
          <input 
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Search..."
          />
          {isLoading && <Spinner />}
          {data?.data.map(item => <ResultCard key={item.id} item={item} />)}
        </div>
      );
    };
    ```

- [ ] **Create/Upload Page**
  - [ ] Form with all required fields
  - [ ] File upload if applicable
  - [ ] Form validation
  - [ ] Loading/success states
  - [ ] Error messages

- [ ] **Detail/Analysis Page**
  - [ ] Display item details
  - [ ] Show analysis/classification results
  - [ ] Visualizations (charts, heatmaps)
  - [ ] Related items (recommendations, similar)

- [ ] **Dashboard** (if analytics)
  - [ ] KPI cards (throughput, accuracy, etc.)
  - [ ] Charts (trends, distributions)
  - [ ] Real-time updates (WebSocket or polling)

### Styling & UX
- [ ] **Apply Design System**
  - [ ] Use Material-UI or Tailwind
  - [ ] Consistent colors and typography
  - [ ] Responsive design (mobile-first)
  - [ ] Dark mode support (optional)

- [ ] **Error Handling & Loading States**
  - [ ] Show loading spinners
  - [ ] Display error messages
  - [ ] Retry buttons
  - [ ] Fallback UI

### Testing
- [ ] **Component Tests**
  - [ ] Unit tests for components
  - [ ] Integration tests with mocked API
  - [ ] E2E tests with Cypress/Playwright

---

## 📊 Phase 5: Testing & Optimization (Days 18-19)

### Unit Tests
- [ ] **Backend Tests**
  - [ ] Repository tests (using @DataJpaTest)
  - [ ] Service tests (using Mockito)
  - [ ] Controller tests (using @WebMvcTest)
  - [ ] Target: >80% code coverage
  - [ ] Run: `mvn test`

- [ ] **Frontend Tests**
  - [ ] Component tests (using React Testing Library)
  - [ ] Hook tests
  - [ ] Utility function tests
  - [ ] Run: `npm test`

### Performance Testing
- [ ] **Measure Baseline**
  - [ ] Embedding generation latency
  - [ ] Search latency (different query complexities)
  - [ ] API response times
  - [ ] Database query times
  - [ ] Document in performance-baseline.md

- [ ] **Load Testing**
  - [ ] Use JMeter or Gatling for backend
  - [ ] Simulate expected throughput (2x)
  - [ ] Measure latency percentiles (p50, p95, p99)
  - [ ] Identify bottlenecks
  - [ ] Example:
    ```bash
    # Install JMeter
    brew install jmeter
    
    # Create test plan or use existing
    jmeter -n -t test-plan.jmx -l results.jtl -j jmeter.log
    ```

- [ ] **Database Optimization**
  - [ ] Verify indexes are being used (EXPLAIN PLAN)
  - [ ] Add missing indexes
  - [ ] Optimize queries (N+1, eager loading)
  - [ ] Test with realistic data volume

- [ ] **Caching Implementation**
  - [ ] Add @Cacheable for expensive operations
  - [ ] Configure cache expiration (TTL)
  - [ ] Monitor cache hit rates
  - [ ] Example:
    ```java
    @Cacheable(value = "products", key = "#id", 
               cacheManager = "cacheManager",
               unless = "#result == null")
    public Product findById(UUID id) { ... }
    ```

### Accuracy Testing
- [ ] **Create Test Dataset**
  - [ ] Prepare known test cases
  - [ ] Define expected vs actual results
  - [ ] Document edge cases

- [ ] **Measure Accuracy** (if applicable)
  - [ ] Classification accuracy: (correct / total) * 100
  - [ ] Search relevance: use nDCG or MRR metrics
  - [ ] Entity extraction: precision/recall/F1
  - [ ] Document results

---

## 🔒 Phase 6: Security & Compliance (Day 20)

### Data Security
- [ ] **PII/PHI Protection** (if applicable)
  - [ ] Identify sensitive fields
  - [ ] Implement encryption at rest (if needed)
  - [ ] Add PII detection service
  - [ ] Implement redaction/masking
  - [ ] Document retention policies

- [ ] **Database Security**
  - [ ] Use parameterized queries (always, prevents SQL injection)
  - [ ] Setup database user with minimal permissions
  - [ ] Enable connection encryption (if not local)
  - [ ] Backup strategy documented

- [ ] **API Security**
  - [ ] Add CORS configuration (if frontend separate)
  - [ ] Setup authentication/authorization (if needed)
  - [ ] Add rate limiting (if public API)
  - [ ] Use HTTPS in production
  - [ ] Example:
    ```java
    @Configuration
    public class SecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.cors().and()
                .csrf().disable()
                .authorizeRequests()
                    .antMatchers("/swagger-ui/**").permitAll()
                    .anyRequest().authenticated();
            return http.build();
        }
    }
    ```

### Audit & Logging
- [ ] **Add Audit Logging**
  - [ ] Log all data modifications
  - [ ] Log search queries (for analysis)
  - [ ] Log API access (if needed)
  - [ ] Include timestamp and user ID
  - [ ] Store in immutable log table

- [ ] **Error Logging**
  - [ ] Log errors with stack traces (DEBUG level)
  - [ ] Log warnings for recoverable errors (WARN level)
  - [ ] Don't log sensitive data
  - [ ] Use SLF4J

### Documentation
- [ ] **Deployment Guide**
  - [ ] Docker build and run instructions
  - [ ] Environment variables needed
  - [ ] Database setup steps
  - [ ] How to upgrade schema

- [ ] **API Documentation**
  - [ ] Swagger/OpenAPI available at /swagger-ui.html
  - [ ] All endpoints documented
  - [ ] Example requests/responses

- [ ] **README.md**
  - [ ] What the app does
  - [ ] Quick start (local setup)
  - [ ] Architecture overview
  - [ ] Technology stack
  - [ ] KPIs and success metrics
  - [ ] Troubleshooting section

---

## 🚀 Phase 7: Deployment (Days 21)

### Docker & Containerization
- [ ] **Create Dockerfile**
  - [ ] Multi-stage build (keep image small)
  - [ ] Use eclipse-temurin:21-jre-alpine as base
  - [ ] Copy JAR and run
  - [ ] Example:
    ```dockerfile
    FROM eclipse-temurin:21-jre-alpine
    WORKDIR /app
    COPY target/*.jar app.jar
    EXPOSE 8080
    ENTRYPOINT ["java", "-jar", "app.jar"]
    ```

- [ ] **Create docker-compose.yml** (for local testing)
  - [ ] Backend service
  - [ ] Frontend service
  - [ ] PostgreSQL (optional, for production)
  - [ ] Adminer/pgAdmin (for debugging)

- [ ] **Build & Test**
  - [ ] `docker build -t use-case-app:latest .`
  - [ ] `docker run -p 8080:8080 use-case-app:latest`
  - [ ] Test API at localhost:8080/swagger-ui.html

### Kubernetes (if applicable)
- [ ] **Create Kubernetes Manifests**
  - [ ] Deployment.yaml (app definition)
  - [ ] Service.yaml (expose port)
  - [ ] ConfigMap.yaml (configuration)
  - [ ] Secret.yaml (API keys, passwords - *never commit*)
  - [ ] PersistentVolume.yaml (if needed for H2/Lucene)

- [ ] **Deploy to Cluster**
  - [ ] `kubectl apply -f deployment.yaml`
  - [ ] Verify pods are running: `kubectl get pods`
  - [ ] Check logs: `kubectl logs deployment/use-case-app`

### Monitoring & Observability
- [ ] **Add Metrics**
  - [ ] Use Micrometer for Spring Boot metrics
  - [ ] Export to Prometheus
  - [ ] Setup Grafana dashboards
  - [ ] Track: throughput, latency, errors, cache hit rate

- [ ] **Logging**
  - [ ] Configure JSON logging (for log aggregation)
  - [ ] Send logs to ELK stack or CloudWatch
  - [ ] Setup log levels (INFO in prod, DEBUG in dev)

- [ ] **Health Checks**
  - [ ] Implement /health endpoint
  - [ ] Check database connectivity
  - [ ] Check API dependencies (OpenAI, etc.)
  - [ ] Example:
    ```java
    @Component
    public class CustomHealthIndicator extends AbstractHealthIndicator {
        @Override
        protected void doHealthCheck(Health.Builder builder) {
            // Check database
            // Check OpenAI API
            builder.up();
        }
    }
    ```

---

## ✅ Final Checklist

### Pre-Launch
- [ ] All tests passing locally
- [ ] 80%+ code coverage
- [ ] Performance targets met (latency, throughput)
- [ ] Security review completed
- [ ] Documentation complete
- [ ] README with quick start
- [ ] Changelog updated

### Staging Deployment
- [ ] Deployed to staging environment
- [ ] Smoke tests passing
- [ ] Load testing completed (2x expected)
- [ ] Monitoring configured
- [ ] Alert rules set

### Production Launch
- [ ] Data migration plan (if needed)
- [ ] Rollback plan documented
- [ ] Team trained on deployment
- [ ] On-call rotation scheduled
- [ ] Customer communication ready
- [ ] Launch! 🚀

### Post-Launch
- [ ] Monitor metrics for 24 hours
- [ ] Watch for errors/anomalies
- [ ] Collect user feedback
- [ ] Plan iteration/improvements
- [ ] Document lessons learned

---

## 📈 Success Metrics Template

Copy this to your app's README:

```markdown
## Success Metrics

### Functional
- [ ] All CRUD operations working
- [ ] Search relevance >80%
- [ ] Classification accuracy >90% (if applicable)
- [ ] <1% error rate on production

### Performance
- [ ] Embedding latency: <100ms
- [ ] Search latency: <500ms
- [ ] API response time: <200ms (p95)
- [ ] Throughput: [YOUR_TARGET] ops/sec

### Operational
- [ ] Deployment time: <10 minutes
- [ ] Mean time to recovery: <5 minutes
- [ ] Uptime: >99.5%
- [ ] Cost: <$[YOUR_BUDGET] per month

### Business
- [ ] User adoption: >50%
- [ ] Daily active users: [TARGET]
- [ ] Feature usage: >30% of users
- [ ] Revenue: $[TARGET] per month
```

---

## 🆘 Troubleshooting

### Common Issues

**Problem: ONNX model won't load**
```bash
# Check model file exists
ls -la models/embeddings/model.onnx

# Verify Java can read it
file models/embeddings/model.onnx
```

**Problem: Lucene index corrupted**
```bash
# Delete and rebuild
rm -rf data/lucene-vector-index
mvn test  # will recreate index
```

**Problem: Tests fail with "database is locked"**
```
# H2 concurrency issue - use H2 console
# Set: MODE=POSTGRESQL for better compatibility
```

**Problem: OpenAI API rate limited**
```
# Add retry logic with exponential backoff
# Implement circuit breaker pattern
# Check API usage in dashboard
```

---

## 📞 Support

**Questions?** Check:
1. Full documentation: `AI_CORE_REAL_LIFE_USE_CASES.md`
2. AI module docs: `ai-infrastructure-module/docs/`
3. Spring Boot guides: https://spring.io/guides
4. Ask the team: Check team Slack/Discord

---

**Last Updated:** November 2025  
**Status:** Ready for Use  
**Template Version:** 1.0





