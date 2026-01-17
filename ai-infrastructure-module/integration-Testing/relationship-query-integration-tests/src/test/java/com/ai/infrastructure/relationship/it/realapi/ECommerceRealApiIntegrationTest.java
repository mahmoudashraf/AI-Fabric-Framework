package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.it.RelationshipQueryIntegrationTestApplication;
import com.ai.infrastructure.relationship.it.api.RelationshipQueryRequest;
import com.ai.infrastructure.relationship.it.config.BackendEnvTestConfiguration;
import com.ai.infrastructure.relationship.it.entity.BrandEntity;
import com.ai.infrastructure.relationship.it.entity.ProductEntity;
import com.ai.infrastructure.relationship.it.repository.BrandRepository;
import com.ai.infrastructure.relationship.it.repository.ProductRepository;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("realapi")
@Import(BackendEnvTestConfiguration.class)
class ECommerceRealApiIntegrationTest {

    private static final String QUERY = "Show me blue shoes under $100 from Nike";
    private static final String CROSS_BRAND_QUERY = "Show active Nike or Adidas runner shoes priced between $80 and $120 available in red or blue";
    private static final Pattern PRODUCT_CONTENT_PATTERN = Pattern.compile("^(.*) \\((.*)\\) - \\$(.*)$");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired(required = false)
    private VectorDatabaseService vectorDatabaseService;

    private String nikeProductId;
    private String adidasRunnerId;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        brandRepository.deleteAllInBatch();
        if (vectorDatabaseService != null) {
            try {
                vectorDatabaseService.clearVectors();
            } catch (Exception ignored) {
            }
        }
        seedCatalog();
    }

    @Test
    void shouldFindBlueNikeShoesUnderHundred() {
        RelationshipQueryRequest request = new RelationshipQueryRequest();
        request.setQuery(QUERY);
        request.setEntityTypes(List.of("product"));
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
        assertThat(rag.getDocuments()).anySatisfy(doc -> assertThat(doc.getId()).isEqualTo(nikeProductId));
        assertThat(rag.getDocuments()).anySatisfy(doc -> assertThat(doc.getContent()).contains("Blue Runner"));
    }

    @Test
    void shouldFindCrossBrandRunnerShoesWithinRange() {
        RelationshipQueryRequest request = new RelationshipQueryRequest();
        request.setQuery(CROSS_BRAND_QUERY);
        request.setEntityTypes(List.of("product"));
        request.setReturnMode(ReturnMode.FULL);
        request.setLimit(10);

        ResponseEntity<RAGResponse> response = restTemplate.postForEntity(
            "/api/relationship-query/execute",
            request,
            RAGResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        RAGResponse rag = response.getBody();
        assertThat(rag.getDocuments()).isNotEmpty();
        assertThat(rag.getDocuments()).allSatisfy(doc -> {
            assertThat(doc.getMetadata()).isNotNull();
            assertThat(doc.getMetadata().get("brand")).isIn("Nike", "Adidas");
            assertThat(doc.getMetadata().get("status")).isEqualTo("ACTIVE");

            ParsedProduct parsed = parseProductContent(doc.getContent());
            assertThat(parsed.color()).isIn("red", "blue");
            assertThat(parsed.price()).isBetween(BigDecimal.valueOf(80), BigDecimal.valueOf(120));
        });
    }

    private void seedCatalog() {
        BrandEntity nike = new BrandEntity();
        nike.setName("Nike");

        BrandEntity adidas = new BrandEntity();
        adidas.setName("Adidas");

        nike = brandRepository.save(nike);
        adidas = brandRepository.save(adidas);

        ProductEntity nikeBlueRunner = product("Blue Runner Running Shoes", "blue", BigDecimal.valueOf(85), "ACTIVE", nike);
        ProductEntity nikePremiumBoot = product("Premium Trail Boot", "blue", BigDecimal.valueOf(180), "ACTIVE", nike);
        ProductEntity nikeRedRunner = product("Red Runner Running Shoes", "red", BigDecimal.valueOf(90), "ACTIVE", nike);
        ProductEntity adidasBlue = product("Adidas Flex Shoes", "blue", BigDecimal.valueOf(95), "ACTIVE", adidas);
        ProductEntity adidasRunner = product("Adidas Runner Shoes Elite", "red", BigDecimal.valueOf(110), "ACTIVE", adidas);

        productRepository.saveAll(List.of(nikeBlueRunner, nikePremiumBoot, nikeRedRunner, adidasBlue, adidasRunner));
        indexProduct(nikeBlueRunner);
        indexProduct(nikePremiumBoot);
        indexProduct(nikeRedRunner);
        indexProduct(adidasBlue);
        indexProduct(adidasRunner);
        nikeProductId = nikeBlueRunner.getId();
        adidasRunnerId = adidasRunner.getId();
    }

    private ProductEntity product(String name, String color, BigDecimal price, String status, BrandEntity brand) {
        ProductEntity product = new ProductEntity();
        product.setName(name);
        product.setColor(color);
        product.setPrice(price);
        product.setStatus(status);
        product.setBrand(brand);
        brand.getProducts().add(product);
        return product;
    }

    private void indexProduct(ProductEntity product) {
        searchableEntityRepository.save(
            AISearchableEntity.builder()
                .entityType("product")
                .entityId(product.getId())
                .searchableContent("%s (%s) - $%s".formatted(product.getName(), product.getColor(), product.getPrice()))
                .metadata("""
                    {"brand":"%s","status":"%s"}
                    """.formatted(product.getBrand().getName(), product.getStatus()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()
        );
    }

    private ParsedProduct parseProductContent(String content) {
        assertThat(content).isNotBlank();
        Matcher matcher = PRODUCT_CONTENT_PATTERN.matcher(content.trim());
        assertThat(matcher.matches())
            .as("content should match '%s' but was '%s'".formatted(PRODUCT_CONTENT_PATTERN, content))
            .isTrue();

        String name = matcher.group(1).trim();
        String color = matcher.group(2).trim().toLowerCase(Locale.ROOT);
        BigDecimal price = new BigDecimal(matcher.group(3).trim());
        return new ParsedProduct(name, color, price);
    }

    private record ParsedProduct(String name, String color, BigDecimal price) {}
}
