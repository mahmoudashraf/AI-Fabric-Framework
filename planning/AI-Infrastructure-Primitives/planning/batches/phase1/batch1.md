# BATCH 1 — RAG System Foundation & @AICapable Annotations

## 🎯 **Batch Goal**
Implement the foundational RAG (Retrieval-Augmented Generation) system with Pinecone integration and create the @AICapable annotation framework that enables any entity to become AI-capable with a single annotation.

## 📋 **Tickets in This Batch**
- **P1.1-A**: RAG System Foundation with Pinecone Integration
- **P1.1-B**: @AICapable Annotation Framework

## 🏗️ **Architecture Overview**

### **RAG System Flow**
```
1. Document Indexing → 2. Vector Storage → 3. Semantic Search → 4. AI Response
     ↓                    ↓                    ↓                    ↓
Entity Data → Pinecone → Embeddings → Search Results → AI Answer
```

### **@AICapable Annotation Flow**
```
@Entity @AICapable → Annotation Processor → AI Service Generation → Auto AI Features
```

## 🔧 **Implementation Details**

### **Backend Implementation**

#### **1. RAG System Foundation**
```java
// Extend existing AIService with RAG capabilities
@Service
public class AIService {
    
    @Autowired
    private PineconeClient pineconeClient;
    
    @Autowired
    private OpenAIEmbeddingService embeddingService;
    
    public void indexDocument(String content, String entityType, UUID entityId) {
        // Create embedding
        List<Double> embedding = embeddingService.createEmbedding(content);
        
        // Store in Pinecone
        pineconeClient.upsert(
            entityId.toString(),
            embedding,
            Map.of("content", content, "entityType", entityType)
        );
    }
    
    public List<SearchResult> semanticSearch(String query, String entityType) {
        // Create query embedding
        List<Double> queryEmbedding = embeddingService.createEmbedding(query);
        
        // Search Pinecone
        return pineconeClient.query(queryEmbedding, entityType, 10);
    }
}
```

#### **2. @AICapable Annotation Framework**
```java
// Custom annotation for AI capabilities
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AICapable {
    String[] features() default {"rag", "search", "recommendations"};
    String description() default "";
}

// Annotation processor
@Component
public class AICapableProcessor {
    
    @EventListener
    public void handleEntitySave(EntitySaveEvent event) {
        if (event.getEntity().getClass().isAnnotationPresent(AICapable.class)) {
            // Auto-generate AI features
            generateAIFeatures(event.getEntity());
        }
    }
    
    private void generateAIFeatures(Object entity) {
        // Generate RAG service
        // Create search endpoints
        // Enable AI recommendations
    }
}
```

### **Frontend Implementation**

#### **1. AI Search Component**
```typescript
// Reuse existing search components
export const AISearchComponent = () => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    
    const { data: searchResults, isLoading } = useAISearch(query);
    
    return (
        <MainCard>
            <SearchInput 
                value={query}
                onChange={setQuery}
                placeholder="Ask AI about your data..."
            />
            <SearchResults 
                results={searchResults}
                loading={isLoading}
            />
        </MainCard>
    );
};
```

#### **2. AI Health Dashboard**
```typescript
// Adapt existing dashboard components
export const AIHealthDashboard = () => {
    const { data: health } = useAIHealth();
    
    return (
        <LatestCustomerTableCard>
            <Table>
                <TableHead>
                    <TableRow>
                        <TableCell>AI Service</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Response Time</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {health?.services.map(service => (
                        <TableRow key={service.name}>
                            <TableCell>{service.name}</TableCell>
                            <TableCell>
                                <Chip 
                                    label={service.status}
                                    color={service.status === 'healthy' ? 'success' : 'error'}
                                />
                            </TableCell>
                            <TableCell>{service.responseTime}ms</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </LatestCustomerTableCard>
    );
};
```

## 🧪 **Testing Strategy**

### **Backend Tests**
```java
@SpringBootTest
class AIServiceTest {
    
    @Test
    void shouldIndexDocumentSuccessfully() {
        // Given
        String content = "Test product description";
        String entityType = "Product";
        UUID entityId = UUID.randomUUID();
        
        // When
        aiService.indexDocument(content, entityType, entityId);
        
        // Then
        verify(pineconeClient).upsert(any(), any(), any());
    }
    
    @Test
    void shouldPerformSemanticSearch() {
        // Given
        String query = "luxury watches";
        
        // When
        List<SearchResult> results = aiService.semanticSearch(query, "Product");
        
        // Then
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getScore()).isGreaterThan(0.8);
    }
}
```

### **Frontend Tests**
```typescript
describe('AISearchComponent', () => {
    it('should display search results', async () => {
        render(<AISearchComponent />);
        
        const searchInput = screen.getByPlaceholderText('Ask AI about your data...');
        fireEvent.change(searchInput, { target: { value: 'luxury watches' } });
        
        await waitFor(() => {
            expect(screen.getByText('Rolex Submariner')).toBeInTheDocument();
        });
    });
});
```

## 📊 **Success Metrics**

### **Technical Metrics**
- [ ] RAG system indexes documents successfully
- [ ] Semantic search returns relevant results (≥85% accuracy)
- [ ] AI health endpoint shows system status
- [ ] @AICapable annotation processes correctly
- [ ] Test coverage ≥80% for AI services

### **Performance Metrics**
- [ ] Search response time <200ms
- [ ] Document indexing <500ms
- [ ] System uptime ≥99.5%
- [ ] Pinecone API calls <100ms

## 🚀 **Deployment Plan**

### **Phase 1: Backend Deployment**
1. Add Pinecone dependency to `pom.xml`
2. Create database migrations for AI tables
3. Implement RAG system in `AIService`
4. Create @AICapable annotation framework
5. Add AI endpoints to controllers

### **Phase 2: Frontend Integration**
1. Create AI search components
2. Implement AI health dashboard
3. Add AI configuration management
4. Create AI hooks for React Query
5. Add AI routes to Next.js

### **Phase 3: Testing & Validation**
1. Run comprehensive test suite
2. Perform integration testing
3. Validate AI search accuracy
4. Test annotation processing
5. Verify system health monitoring

## 🔄 **Rollback Plan**

### **If RAG System Fails**
1. Disable AI features via configuration
2. Revert to original `AIService`
3. Remove Pinecone integration
4. Restore previous search functionality

### **If Annotation Processing Fails**
1. Disable @AICapable annotation processing
2. Revert to manual AI service creation
3. Remove annotation processor
4. Restore previous entity handling

## 📝 **Deliverables**

### **Backend Files**
- `AIService.java` (extended with RAG)
- `AICapable.java` (annotation)
- `AICapableProcessor.java` (processor)
- `AIKnowledgeBase.java` (entity)
- `AIController.java` (endpoints)
- `V001__ai_knowledge_base.yaml` (migration)

### **Frontend Files**
- `AISearchComponent.tsx` (search UI)
- `AIHealthDashboard.tsx` (health monitoring)
- `useAISearch.ts` (React Query hook)
- `useAIHealth.ts` (health hook)
- `/ai/search/page.tsx` (search page)
- `/ai/health/page.tsx` (health page)

### **Configuration Files**
- `application.yml` (AI configuration)
- `pom.xml` (Pinecone dependency)
- `package.json` (AI dependencies)

## 🎉 **Expected Outcomes**

After this batch, developers will be able to:

1. **Add AI to any entity** with `@AICapable` annotation
2. **Search their data semantically** using natural language
3. **Monitor AI system health** in real-time
4. **Configure AI features** through admin interface
5. **Get AI recommendations** for their data

This creates the foundation for all future AI features and makes the platform truly AI-enabled from day one!