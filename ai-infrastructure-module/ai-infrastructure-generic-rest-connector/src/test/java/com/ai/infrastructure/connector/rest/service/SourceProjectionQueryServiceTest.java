package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.InMemoryIntegrationStateRepository;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceProjectionQueryServiceTest {

    @Test
    void queriesOnlyTheSynchronizedSafeProjectionWithEveryDeclaredFilter() {
        InMemoryIntegrationStateRepository repository = readyRepository();
        SourceProjectionQueryService service = new SourceProjectionQueryService(repository);
        RestRoutingConfig.SourceProjectionQuery config = config();

        Map<String, Object> result = service.query(config, Map.of(
            "fuelType", "diesel",
            "bodyType", "suv",
            "maxPriceGbp", 10_000,
            "limit", 5
        ));

        assertThat(result.get("totalResults")).isEqualTo(1L);
        assertThat(result.get("returnedCount")).isEqualTo(1);
        assertThat((List<?>) result.get("results")).singleElement().satisfies(record -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> projected = (Map<String, Object>) record;
            assertThat(projected)
                .containsEntry("stockId", "stock-1")
                .containsEntry("priceGbp", 9_500)
                .doesNotContainKey("internalNote");
        });
        @SuppressWarnings("unchecked")
        Map<String, Object> appliedFilters = (Map<String, Object>) result.get("appliedFilters");
        assertThat(appliedFilters)
            .containsEntry("fuelType", "diesel")
            .containsEntry("bodyType", "suv")
            .containsEntry("maxPriceGbp", "10000");
    }

    @Test
    void reportsARealEmptyResultInsteadOfReturningUnfilteredRecords() {
        SourceProjectionQueryService service = new SourceProjectionQueryService(readyRepository());

        Map<String, Object> result = service.query(config(), Map.of(
            "fuelType", "Diesel",
            "bodyType", "SUV",
            "maxPriceGbp", 1_000
        ));

        assertThat(result.get("totalResults")).isEqualTo(0L);
        assertThat(result.get("returnedCount")).isEqualTo(0);
        assertThat((List<?>) result.get("results")).isEmpty();
    }

    @Test
    void treatsConfiguredNumericSentinelAsAnAbsentOptionalFilter() {
        SourceProjectionQueryService service = new SourceProjectionQueryService(readyRepository());
        RestRoutingConfig.SourceProjectionQuery config = config();
        config.getFilters().stream()
            .filter(filter -> "maxMileage".equals(filter.getParam()))
            .findFirst()
            .orElseThrow()
            .setAbsentValues(List.of("0"));

        Map<String, Object> result = service.query(config, Map.of(
            "fuelType", "Electric",
            "maxMileage", 0.0
        ));

        assertThat(result.get("totalResults")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> appliedFilters = (Map<String, Object>) result.get("appliedFilters");
        assertThat(appliedFilters)
            .containsEntry("fuelType", "Electric")
            .doesNotContainKey("maxMileage");
    }

    @Test
    void failsClosedBeforeTheSourceHasACompletedSync() {
        SourceProjectionQueryService service = new SourceProjectionQueryService(
            new InMemoryIntegrationStateRepository()
        );

        assertThatThrownBy(() -> service.query(config(), Map.of()))
            .isInstanceOf(SourceProjectionQueryService.ProjectionQueryException.class)
            .hasMessageContaining("not ready");
    }

    private InMemoryIntegrationStateRepository readyRepository() {
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        repository.startSync("stock-source", "run-1", "source-v1");
        repository.applyProjectionChanges(
            "stock-source",
            "run-1",
            List.of(
                record("stock-1", "Diesel", "SUV", 9_500, 45_000),
                record("stock-2", "Electric", "Hatchback", 18_000, 12_000)
            ),
            Set.of()
        );
        repository.completeSync(
            "stock-source",
            "run-1",
            null,
            new IntegrationStateRepository.SyncCounts(2, 2, 2, 0, 2, 2, 0)
        );
        return repository;
    }

    private IntegrationStateRepository.SourceProjectionRecord record(
        String stockId,
        String fuelType,
        String bodyType,
        int priceGbp,
        int mileage
    ) {
        return new IntegrationStateRepository.SourceProjectionRecord(
            stockId,
            "fingerprint-" + stockId,
            "approved searchable content",
            Map.of(
                "stockId", stockId,
                "make", "Northstar",
                "fuelType", fuelType,
                "bodyType", bodyType,
                "priceGbp", priceGbp,
                "mileage", mileage,
                "internalNote", "not projected outward"
            ),
            Map.of("providerUpdatedAt", "2026-10-04T12:00:00Z"),
            Instant.now()
        );
    }

    private RestRoutingConfig.SourceProjectionQuery config() {
        RestRoutingConfig.SourceProjectionQuery config = new RestRoutingConfig.SourceProjectionQuery();
        config.setSourceRef("stock-source");
        config.setOutputFields(List.of("stockId", "make", "fuelType", "bodyType", "priceGbp", "mileage"));
        config.setDefaultLimit(10);
        config.setMaxLimit(24);
        config.setMaxStalenessSeconds(3_600);
        config.setFilters(List.of(
            filter("fuelType", "fuelType", RestRoutingConfig.SourceProjectionFilterOperator.EQUALS_IGNORE_CASE),
            filter("bodyType", "bodyType", RestRoutingConfig.SourceProjectionFilterOperator.EQUALS_IGNORE_CASE),
            filter("maxPriceGbp", "priceGbp", RestRoutingConfig.SourceProjectionFilterOperator.NUMBER_LESS_THAN_OR_EQUAL),
            filter("maxMileage", "mileage", RestRoutingConfig.SourceProjectionFilterOperator.NUMBER_LESS_THAN_OR_EQUAL)
        ));
        return config;
    }

    private RestRoutingConfig.SourceProjectionFilter filter(
        String param,
        String field,
        RestRoutingConfig.SourceProjectionFilterOperator operator
    ) {
        RestRoutingConfig.SourceProjectionFilter filter = new RestRoutingConfig.SourceProjectionFilter();
        filter.setParam(param);
        filter.setFields(List.of(field));
        filter.setOperator(operator);
        return filter;
    }
}
