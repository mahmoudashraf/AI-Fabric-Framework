package com.ai.infrastructure.relationship.usecases;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.integration.IntegrationTestSupport;
import com.ai.infrastructure.relationship.integration.RelationshipQueryIntegrationTest;
import com.ai.infrastructure.relationship.integration.entity.MedicalCaseEntity;
import com.ai.infrastructure.relationship.integration.entity.PatientEntity;
import com.ai.infrastructure.relationship.integration.repository.MedicalCaseRepository;
import com.ai.infrastructure.relationship.integration.repository.PatientRepository;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.service.DynamicJPAQueryBuilder;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTest.IntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@Import(MedicalCaseFinderTest.VectorOverrides.class)
class MedicalCaseFinderTest {

    private static final Logger log = LoggerFactory.getLogger(MedicalCaseFinderTest.class);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.registerCommonProperties(registry);
    }

    @Autowired
    private MedicalCaseRepository medicalCaseRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired
    private RelationshipQueryPlanner planner;

    @Autowired
    private DynamicJPAQueryBuilder dynamicJPAQueryBuilder;

    @Autowired
    private RelationshipQueryValidator relationshipQueryValidator;

    @Autowired
    private RelationshipQueryProperties relationshipQueryProperties;

    @Autowired
    private RelationshipModuleMetadata relationshipModuleMetadata;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private AIEmbeddingService aiEmbeddingService;

    @Autowired
    private QueryCache queryCache;

    @Autowired
    private QueryMetrics queryMetrics;

    @Autowired
    private EntityRelationshipMapper entityRelationshipMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    private LLMDrivenJPAQueryService llmDrivenJPAQueryService;
    private String oncologyCaseId;

    @BeforeEach
    void setUp() {
        Mockito.reset(planner);
        searchableEntityRepository.deleteAll();
        medicalCaseRepository.deleteAll();
        patientRepository.deleteAll();
        if (vectorDatabaseService != null) {
            try {
                vectorDatabaseService.clearVectors();
            } catch (Exception ex) {
                log.warn("Unable to clear vectors from Lucene test index; continuing with fresh context", ex);
            }
        }

        seedMedicalData();

        var schemaProvider = new com.ai.infrastructure.relationship.service.RelationshipSchemaProvider(
            entityManager,
            null,
            relationshipQueryProperties,
            entityRelationshipMapper
        );
        schemaProvider.refreshSchema();

        var jpaTraversalService = new com.ai.infrastructure.relationship.service.JpaRelationshipTraversalService(entityManager);
        var metadataTraversalService = new com.ai.infrastructure.relationship.service.MetadataRelationshipTraversalService(
            searchableEntityRepository,
            objectMapper
        );

        llmDrivenJPAQueryService = new LLMDrivenJPAQueryService(
            planner,
            dynamicJPAQueryBuilder,
            relationshipQueryValidator,
            relationshipQueryProperties,
            relationshipModuleMetadata,
            jpaTraversalService,
            metadataTraversalService,
            searchableEntityRepository,
            vectorDatabaseService,
            aiEmbeddingService,
            queryCache,
            queryMetrics
        );
    }

    @Test
    void shouldFindActiveOncologyImmunotherapyCasesForAlice() {
        String query = "Find active oncology cases for Alice Carter that require immunotherapy";

        FilterCondition specialtyFilter = FilterCondition.builder()
            .field("specialty")
            .operator(FilterOperator.ILIKE)
            .value("oncology")
            .build();

        FilterCondition therapyFilter = FilterCondition.builder()
            .field("therapyPlan")
            .operator(FilterOperator.ILIKE)
            .value("%immunotherapy%")
            .build();

        FilterCondition statusFilter = FilterCondition.builder()
            .field("status")
            .operator(FilterOperator.EQUALS)
            .value("ACTIVE")
            .build();

        RelationshipPath patientPath = RelationshipPath.builder()
            .fromEntityType("medical-case")
            .relationshipType("patient")
            .toEntityType("patient")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .conditions(List.of(FilterCondition.builder()
                .field("fullName")
                .operator(FilterOperator.ILIKE)
                .value("%Alice Carter%")
                .build()))
            .build();

        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery(query)
            .primaryEntityType("medical-case")
            .candidateEntityTypes(List.of("medical-case", "patient"))
            .relationshipPaths(List.of(patientPath))
            .directFilters(Map.of("medical-case", List.of(specialtyFilter, therapyFilter, statusFilter)))
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .returnMode(ReturnMode.FULL)
            .needsSemanticSearch(false)
            .limit(5)
            .build();

        when(planner.planQuery(eq(query), eq(List.of("medical-case")))).thenReturn(plan);

        QueryOptions options = QueryOptions.builder()
            .returnMode(ReturnMode.FULL)
            .limit(5)
            .build();

        RAGResponse response = llmDrivenJPAQueryService.executeRelationshipQuery(query, List.of("medical-case"), options);

        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).getId()).isEqualTo(oncologyCaseId);
        assertThat(response.getDocuments().get(0).getContent()).contains("Alice Carter");
    }

    private void seedMedicalData() {
        PatientEntity alice = new PatientEntity();
        alice.setFullName("Alice Carter");
        alice.setDateOfBirth(LocalDate.of(1984, 5, 12));

        PatientEntity bob = new PatientEntity();
        bob.setFullName("Bob Jensen");
        bob.setDateOfBirth(LocalDate.of(1978, 2, 8));

        patientRepository.save(alice);
        patientRepository.save(bob);

        MedicalCaseEntity activeOncology = medicalCase(
            "Alice Carter Oncology Case",
            "oncology",
            "Immunotherapy + monitoring",
            "ACTIVE",
            alice
        );
        MedicalCaseEntity inactiveOncology = medicalCase(
            "Alice Carter Oncology Case (Closed)",
            "oncology",
            "Radiation therapy",
            "CLOSED",
            alice
        );
        MedicalCaseEntity cardiologyBob = medicalCase(
            "Bob Jensen Cardiology Case",
            "cardiology",
            "Statin therapy",
            "ACTIVE",
            bob
        );

        oncologyCaseId = activeOncology.getId();

        indexCase(activeOncology, "oncology", "immunotherapy", "ACTIVE", "alice carter");
        indexCase(inactiveOncology, "oncology", "radiation", "CLOSED", "alice carter");
        indexCase(cardiologyBob, "cardiology", "statin", "ACTIVE", "bob jensen");

        entityRelationshipMapper.registerEntityType(MedicalCaseEntity.class);
        entityRelationshipMapper.registerEntityType(PatientEntity.class);
        entityRelationshipMapper.registerRelationship("medical-case", "patient", "patient", RelationshipDirection.FORWARD, false);
    }

    private MedicalCaseEntity medicalCase(String title, String specialty, String therapy, String status, PatientEntity patient) {
        MedicalCaseEntity medicalCase = new MedicalCaseEntity();
        medicalCase.setTitle(title);
        medicalCase.setSpecialty(specialty);
        medicalCase.setTherapyPlan(therapy);
        medicalCase.setStatus(status);
        medicalCase.setPatient(patient);
        patient.getMedicalCases().add(medicalCase);
        return medicalCaseRepository.save(medicalCase);
    }

    private void indexCase(MedicalCaseEntity medicalCase, String specialty, String therapy, String status, String patientName) {
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityType("medical-case")
            .entityId(medicalCase.getId())
            .searchableContent(medicalCase.getTitle())
            .metadata("{\"specialty\":\"%s\",\"therapy\":\"%s\",\"status\":\"%s\",\"patient\":\"%s\"}"
                .formatted(specialty, therapy, status, patientName))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        searchableEntityRepository.save(entity);
    }

    @TestConfiguration
    static class VectorOverrides {

        @Bean
        public VectorDatabaseService vectorDatabaseService() {
            return Mockito.mock(VectorDatabaseService.class);
        }
    }

    @AfterAll
    static void cleanUpLuceneIndex() throws IOException {
        IntegrationTestSupport.cleanUpLuceneIndex();
    }
}
