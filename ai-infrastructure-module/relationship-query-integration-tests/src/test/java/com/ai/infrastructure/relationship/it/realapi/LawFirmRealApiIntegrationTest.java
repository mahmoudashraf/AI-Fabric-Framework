package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.relationship.it.RelationshipQueryIntegrationTestApplication;
import com.ai.infrastructure.relationship.it.api.RelationshipQueryRequest;
import com.ai.infrastructure.relationship.it.entity.DocumentEntity;
import com.ai.infrastructure.relationship.it.entity.UserEntity;
import com.ai.infrastructure.relationship.it.repository.DocumentRepository;
import com.ai.infrastructure.relationship.it.repository.UserRepository;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.it.support.RelationshipQueryPlanFixtures;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("realapi")
class LawFirmRealApiIntegrationTest {

    private static final String QUERY = "Find all contracts related to John Smith in Q4 2023";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired(required = false)
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private QueryCache queryCache;

    private String q4ContractId;

    @BeforeEach
    void setUp() {
        queryCache.putPlan(QueryCache.hash(QUERY), RelationshipQueryPlanFixtures.planFor(QUERY));
        searchableEntityRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        if (vectorDatabaseService != null) {
            try {
                vectorDatabaseService.clearVectors();
            } catch (Exception ignored) {
            }
        }
        seedLawFirmData();
    }

    @Test
    void shouldReturnContractsForJohnSmithQ4() {
        RelationshipQueryRequest request = new RelationshipQueryRequest();
        request.setQuery(QUERY);
        request.setEntityTypes(List.of("document"));
        request.setReturnMode(ReturnMode.FULL);
        request.setLimit(5);

        ResponseEntity<RAGResponse> response = restTemplate.postForEntity(
            "/api/relationship-query/execute",
            request,
            RAGResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        RAGResponse rag = response.getBody();
        assertThat(rag.getDocuments()).isNotEmpty();
        assertThat(rag.getDocuments()).anySatisfy(doc -> assertThat(doc.getId()).isEqualTo(q4ContractId));
        assertThat(rag.getDocuments()).anySatisfy(doc ->
            assertThat(doc.getContent()).contains("Contract - John Smith - Q4 2023"));
    }

    private void seedLawFirmData() {
        UserEntity johnSmith = new UserEntity();
        johnSmith.setFullName("John Smith");
        johnSmith.setEmail("john.smith@firm.test");

        UserEntity janeDoe = new UserEntity();
        janeDoe.setFullName("Jane Doe");
        janeDoe.setEmail("jane.doe@firm.test");

        johnSmith = userRepository.save(johnSmith);
        janeDoe = userRepository.save(janeDoe);

        DocumentEntity q4Contract = createDocument("Contract - John Smith - Q4 2023", "ACTIVE", johnSmith);
        DocumentEntity q3Contract = createDocument("Contract - John Smith - Q3 2023", "ACTIVE", johnSmith);
        DocumentEntity archived = createDocument("Contract - John Smith - Q4 2023 (Archive)", "ARCHIVED", johnSmith);
        DocumentEntity janeContract = createDocument("Contract - Jane Doe - Q4 2023", "ACTIVE", janeDoe);

        documentRepository.saveAll(List.of(q4Contract, q3Contract, archived, janeContract));
        q4ContractId = q4Contract.getId();
    }

    private DocumentEntity createDocument(String title, String status, UserEntity author) {
        DocumentEntity document = new DocumentEntity();
        document.setTitle(title);
        document.setStatus(status);
        document.setAuthor(author);
        author.getDocuments().add(document);
        return document;
    }
}
