package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.InMemoryIntegrationStateRepository;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderIntegrationFoundationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void formTokenPageFixtureCachesTokenInjectsResourceAndIndexesEveryPage() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger catalogCalls = new AtomicInteger();
        AtomicReference<String> tokenForm = new AtomicReference<>();
        List<JsonNode> indexedOperations = java.util.Collections.synchronizedList(new ArrayList<>());
        startServer();
        server.createContext("/authenticate", exchange -> {
            tokenCalls.incrementAndGet();
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"token\":\"fixture-token\",\"expiresIn\":3600}");
        });
        server.createContext("/catalog", exchange -> {
            catalogCalls.incrementAndGet();
            Map<String, String> query = query(exchange.getRequestURI());
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-token");
            assertThat(query.get("account")).isEqualTo("account-17");
            assertThat(query.get("pageSize")).isEqualTo("2");
            String body = "1".equals(query.get("page"))
                ? "{\"items\":[{\"id\":\"item-1\",\"account\":\"account-17\",\"name\":\"Alpha\"},{\"id\":\"item-2\",\"account\":\"account-17\",\"name\":\"Beta\"}]}"
                : "{\"items\":[{\"id\":\"item-3\",\"account\":\"account-17\",\"name\":\"Gamma\"}]}";
            exchange.getResponseHeaders().add("X-Fixture-Request", "request-" + catalogCalls.get());
            respond(exchange, 200, body);
        });
        server.createContext("/api/internal/integrations/data-sync/batch", exchange -> respondToDataSync(exchange, indexedOperations, false));

        RestRoutingConfig config = baseConfig();
        RestRoutingConfig.ConnectionProfile profile = formProfile(baseUrl());
        profile.setCorrelationResponseHeaders(List.of("X-Fixture-Request"));
        config.getConnectionProfiles().put("form-page", profile);
        config.getProtectedResources().put("account-binding", binding("form-page", "account", "account-17"));
        config.getDataSources().put("catalog-source", pageSource());

        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        Services services = services(config, repository);

        List<CompletableFuture<Map<String, String>>> concurrentAuth = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            concurrentAuth.add(CompletableFuture.supplyAsync(
                () -> services.tokenService().authenticationHeaders("form-page", false)
            ));
        }
        CompletableFuture.allOf(concurrentAuth.toArray(CompletableFuture[]::new)).join();

        IntegrationStateRepository.SyncState state = services.syncService().reconcile("catalog-source");

        assertThat(tokenCalls.get()).isEqualTo(1);
        assertThat(tokenForm.get()).contains("key=fixture-key").contains("secret=fixture-secret");
        assertThat(catalogCalls.get()).isEqualTo(2);
        assertThat(state.status()).isEqualTo("COMPLETED");
        assertThat(state.sourceVersion()).isEqualTo("fixture-source-v1");
        assertThat(state.providerCorrelationHeader()).isEqualTo("X-Fixture-Request");
        assertThat(state.providerCorrelationValue()).isEqualTo("request-2");
        assertThat(state.counts().sourceCount()).isEqualTo(3);
        assertThat(state.counts().indexedCount()).isEqualTo(3);
        assertThat(repository.activeRecordIds("catalog-source")).containsExactlyInAnyOrder("item-1", "item-2", "item-3");
        assertThat(indexedOperations).hasSize(3);
        assertThat(indexedOperations).allSatisfy(operation -> {
            assertThat(operation.path("vectorSpace").asText()).isEqualTo("catalog-entry");
            assertThat(operation.path("metadata").path("deploymentId").asText()).isEqualTo("dep-neutral-a");
            assertThat(operation.path("metadata").path("tenantId").asText()).isEqualTo("tenant-neutral-a");
            assertThat(operation.path("metadata").path("protectedResourceFingerprint").asText()).hasSize(64);
        });
    }

    @Test
    void apiKeyCursorFixtureSupportsPathAndHeaderBindingErrorMappingAndFailureResume() throws Exception {
        AtomicReference<String> expectedCursor = new AtomicReference<>();
        AtomicBoolean failRuntime = new AtomicBoolean(false);
        List<JsonNode> indexedOperations = java.util.Collections.synchronizedList(new ArrayList<>());
        startServer();
        server.createContext("/feed/tenant-44", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Provider-Key")).isEqualTo("fixture-api-key");
            assertThat(exchange.getRequestHeaders().getFirst("X-Tenant-Scope")).isEqualTo("tenant-44");
            String cursor = query(exchange.getRequestURI()).get("after");
            expectedCursor.set(cursor);
            if (cursor == null) {
                respond(exchange, 200, "{\"records\":[{\"id\":\"doc-1\",\"tenant\":\"tenant-44\",\"title\":\"First\"}],\"next\":\"cursor-2\"}");
            } else if ("cursor-2".equals(cursor)) {
                respond(exchange, 200, failRuntime.get()
                    ? "{\"records\":[{\"id\":\"doc-3\",\"tenant\":\"tenant-44\",\"title\":\"Third\"}],\"next\":\"cursor-3\"}"
                    : "{\"records\":[{\"id\":\"doc-2\",\"tenant\":\"tenant-44\",\"title\":\"Second\"}],\"next\":\"\"}");
            } else {
                respond(exchange, 200, "{\"records\":[],\"next\":\"\"}");
            }
        });
        server.createContext("/denied", exchange -> respond(exchange, 403, "{\"code\":\"MISSING_GRANT\"}"));
        server.createContext("/oversized", exchange -> respond(exchange, 200, "x".repeat(4096)));
        server.createContext("/api/internal/integrations/data-sync/batch", exchange -> respondToDataSync(exchange, indexedOperations, failRuntime.get()));

        RestRoutingConfig config = baseConfig();
        RestRoutingConfig.ConnectionProfile profile = apiKeyProfile(baseUrl());
        RestRoutingConfig.ErrorMapping mapping = new RestRoutingConfig.ErrorMapping();
        mapping.setStatus(403);
        mapping.setBodyJsonPointer("/code");
        mapping.setEqualsValue("MISSING_GRANT");
        mapping.setErrorClass(RestRoutingConfig.ErrorMapping.ErrorClass.CAPABILITY_DENIED);
        profile.setErrorMappings(List.of(mapping));
        config.getConnectionProfiles().put("key-cursor", profile);
        config.getProtectedResources().put("tenant-binding", binding("key-cursor", "tenant", "tenant-44"));
        config.getDataSources().put("document-feed", cursorSource());

        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        Services services = services(config, repository);
        IntegrationStateRepository.SyncState first = services.syncService().reconcile("document-feed");

        assertThat(first.cursor()).isEqualTo("cursor-2");
        assertThat(first.counts().indexedCount()).isEqualTo(2);
        assertThat(repository.activeRecordIds("document-feed")).containsExactlyInAnyOrder("doc-1", "doc-2");

        ProviderHttpClient.ProviderResponse denied = services.providerClient().execute(new ProviderHttpClient.ProviderRequest(
            "key-cursor", "GET", "/denied", Map.of(), Map.of(), null, 2000, 1024, true
        ));
        assertThat(services.providerClient().classify("key-cursor", denied.status(), denied.body()))
            .isEqualTo(ProviderErrorClass.CAPABILITY_DENIED);
        assertThatThrownBy(() -> services.providerClient().execute(new ProviderHttpClient.ProviderRequest(
            "key-cursor", "GET", "/oversized", Map.of(), Map.of(), null, 2000, 128, true
        )))
            .isInstanceOf(ProviderCallException.class)
            .extracting(error -> ((ProviderCallException) error).errorClass())
            .isEqualTo(ProviderErrorClass.MALFORMED_RESPONSE);

        failRuntime.set(true);
        assertThatThrownBy(() -> services.syncService().reconcile("document-feed"))
            .isInstanceOf(ProviderCallException.class);
        IntegrationStateRepository.SyncState failed = repository.syncState("document-feed").orElseThrow();
        assertThat(expectedCursor.get()).isEqualTo("cursor-3");
        assertThat(failed.cursor()).isEqualTo("cursor-2");
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.counts().failedWorkCount()).isEqualTo(1);
        assertThat(repository.activeRecordIds("document-feed")).containsExactlyInAnyOrder("doc-1", "doc-2");
    }

    @Test
    void changedSourceVersionRestartsCursorFromTheBeginning() throws Exception {
        List<String> observedCursors = java.util.Collections.synchronizedList(new ArrayList<>());
        List<JsonNode> indexedOperations = java.util.Collections.synchronizedList(new ArrayList<>());
        startServer();
        server.createContext("/versioned/tenant-44", exchange -> {
            String cursor = query(exchange.getRequestURI()).get("after");
            observedCursors.add(cursor == null ? "<start>" : cursor);
            respond(exchange, 200, cursor == null
                ? "{\"records\":[{\"id\":\"doc-1\",\"tenant\":\"tenant-44\",\"title\":\"First\"}],\"next\":\"cursor-2\"}"
                : "{\"records\":[],\"next\":\"\"}");
        });
        server.createContext("/api/internal/integrations/data-sync/batch", exchange ->
            respondToDataSync(exchange, indexedOperations, false)
        );

        RestRoutingConfig config = baseConfig();
        config.getConnectionProfiles().put("key-cursor", apiKeyProfile(baseUrl()));
        config.getProtectedResources().put("tenant-binding", binding("key-cursor", "tenant", "tenant-44"));
        RestRoutingConfig.HttpDataSource source = cursorSource();
        source.setPath("/versioned/{tenant}");
        config.getDataSources().put("versioned-feed", source);
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        Services services = services(config, repository);

        services.syncService().reconcile("versioned-feed");
        source.setSourceVersion("fixture-source-v2");
        services.syncService().reconcile("versioned-feed");

        assertThat(observedCursors).containsExactly("<start>", "cursor-2", "<start>", "cursor-2");
        assertThat(repository.syncState("versioned-feed").orElseThrow().sourceVersion())
            .isEqualTo("fixture-source-v2");
    }

    @Test
    void concurrentAuthenticationRejectionsCauseOneGenerationAwareTokenRefresh() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger protectedCalls = new AtomicInteger();
        int concurrentRequests = 6;
        CountDownLatch rejectedTokenArrivals = new CountDownLatch(concurrentRequests);
        startServer();
        server.createContext("/authenticate", exchange -> {
            int tokenNumber = tokenCalls.incrementAndGet();
            long expiresAt = java.time.Instant.now().plusSeconds(3600).getEpochSecond();
            respond(exchange, 200, "{\"token\":\"fixture-token-" + tokenNumber + "\",\"expiresAt\":" + expiresAt + "}");
        });
        server.createContext("/protected", exchange -> {
            protectedCalls.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if ("Bearer fixture-token-1".equals(authorization)) {
                rejectedTokenArrivals.countDown();
                try {
                    rejectedTokenArrivals.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                respond(exchange, 401, "{\"code\":\"TOKEN_EXPIRED\"}");
                return;
            }
            assertThat(authorization).isEqualTo("Bearer fixture-token-2");
            respond(exchange, 200, "{\"ok\":true}");
        });

        RestRoutingConfig config = baseConfig();
        RestRoutingConfig.ConnectionProfile profile = formProfile(baseUrl());
        profile.getAuth().setRelativeExpiryJsonPointer(null);
        profile.getAuth().setAbsoluteExpiryJsonPointer("/expiresAt");
        config.getConnectionProfiles().put("refresh-profile", profile);
        Services services = services(config, new InMemoryIntegrationStateRepository());
        services.tokenService().authenticationHeaders("refresh-profile", false);

        List<CompletableFuture<ProviderHttpClient.ProviderResponse>> calls = new ArrayList<>();
        for (int index = 0; index < concurrentRequests; index++) {
            calls.add(CompletableFuture.supplyAsync(() -> services.providerClient().execute(
                new ProviderHttpClient.ProviderRequest(
                    "refresh-profile", "GET", "/protected", Map.of(), Map.of(), null, 3000, 1024, true
                )
            )));
        }
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();

        assertThat(calls).allSatisfy(call -> assertThat(call.join().status()).isEqualTo(200));
        assertThat(tokenCalls.get()).isEqualTo(2);
        assertThat(protectedCalls.get()).isEqualTo(concurrentRequests * 2);
        assertThat(services.tokenService().posture().get("refresh-profile").status()).isEqualTo("READY");
    }

    @Test
    void retriesOnlySafeOrIdempotentProviderRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startServer();
        server.createContext("/mutation", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "{\"code\":\"TEMPORARY\"}");
        });
        RestRoutingConfig config = baseConfig();
        RestRoutingConfig.ConnectionProfile profile = apiKeyProfile(baseUrl());
        profile.getRatePolicy().setMaxAttempts(3);
        profile.getRatePolicy().setRetryBackoffMs(0);
        profile.getRatePolicy().setUnavailablePauseMs(0);
        profile.getRatePolicy().setRetryStatuses(List.of(503));
        config.getConnectionProfiles().put("write-profile", profile);
        Services services = services(config, new InMemoryIntegrationStateRepository());

        ProviderHttpClient.ProviderResponse unsafe = services.providerClient().execute(
            new ProviderHttpClient.ProviderRequest(
                "write-profile", "POST", "/mutation", Map.of(), Map.of(), "{}", 2000, 1024, false
            )
        );
        assertThat(unsafe.status()).isEqualTo(503);
        assertThat(calls.get()).isEqualTo(1);

        ProviderHttpClient.ProviderResponse idempotent = services.providerClient().execute(
            new ProviderHttpClient.ProviderRequest(
                "write-profile", "POST", "/mutation", Map.of(), Map.of(), "{}", 2000, 1024, true
            )
        );
        assertThat(idempotent.status()).isEqualTo(503);
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void snapshotAndFieldTombstonesConvergeActiveRecordsAfterSuccessfulIndexing() throws Exception {
        AtomicBoolean changed = new AtomicBoolean(false);
        List<JsonNode> indexedOperations = java.util.Collections.synchronizedList(new ArrayList<>());
        startServer();
        server.createContext("/snapshot", exchange -> respond(exchange, 200, changed.get()
            ? "{\"records\":[{\"id\":\"item-2\",\"scope\":\"scope-8\",\"name\":\"Old\",\"state\":\"deleted\"},{\"id\":\"item-3\",\"scope\":\"scope-8\",\"name\":\"New\",\"state\":\"active\"}]}"
            : "{\"records\":[{\"id\":\"item-1\",\"scope\":\"scope-8\",\"name\":\"One\",\"state\":\"active\"},{\"id\":\"item-2\",\"scope\":\"scope-8\",\"name\":\"Two\",\"state\":\"active\"}]}"
        ));
        server.createContext("/api/internal/integrations/data-sync/batch", exchange ->
            respondToDataSync(exchange, indexedOperations, false)
        );

        RestRoutingConfig config = baseConfig();
        config.getConnectionProfiles().put("snapshot-profile", apiKeyProfile(baseUrl()));
        config.getProtectedResources().put("scope-binding", binding("snapshot-profile", "tenant", "scope-8"));
        RestRoutingConfig.HttpDataSource source = source(
            "snapshot-profile", "scope-binding", "/snapshot", "snapshot-entry"
        );
        source.setRequiredCapabilityGrants(List.of("documents:read"));
        source.getMapping().setRecordsJsonPointer("/records");
        source.getMapping().setResourceJsonPointer("/scope");
        source.getTombstonePolicy().setStrategy(RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_OR_FIELD_VALUE);
        source.getTombstonePolicy().setOperationJsonPointer("/state");
        source.getTombstonePolicy().setDeleteValues(List.of("deleted"));
        config.getDataSources().put("snapshot-source", source);
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        Services services = services(config, repository);

        services.syncService().reconcile("snapshot-source");
        assertThat(repository.activeRecordIds("snapshot-source"))
            .containsExactlyInAnyOrder("item-1", "item-2");

        indexedOperations.clear();
        changed.set(true);
        IntegrationStateRepository.SyncState state = services.syncService().reconcile("snapshot-source");

        assertThat(repository.activeRecordIds("snapshot-source")).containsExactly("item-3");
        assertThat(state.counts().sourceCount()).isEqualTo(2);
        assertThat(state.counts().normalizedCount()).isEqualTo(2);
        assertThat(state.counts().indexedCount()).isEqualTo(1);
        assertThat(state.counts().deletedCount()).isEqualTo(2);
        assertThat(indexedOperations).filteredOn(operation -> "UPSERT".equals(operation.path("type").asText()))
            .extracting(operation -> operation.path("id").asText())
            .containsExactly("item-3");
        assertThat(indexedOperations).filteredOn(operation -> "DELETE".equals(operation.path("type").asText()))
            .extracting(operation -> operation.path("id").asText())
            .containsExactlyInAnyOrder("item-1", "item-2");
    }

    @Test
    void undeclaredPartialSuccessFailsBeforeApplyingSnapshotChanges() throws Exception {
        AtomicBoolean partial = new AtomicBoolean(false);
        List<JsonNode> indexedOperations = java.util.Collections.synchronizedList(new ArrayList<>());
        startServer();
        server.createContext("/partial-snapshot", exchange -> respond(exchange, partial.get() ? 206 : 200, partial.get()
            ? "{\"records\":[{\"id\":\"item-2\",\"scope\":\"scope-8\",\"name\":\"Two\"}]}"
            : "{\"records\":[{\"id\":\"item-1\",\"scope\":\"scope-8\",\"name\":\"One\"},{\"id\":\"item-2\",\"scope\":\"scope-8\",\"name\":\"Two\"}]}"
        ));
        server.createContext("/api/internal/integrations/data-sync/batch", exchange ->
            respondToDataSync(exchange, indexedOperations, false)
        );

        RestRoutingConfig config = baseConfig();
        config.getConnectionProfiles().put("snapshot-profile", apiKeyProfile(baseUrl()));
        config.getProtectedResources().put("scope-binding", binding("snapshot-profile", "tenant", "scope-8"));
        RestRoutingConfig.HttpDataSource source = source(
            "snapshot-profile", "scope-binding", "/partial-snapshot", "snapshot-entry"
        );
        source.setRequiredCapabilityGrants(List.of("documents:read"));
        source.getMapping().setRecordsJsonPointer("/records");
        source.getMapping().setResourceJsonPointer("/scope");
        source.getTombstonePolicy().setStrategy(RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_FROM_SNAPSHOT);
        config.getDataSources().put("partial-snapshot", source);
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        Services services = services(config, repository);

        services.syncService().reconcile("partial-snapshot");
        assertThat(repository.activeRecordIds("partial-snapshot"))
            .containsExactlyInAnyOrder("item-1", "item-2");

        partial.set(true);
        indexedOperations.clear();
        assertThatThrownBy(() -> services.syncService().reconcile("partial-snapshot"))
            .isInstanceOf(ProviderCallException.class)
            .hasMessageContaining("not declared as a complete response");

        assertThat(repository.activeRecordIds("partial-snapshot"))
            .containsExactlyInAnyOrder("item-1", "item-2");
        assertThat(indexedOperations).isEmpty();
        assertThat(repository.syncState("partial-snapshot").orElseThrow().status()).isEqualTo("FAILED");
    }

    @Test
    void incompletePagedSnapshotFailsBeforeApplyingAbsenceDeletes() throws Exception {
        AtomicBoolean truncate = new AtomicBoolean(false);
        List<JsonNode> indexedOperations = java.util.Collections.synchronizedList(new ArrayList<>());
        startServer();
        server.createContext("/bounded-snapshot", exchange -> {
            int page = Integer.parseInt(query(exchange.getRequestURI()).getOrDefault("page", "1"));
            if (!truncate.get()) {
                respond(exchange, 200, page == 1
                    ? "{\"records\":[{\"id\":\"item-1\",\"scope\":\"scope-8\",\"name\":\"One\"},{\"id\":\"item-2\",\"scope\":\"scope-8\",\"name\":\"Two\"}]}"
                    : "{\"records\":[]}");
                return;
            }
            respond(exchange, 200,
                "{\"records\":[{\"id\":\"item-2\",\"scope\":\"scope-8\",\"name\":\"Two\"},{\"id\":\"item-3\",\"scope\":\"scope-8\",\"name\":\"Three\"}]}"
            );
        });
        server.createContext("/api/internal/integrations/data-sync/batch", exchange ->
            respondToDataSync(exchange, indexedOperations, false)
        );

        RestRoutingConfig config = baseConfig();
        config.getConnectionProfiles().put("snapshot-profile", apiKeyProfile(baseUrl()));
        config.getProtectedResources().put("scope-binding", binding("snapshot-profile", "tenant", "scope-8"));
        RestRoutingConfig.HttpDataSource source = source(
            "snapshot-profile", "scope-binding", "/bounded-snapshot", "snapshot-entry"
        );
        source.setRequiredCapabilityGrants(List.of("documents:read"));
        source.getMapping().setRecordsJsonPointer("/records");
        source.getMapping().setResourceJsonPointer("/scope");
        source.getPagination().setStrategy(RestRoutingConfig.Pagination.Strategy.PAGE_SIZE);
        source.getPagination().setPageSize(2);
        source.getPagination().setMaxPages(2);
        source.getTombstonePolicy().setStrategy(RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_FROM_SNAPSHOT);
        config.getDataSources().put("bounded-snapshot", source);
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        Services services = services(config, repository);

        services.syncService().reconcile("bounded-snapshot");
        assertThat(repository.activeRecordIds("bounded-snapshot"))
            .containsExactlyInAnyOrder("item-1", "item-2");

        truncate.set(true);
        indexedOperations.clear();
        assertThatThrownBy(() -> services.syncService().reconcile("bounded-snapshot"))
            .isInstanceOf(ProviderCallException.class)
            .hasMessageContaining("page limit");

        assertThat(repository.activeRecordIds("bounded-snapshot"))
            .containsExactlyInAnyOrder("item-1", "item-2");
        assertThat(indexedOperations).isEmpty();
        assertThat(repository.syncState("bounded-snapshot").orElseThrow().status()).isEqualTo("FAILED");
    }

    private Services services(RestRoutingConfig config, IntegrationStateRepository repository) {
        Clock clock = Clock.systemUTC();
        ProviderTokenService tokenService = new ProviderTokenService(config, OBJECT_MAPPER, clock);
        ProviderHttpClient providerClient = new ProviderHttpClient(
            config,
            tokenService,
            new ProviderRateLimiter(clock),
            OBJECT_MAPPER
        );
        ProtectedResourceService protectedResources = new ProtectedResourceService(config, OBJECT_MAPPER);
        RuntimeDataSyncClient runtimeClient = new RuntimeDataSyncClient(config, OBJECT_MAPPER, repository);
        HttpDataSyncService syncService = new HttpDataSyncService(
            config,
            providerClient,
            protectedResources,
            runtimeClient,
            repository,
            OBJECT_MAPPER,
            clock
        );
        return new Services(tokenService, providerClient, syncService);
    }

    private RestRoutingConfig baseConfig() {
        RestRoutingConfig config = new RestRoutingConfig();
        config.getRuntimeDataSync().setEnabled(true);
        config.getRuntimeDataSync().setBaseUrl(baseUrl());
        config.getRuntimeDataSync().setApiKeyHeader("X-Integration-Key");
        config.getRuntimeDataSync().setApiKeyValue("runtime-fixture-key");
        config.getRuntimeDataSync().setDeploymentId("dep-neutral-a");
        config.getRuntimeDataSync().setTenantId("tenant-neutral-a");
        config.getRuntimeDataSync().setWorkPollAttempts(2);
        config.getRuntimeDataSync().setWorkPollIntervalMs(100);
        return config;
    }

    private RestRoutingConfig.ConnectionProfile formProfile(String baseUrl) {
        RestRoutingConfig.ConnectionProfile profile = new RestRoutingConfig.ConnectionProfile();
        profile.setEnvironment("fixture");
        profile.setBaseUrl(baseUrl);
        profile.setAllowedHosts(List.of("127.0.0.1"));
        profile.setCapabilityGrants(List.of("catalog:read"));
        profile.getAuth().setStrategy(RestRoutingConfig.ProviderAuth.Strategy.FORM_TOKEN_EXCHANGE);
        profile.getAuth().setTokenPath("/authenticate");
        profile.getAuth().setCredentialFields(Map.of("key", "fixture-key", "secret", "fixture-secret"));
        profile.getAuth().setTokenJsonPointer("/token");
        profile.getAuth().setRelativeExpiryJsonPointer("/expiresIn");
        return profile;
    }

    private RestRoutingConfig.ConnectionProfile apiKeyProfile(String baseUrl) {
        RestRoutingConfig.ConnectionProfile profile = new RestRoutingConfig.ConnectionProfile();
        profile.setEnvironment("fixture");
        profile.setBaseUrl(baseUrl);
        profile.setAllowedHosts(List.of("127.0.0.1"));
        profile.setCapabilityGrants(List.of("documents:read"));
        profile.getAuth().setStrategy(RestRoutingConfig.ProviderAuth.Strategy.API_KEY);
        profile.getAuth().setApiKeyHeader("X-Provider-Key");
        profile.getAuth().setApiKeyValue("fixture-api-key");
        return profile;
    }

    private RestRoutingConfig.ProtectedResourceBinding binding(String profileRef, String type, String id) {
        RestRoutingConfig.ProtectedResourceBinding binding = new RestRoutingConfig.ProtectedResourceBinding();
        binding.setConnectionProfileRef(profileRef);
        binding.setEnvironment("fixture");
        binding.setResourceType(type);
        binding.setResourceId(id);
        binding.setCapabilityGrants(List.of(type.equals("account") ? "catalog:read" : "documents:read"));
        return binding;
    }

    private RestRoutingConfig.HttpDataSource pageSource() {
        RestRoutingConfig.HttpDataSource source = source("form-page", "account-binding", "/catalog", "catalog-entry");
        source.setRequiredCapabilityGrants(List.of("catalog:read"));
        source.getPagination().setStrategy(RestRoutingConfig.Pagination.Strategy.PAGE_SIZE);
        source.getPagination().setPageQuery("page");
        source.getPagination().setSizeQuery("pageSize");
        source.getPagination().setPageSize(2);
        source.getPagination().setMaxPages(5);
        source.getTrustedResourcePlacements().add(placement(RestRoutingConfig.ResourcePlacement.Target.QUERY, "account"));
        source.getMapping().setRecordsJsonPointer("/items");
        source.getMapping().setResourceJsonPointer("/account");
        return source;
    }

    private RestRoutingConfig.HttpDataSource cursorSource() {
        RestRoutingConfig.HttpDataSource source = source(
            "key-cursor", "tenant-binding", "/feed/{tenant}", "knowledge-record"
        );
        source.setRequiredCapabilityGrants(List.of("documents:read"));
        source.getPagination().setStrategy(RestRoutingConfig.Pagination.Strategy.CURSOR);
        source.getPagination().setCursorQuery("after");
        source.getPagination().setNextCursorJsonPointer("/next");
        source.getPagination().setMaxPages(5);
        source.getTrustedResourcePlacements().add(placement(RestRoutingConfig.ResourcePlacement.Target.PATH, "tenant"));
        source.getTrustedResourcePlacements().add(placement(RestRoutingConfig.ResourcePlacement.Target.HEADER, "X-Tenant-Scope"));
        source.getMapping().setRecordsJsonPointer("/records");
        source.getMapping().setResourceJsonPointer("/tenant");
        return source;
    }

    private RestRoutingConfig.HttpDataSource source(
        String profileRef,
        String bindingRef,
        String path,
        String entityType
    ) {
        RestRoutingConfig.HttpDataSource source = new RestRoutingConfig.HttpDataSource();
        source.setSourceVersion("fixture-source-v1");
        source.setConnectionProfileRef(profileRef);
        source.setProtectedResourceBindingRef(bindingRef);
        source.setPath(path);
        source.setMethod("GET");
        source.setVectorSpace(entityType);
        source.setEntityType(entityType);
        source.getMapping().setIdJsonPointer("/id");
        source.getMapping().setContentFields(Map.of("title", "/title", "name", "/name"));
        source.getMapping().setEntityFields(Map.of("title", "/title", "name", "/name"));
        source.getMapping().setMetadataFields(Map.of());
        return source;
    }

    private RestRoutingConfig.ResourcePlacement placement(RestRoutingConfig.ResourcePlacement.Target target, String field) {
        RestRoutingConfig.ResourcePlacement placement = new RestRoutingConfig.ResourcePlacement();
        placement.setTarget(target);
        placement.setField(field);
        return placement;
    }

    private void respondToDataSync(
        HttpExchange exchange,
        List<JsonNode> indexedOperations,
        boolean fail
    ) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("X-Integration-Key")).isEqualTo("runtime-fixture-key");
        JsonNode request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
        ArrayNode results = OBJECT_MAPPER.createArrayNode();
        for (JsonNode operation : request.path("operations")) {
            indexedOperations.add(operation.deepCopy());
            ObjectNode result = results.addObject();
            result.put("success", !fail);
            result.put("errorCode", fail ? "FIXTURE_INDEX_FAILURE" : null);
            result.set("metadata", OBJECT_MAPPER.createObjectNode());
        }
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("success", !fail);
        response.set("results", results);
        respond(exchange, fail ? 207 : 200, OBJECT_MAPPER.writeValueAsString(response));
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        if (uri.getRawQuery() == null) {
            return values;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : ""
            );
        }
        return values;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record Services(
        ProviderTokenService tokenService,
        ProviderHttpClient providerClient,
        HttpDataSyncService syncService
    ) {
    }
}
