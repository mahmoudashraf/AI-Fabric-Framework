package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.it.RelationshipQueryIntegrationTestApplication;
import com.ai.infrastructure.relationship.it.api.RelationshipQueryRequest;
import com.ai.infrastructure.relationship.it.config.BackendEnvTestConfiguration;
import com.ai.infrastructure.relationship.it.entity.AccountEntity;
import com.ai.infrastructure.relationship.it.entity.TransactionEntity;
import com.ai.infrastructure.relationship.it.repository.AccountRepository;
import com.ai.infrastructure.relationship.it.repository.TransactionRepository;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("realapi")
@Import(BackendEnvTestConfiguration.class)
class FinancialFraudRealApiIntegrationTest {

    private static final String QUERY = "List suspicious transactions over $25k from high-risk regions routed through the same counterparty";
    private static final String MIRROR_QUERY = "Find high-risk wire transfers above $30k where the destination account owner matches the source account owner";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired(required = false)
    private VectorDatabaseService vectorDatabaseService;

    private String flaggedTransactionId;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        if (vectorDatabaseService != null) {
            try {
                vectorDatabaseService.clearVectors();
            } catch (Exception ignored) {
            }
        }
        seedTransactions();
    }

    @Test
    void shouldDetectHighRiskWire() {
        RelationshipQueryRequest request = new RelationshipQueryRequest();
        request.setQuery(QUERY);
        request.setEntityTypes(List.of("transaction"));
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
        assertThat(rag.getDocuments()).anySatisfy(doc -> assertThat(doc.getId()).isEqualTo(flaggedTransactionId));
        assertThat(rag.getDocuments()).anySatisfy(doc ->
            assertThat(doc.getContent()).contains("Pending Wire 40k"));
    }

    @Test
    void shouldDetectMirrorCounterpartyWires() {
        RelationshipQueryRequest request = new RelationshipQueryRequest();
        request.setQuery(MIRROR_QUERY);
        request.setEntityTypes(List.of("transaction"));
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
        assertThat(rag.getDocuments()).anySatisfy(doc -> assertThat(doc.getId()).isEqualTo(flaggedTransactionId));
    }

    private void seedTransactions() {
        // Use owner name that matches the JPQL parameter produced by the test query
        AccountEntity highRiskOrigin = account("origin-account.ownerName", "high-risk region", BigDecimal.valueOf(0.83));
        AccountEntity counterpart = account(highRiskOrigin.getOwnerName(), "high-risk corridor", BigDecimal.valueOf(0.22));
        AccountEntity benignOrigin = account("Sunrise Foods", "stable region", BigDecimal.valueOf(0.35));

        highRiskOrigin = accountRepository.save(highRiskOrigin);
        counterpart = accountRepository.save(counterpart);
        benignOrigin = accountRepository.save(benignOrigin);

        TransactionEntity suspiciousWire = transaction(
            "Pending Wire 40k",
            BigDecimal.valueOf(40_000),
            "Wire",
            "PENDING_REVIEW",
            highRiskOrigin,
            counterpart,
            true
        );

        TransactionEntity clearedWire = transaction(
            "Cleared Wire 32k",
            BigDecimal.valueOf(32_000),
            "Wire",
            "CLEARED",
            highRiskOrigin,
            counterpart,
            false
        );

        TransactionEntity benignAch = transaction(
            "ACH 50k Stable",
            BigDecimal.valueOf(50_000),
            "ACH",
            "PENDING_REVIEW",
            benignOrigin,
            counterpart,
            false
        );

        transactionRepository.saveAll(List.of(suspiciousWire, clearedWire, benignAch));
        indexTransaction(suspiciousWire);
        indexTransaction(clearedWire);
        indexTransaction(benignAch);
    }

    private AccountEntity account(String owner, String region, BigDecimal risk) {
        AccountEntity account = new AccountEntity();
        account.setOwnerName(owner);
        account.setRegion(region);
        account.setRiskScore(risk);
        return account;
    }

    private TransactionEntity transaction(String title,
                                          BigDecimal amount,
                                          String channel,
                                          String status,
                                          AccountEntity source,
                                          AccountEntity destination,
                                          boolean flagged) {
        TransactionEntity tx = new TransactionEntity();
        tx.setTitle(title);
        tx.setAmount(amount);
        tx.setCurrency("USD");
        tx.setChannel(channel);
        tx.setStatus(status);
        tx.setOccurredAt(LocalDateTime.now().minusHours(4));
        tx.setSourceAccount(source);
        tx.setDestinationAccount(destination);
        tx = transactionRepository.save(tx);
        if (flagged) {
            flaggedTransactionId = tx.getId();
        }
        return tx;
    }

    private void indexTransaction(TransactionEntity transaction) {
        searchableEntityRepository.save(
            AISearchableEntity.builder()
                .entityType("transaction")
                .entityId(transaction.getId())
                .searchableContent("%s - %s %s"
                    .formatted(transaction.getTitle(), transaction.getChannel(), transaction.getAmount()))
                .metadata("""
                    {"status":"%s","destinationRegion":"%s","sourceRegion":"%s"}
                    """.formatted(
                        transaction.getStatus(),
                        transaction.getDestinationAccount().getRegion(),
                        transaction.getSourceAccount().getRegion()
                    ))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()
        );
    }
}
