package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.core.AIEmbeddingService;
import ai.fabric.core.AICoreService;
import ai.fabric.core.AISearchService;
import ai.fabric.dto.AdvancedRAGRequest;
import ai.fabric.dto.RAGRequest;
import ai.fabric.dto.RAGResponse;
import ai.fabric.embedding.EmbeddingProvider;
import ai.fabric.indexing.worker.IndexingOperationExecutor;
import ai.fabric.provider.onnx.ONNXEmbeddingProvider;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.rag.config.RAGAutoConfiguration;
import ai.fabric.rag.service.AdvancedRAGService;
import ai.fabric.prompt.ClasspathPromptTemplateStore;
import ai.fabric.config.PromptBundleProperties;
import ai.fabric.prompt.PromptRenderer;
import ai.fabric.prompt.PromptTemplateResolver;
import ai.fabric.spi.RAGProvider;
import ai.fabric.service.VectorManagementService;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.DefaultResourceLoader;

class RuntimeProviderBudgetSpringConfigurationTest extends RuntimeBudgetFixture.TestScope {
    private static final String KEY = "fixture-config-key-not-a-provider-credential";

    @Test
    void disabledOrAbsentFlagAddsNoBeansAndRequiresNoBudgetConfiguration() {
        for (String[] properties : new String[][]{{}, {"loomai.provider-budget.enabled=false"}}) {
            base().withPropertyValues(properties).run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(RuntimeBudgetBindingService.class)
                    .doesNotHaveBean(ProviderBudgetLedger.class).doesNotHaveBean(RuntimeProviderBudgetAdminController.class)
                    .doesNotHaveBean(RuntimeBudgetTurnScopes.class).doesNotHaveBean(RuntimeBudgetHealthGroups.class)
                    .doesNotHaveBean(BudgetGuardedSpringAiModelResolver.class).doesNotHaveBean(RuntimeBudgetStorage.class)
                    .doesNotHaveBean("advancedRagSearchExecutor");
            });
        }
    }

    @Test
    void releasedAdvancedRagUsesTheFundedTurnAndCannotHideProviderDenials() throws Exception {
        for (boolean denied : new boolean[]{false, true}) {
            var fixture = new RuntimeBudgetFixture();
            enabled(fixture).withConfiguration(AutoConfigurations.of(RAGAutoConfiguration.class))
                .withBean(AISearchService.class, () -> mock(AISearchService.class))
                .withBean(AICoreService.class, () -> mock(AICoreService.class))
                .withBean(RAGProvider.class, () -> mock(RAGProvider.class))
                .withBean(PromptRenderer.class, PromptRenderer::new)
                .withBean(PromptTemplateResolver.class, () -> new PromptTemplateResolver(
                    new ClasspathPromptTemplateStore(new DefaultResourceLoader()), new PromptBundleProperties()))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AdvancedRAGService.class);
                    assertThat(context.getBeansOfType(ExecutorService.class)).isEmpty();
                    var budgets = context.getBean(RuntimeBudgetBindingService.class);
                    var scope = context.getBean(RuntimeBudgetConfiguration.class).scope();
                    var approval = new ProviderBudgetLedger.Approval("advanced-rag-fixture", scope,
                        APPROVAL.limits(), Instant.parse("2099-01-01T00:00:00Z"));
                    activate(budgets, budgets.initialize(allocation(approval)));
                    var turns = context.getBean(RuntimeBudgetTurnScopes.class);
                    var core = context.getBean(AICoreService.class);
                    when(core.generateText(any())).thenReturn("expanded one\nexpanded two");
                    var provider = context.getBean(RAGProvider.class);
                    var calls = new AtomicInteger();
                    var owner = Thread.currentThread();
                    when(provider.performRag(any(RAGRequest.class))).thenAnswer(invocation -> {
                        assertThat(Thread.currentThread()).isSameAs(owner);
                        var capture = turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING);
                        calls.incrementAndGet();
                        if (denied) {
                            capture.observer().onDenied(new OpenAiBudgetInvocation.Denial(
                                capture.operationId(), UUID.randomUUID(), BudgetException.Code.LIMIT_EXCEEDED, null));
                        }
                        return RAGResponse.builder().success(!denied).documents(denied ? List.of() : List.of(
                            RAGResponse.RAGDocument.builder().id("policy").content("Approved delivery policy")
                                .score(0.9).similarity(0.9).build())).build();
                    });
                    var identity = RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
                        .subjectId("advanced-rag-owner").authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
                        .customerId("customer").tenantId("tenant").deploymentId("deployment")
                        .expiresAt(Instant.parse("2099-01-01T00:00:00Z")).grantedScopes(List.of("chat:query")).build()).build();
                    try (var turn = turns.openChat(identity, null)) {
                        var response = context.getBean(AdvancedRAGService.class).performAdvancedRAG(
                            AdvancedRAGRequest.builder().query("delivery policy").expansionLevel(2)
                                .rerankingStrategy("hybrid").build());
                        assertThat(calls).hasValue(3);
                        assertThat(response.getDocuments()).hasSize(denied ? 0 : 1);
                        var result = ai.fabric.intent.orchestration.OrchestrationResult.builder()
                            .type(ai.fabric.intent.orchestration.OrchestrationResultType.INFORMATION_PROVIDED)
                            .success(true).build();
                        turn.verifyBeforeResultCommit(result);
                        assertThat(result.isSuccess()).isEqualTo(!denied);
                        assertThat(result.getErrorCode()).isEqualTo(denied ? RuntimeBudgetTurnScopes.INTERRUPTION_CODE : null);
                    }
                    assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(1);
                });
        }
    }

    @Test
    void disablingAdvancedRagDoesNotInstallAnExecutorInTheFundedProfile() throws Exception {
        enabled(new RuntimeBudgetFixture()).withPropertyValues("ai.infrastructure.rag.advanced.enabled=false")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("advancedRagSearchExecutor"));
    }

    @Test
    void unguardedRuntimeKeepsTheFrameworkParallelExecutor() {
        base().withConfiguration(AutoConfigurations.of(RAGAutoConfiguration.class)).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(RuntimeBudgetTurnScopes.class);
            assertThat(context.getBean("advancedRagSearchExecutor")).isInstanceOf(ExecutorService.class);
        });
    }

    @Test
    void explicitHardModeBootsUninitializedAndUsesIndependentConnectionsWithoutReplacingPrimaryDataSource() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        enabled(fixture).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(RuntimeProviderBudgetAdminController.class)
                .hasSingleBean(IndexingOperationExecutor.class).hasSingleBean(BudgetGuardedSpringAiModelResolver.class);
            var budgets = context.getBean(RuntimeBudgetBindingService.class);
            assertThat(budgets.readiness().ready()).isFalse();
            assertThat(context.getBean(RuntimeProviderBudgetHealthIndicator.class).health().getStatus().getCode()).isEqualTo("DOWN");
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
            assertThat(context.getBean(DataSource.class)).isSameAs(fixture.dataSource);
            assertThat(context.getBean(RuntimeBudgetStorage.class).independentDataSource()).isNotSameAs(fixture.dataSource);
            assertThat(context.getBean(RuntimeBudgetConfiguration.class).tenantId()).isEqualTo("tenant");
            var scope = context.getBean(RuntimeBudgetConfiguration.class).scope();
            var approval = new ProviderBudgetLedger.Approval("private-cp-approved", scope, APPROVAL.limits(), Instant.parse("2099-01-01T00:00:00Z"));
            var initialized = budgets.initialize(allocation(approval));
            assertThat(context.getBean(RuntimeProviderBudgetHealthIndicator.class).health().getStatus().getCode()).isEqualTo("DOWN");
            activate(budgets, initialized);
            assertThat(context.getBean(RuntimeProviderBudgetHealthIndicator.class).health().getStatus().getCode()).isEqualTo("UP");
        });
    }

    @Test
    void outputCapModeIsRequiredAndNoCompatibilityModeIsImplicitlyAccepted() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        for (String mode : new String[]{"", "PRESERVE_FRAMEWORK", "max_completion_tokens"}) {
            enabled(fixture).withPropertyValues("loomai.provider-budget.output-cap-mode=" + mode)
                .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void onnxBeanDeclaredAsTheEmbeddingInterfaceIsResolvedByItsQualifiedName() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        enabled(fixture).withAllowBeanDefinitionOverriding(true).withUserConfiguration(InterfaceDeclaredOnnx.class)
            .withInitializer(context -> context.getBeanFactory().registerSingleton("unrelatedEmbeddingProvider", mock(EmbeddingProvider.class)))
            .withBean(AIProviderConfig.class, () -> {
                var providers = providers();
                providers.setEmbeddingProvider("onnx");
                return providers;
            }).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(BudgetGuardedSpringAiModelResolver.class);
                var onnx = context.getBean("onnxEmbeddingProvider", EmbeddingProvider.class);
                verify(onnx).isAvailable();
                verify(onnx, never()).generateEmbedding(any());
                assertThat(context.getBean(RuntimeBudgetBindingService.class).readiness().ready()).isFalse();
            });
    }

    @Test
    void realSpringAndFlywayV20BootUninitializedWithLiveAdministrationButReadinessDown() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        enabled(source).withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class,
                HealthContributorRegistryAutoConfiguration.class, HealthEndpointAutoConfiguration.class,
                AvailabilityProbesAutoConfiguration.class))
            .withBean(ApplicationAvailability.class, ApplicationAvailabilityBean::new)
            .withPropertyValues("spring.flyway.locations=classpath:db/runtime-migration", "management.endpoint.health.probes.enabled=true",
                "management.endpoint.health.group.liveness.include=livenessState",
                "management.endpoint.health.group.readiness.include=readinessState")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(RuntimeProviderBudgetAdminController.class);
                assertThat(context.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("20");
                AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
                AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
                var health = context.getBean(HealthEndpoint.class);
                assertThat(health.healthForPath("liveness").getStatus().getCode()).isEqualTo("UP");
                assertThat(health.healthForPath("readiness").getStatus().getCode()).isEqualTo("DOWN");
                assertThat(context.getBean(RuntimeBudgetBindingService.class).readiness().code()).isEqualTo("NOT_INITIALIZED");
                var jdbc = new JdbcTemplate(source);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_budget_approval", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_budget_attempt", Long.class)).isZero();
                verifyNoInteractions(context.getBean(AIEmbeddingService.class), context.getBean(VectorDatabaseService.class));
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceDeclaredOnnx {
        @Bean
        EmbeddingProvider onnxEmbeddingProvider() {
            var provider = mock(ONNXEmbeddingProvider.class);
            when(provider.isAvailable()).thenReturn(true);
            return provider;
        }
    }

    private static ApplicationContextRunner base() {
        return new ApplicationContextRunner().withUserConfiguration(RuntimeProviderBudgetSpringConfiguration.class,
            RuntimeProviderBudgetAdminController.class);
    }

    private static ApplicationContextRunner enabled(RuntimeBudgetFixture fixture) {
        return enabled(fixture.dataSource);
    }

    private static ApplicationContextRunner enabled(DriverManagerDataSource source) {
        var properties = new DataSourceProperties();
        properties.setUrl(source.getUrl()); properties.setUsername("sa"); properties.setPassword("");
        properties.setDriverClassName("org.h2.Driver");
        return base().withPropertyValues("loomai.provider-budget.enabled=true",
            "AI_FABRIC_RUNTIME_CUSTOMER_ID=customer", "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=deployment", "AI_FABRIC_RUNTIME_TENANT_ID=tenant",
            "LOOMAI_RUNTIME_TARGET_PROFILE_ID=target", "LOOMAI_PROVIDER_BUDGET_ENVIRONMENT=staging",
            "LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY=" + ReviewedOpenAiRateCard.credentialIdentity(KEY),
            "LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE=" + ReviewedOpenAiRateCard.REFERENCE,
            "loomai.provider-budget.output-cap-mode=MAX_COMPLETION_TOKENS")
            .withBean(DataSourceProperties.class, () -> properties).withBean(DataSource.class, () -> source)
            .withBean(AIProviderConfig.class, RuntimeProviderBudgetSpringConfigurationTest::providers)
            .withBean(AIEmbeddingService.class, () -> mock(AIEmbeddingService.class))
            .withBean(VectorManagementService.class, () -> mock(VectorManagementService.class))
            .withBean(VectorDatabaseService.class, () -> mock(VectorDatabaseService.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RuntimeRequestAuthResolver.class, () -> mock(RuntimeRequestAuthResolver.class));
    }

    private static AIProviderConfig providers() {
        var providers = new AIProviderConfig();
        providers.getOpenai().setApiKey(KEY); providers.getOpenai().setModel(ReviewedOpenAiRateCard.CHAT_MODEL);
        providers.getOpenai().setMaxTokens(300); providers.getOpenai().setEmbeddingModel(ReviewedOpenAiRateCard.EMBEDDING_MODEL);
        providers.setEmbeddingProvider("openai");
        return providers;
    }
}
