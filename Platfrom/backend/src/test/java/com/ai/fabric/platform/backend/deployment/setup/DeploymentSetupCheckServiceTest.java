package com.ai.fabric.platform.backend.deployment.setup;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentConfigCompiler;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.setup.SetupCheckContracts.Mode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

class DeploymentSetupCheckServiceTest {
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentDraftRepository drafts = mock(DeploymentDraftRepository.class);
    final DeploymentAccessService access = mock(DeploymentAccessService.class);
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final PlatformSecretRepository rows = mock(PlatformSecretRepository.class);
    final DeploymentProviderSecretResolutionService providers = mock(DeploymentProviderSecretResolutionService.class);
    final SetupProbeHttpClient http = mock(SetupProbeHttpClient.class);
    final ObjectMapper mapper = new ObjectMapper();
    final DeploymentDraftEntity draft = new DeploymentDraftEntity();
    final DeploymentEntity deployment = new DeploymentEntity();
    final DeploymentSetupCheckService checks = new DeploymentSetupCheckService(deployments, drafts, access, secrets, rows, providers, http, mapper, new DeploymentConfigCompiler(mapper));
    @BeforeEach void setup() {
        deployment.setId("dep-one"); deployment.setCustomerId("customer-one");
        draft.setId("draft-one"); draft.setDeploymentId("dep-one"); draft.setRevisionNumber(1);
        draft.setProviderConfigJson("{\"llmProvider\":\"anthropic\",\"embeddingProvider\":\"onnx\",\"vectorStrategy\":\"lucene\"}");
        draft.setActionsConfigJson("{}"); draft.setRoutingConfigJson("{}"); draft.setMarketplaceDatasetConfigJson("{\"datasets\":[]}");
        when(deployments.findById("dep-one")).thenReturn(Optional.of(deployment));
        when(drafts.findTopByDeploymentIdOrderByRevisionNumberDesc("dep-one")).thenReturn(Optional.of(draft));
        own("CUSTOMER_ONE_KEY", "dep-one", null);
        when(secrets.resolveSecretForDeployment("CUSTOMER_ONE_KEY", "dep-one", null)).thenReturn("private-key-one");
        when(http.probe(anyString(), anyMap())).thenReturn(new SetupProbeHttpClient.Result("VERIFIED", "Safe GET accepted.", 200));
    }
    void own(String reference, String owner, String target) {
        var row = new PlatformSecretEntity(); row.setName(reference); row.setDeploymentId(owner); row.setTargetProfileId(target);
        when(rows.findById(reference)).thenReturn(Optional.of(row));
    }
    void actions(String key, String path) {
        draft.setRoutingConfigJson("""
            {"connector":{"upstream":{"base-url":"https://service.example/api","setup-check-path":"%s",
            "auth":{"type":"API_KEY","header":"X-API-Key","value":"${%s}"}}},
            "actions":{"search":{"method":"GET","path":"/inventory"},"create":{"method":"POST","path":"/leads"}}}
            """.formatted(path, key));
    }
    @Test void inspectNeverPerformsNetworkOrClaimsUntestedServicesPassed() {
        actions("CUSTOMER_ONE_KEY", "/health");
        var result = checks.inspect("dep-one");
        assertThat(result.verifiedCount()).isZero(); assertThat(result.failedCount()).isZero();
        assertThat(result.services()).allMatch(item -> "NOT_TESTED".equals(item.status()));
        verifyNoInteractions(http); verify(access).requireDeploymentAccess(deployment);
    }
    @Test void savedMcpMetadataEvidenceJoinsExistingSummaryWithoutExecutingTools() {
        var mcp = mock(com.ai.fabric.platform.backend.mcp.runtime.McpConnectionReadiness.class);
        org.springframework.test.util.ReflectionTestUtils.setField(checks, "mcpReadiness", mcp);
        when(mcp.inspect(eq("dep-one"), any())).thenReturn(java.util.List.of(new SetupCheckContracts.ServiceResult(
            "mcp-one", "Public documentation", "VERIFIED", "Saved tool metadata is current; business tools were not executed.",
            java.time.Instant.now(), java.util.List.of("search_docs"), null)));
        var result = checks.inspect("dep-one");
        assertThat(result.verifiedCount()).isEqualTo(1);
        verify(mcp).inspect(eq("dep-one"), any());
        verifyNoMoreInteractions(mcp); verifyNoInteractions(http);
    }
    @Test void sharedActionsUseOneSafeGetNotBusinessEndpoints() {
        actions("CUSTOMER_ONE_KEY", "/health");
        var result = checks.run("dep-one", ALL);
        assertThat(result.verifiedCount()).isEqualTo(1);
        assertThat(result.services()).filteredOn(item -> "VERIFIED".equals(item.status())).singleElement()
                .satisfies(item -> assertThat(item.usedBy()).containsExactly("search", "create"));
        verify(http).probe("https://service.example/api/health", Map.of("X-API-Key", "private-key-one"));
        verifyNoMoreInteractions(http); verify(access).requireDeploymentEditorAccess(deployment);
    }
    @Test void changedModeReusesMatchingChecksAndUnrelatedEdits() {
        actions("CUSTOMER_ONE_KEY", "/health"); checks.run("dep-one", CHANGED);
        draft.setRevisionNumber(2);
        var result = checks.run("dep-one", CHANGED);
        assertThat(result.verifiedCount()).isEqualTo(1); assertThat(result.revisionNumber()).isEqualTo(2);
        verify(http, times(1)).probe(anyString(), anyMap());
    }
    @Test void credentialRotationInvalidatesPreviousSuccessBeforeAnotherNetworkCall() {
        actions("CUSTOMER_ONE_KEY", "/health"); checks.run("dep-one", ALL);
        when(secrets.resolveSecretForDeployment("CUSTOMER_ONE_KEY", "dep-one", null)).thenReturn("rotated-key");
        assertThat(checks.inspect("dep-one").verifiedCount()).isZero();
        checks.run("dep-one", CHANGED);
        verify(http).probe("https://service.example/api/health", Map.of("X-API-Key", "rotated-key"));
    }
    @Test void changedEndpointCannotInheritSuccess() {
        actions("CUSTOMER_ONE_KEY", "/health"); checks.run("dep-one", ALL);
        actions("CUSTOMER_ONE_KEY", "/health2");
        assertThat(checks.inspect("dep-one").verifiedCount()).isZero();
    }
    @Test void retryFailedDoesNotRepeatSuccessfulServices() {
        actions("CUSTOMER_ONE_KEY", "/health");
        when(http.probe(anyString(), anyMap())).thenReturn(new SetupProbeHttpClient.Result("FAILED", "Authorization rejected.", 403))
                .thenReturn(new SetupProbeHttpClient.Result("VERIFIED", "Safe GET accepted.", 200));
        assertThat(checks.run("dep-one", ALL).failedCount()).isEqualTo(1);
        assertThat(checks.run("dep-one", FAILED).verifiedCount()).isEqualTo(1);
        checks.run("dep-one", FAILED);
        verify(http, times(2)).probe(anyString(), anyMap());
    }
    @Test void absentSafePathDoesNotCallTheActionEvenWhenGet() {
        actions("CUSTOMER_ONE_KEY", "");
        assertThat(checks.run("dep-one", ALL).services()).anyMatch(item -> item.message().contains("No safe GET"));
        verifyNoInteractions(http);
    }
    @Test void retryFailedCanProceedAfterAMissingCredentialIsProvided() {
        actions("CUSTOMER_ONE_KEY", "/health");
        when(secrets.resolveSecretForDeployment("CUSTOMER_ONE_KEY", "dep-one", null)).thenReturn("");
        assertThat(checks.run("dep-one", ALL).failedCount()).isEqualTo(1);
        verifyNoInteractions(http);
        when(secrets.resolveSecretForDeployment("CUSTOMER_ONE_KEY", "dep-one", null)).thenReturn("provided-key");
        assertThat(checks.run("dep-one", FAILED).verifiedCount()).isEqualTo(1);
        verify(http).probe("https://service.example/api/health", Map.of("X-API-Key", "provided-key"));
    }
    @Test void globalCredentialCannotBeSentToCustomEndpoint() {
        actions("OPENAI_API_KEY", "/health"); when(rows.findById("OPENAI_API_KEY")).thenReturn(Optional.empty());
        assertThat(checks.run("dep-one", ALL).failedCount()).isEqualTo(1);
        verifyNoInteractions(http); verify(secrets, never()).resolveSecretForDeployment(eq("OPENAI_API_KEY"), any(), any());
    }
    @Test void foreignAndTargetCredentialsAreNotResolvedOrSent() {
        for (String[] scope : new String[][]{{"dep-other", null}, {"dep-one", "production"}}) {
            own("CUSTOMER_ONE_KEY", scope[0], scope[1]); actions("CUSTOMER_ONE_KEY", "/health");
            assertThat(checks.run("dep-one", ALL).failedCount()).isEqualTo(1);
        }
        verifyNoInteractions(http); verify(secrets, never()).resolveSecretForDeployment(any(), any(), any());
    }
    @Test void permissionIsRecheckedEvenWithCachedResults() {
        actions("CUSTOMER_ONE_KEY", "/health"); checks.run("dep-one", ALL);
        doThrow(new ResponseStatusException(FORBIDDEN)).when(access).requireDeploymentEditorAccess(deployment);
        assertThatThrownBy(() -> checks.run("dep-one", CHANGED)).isInstanceOf(ResponseStatusException.class);
        verify(http, times(1)).probe(anyString(), anyMap());
    }
    @Test void resultsNeverContainCredentialsEndpointOrRawException() throws Exception {
        actions("CUSTOMER_ONE_KEY", "/health");
        when(secrets.resolveSecretForDeployment(any(), any(), any())).thenThrow(new IllegalStateException("sensitive-value https://private.example"));
        String result = mapper.findAndRegisterModules().writeValueAsString(checks.run("dep-one", ALL));
        assertThat(result).doesNotContain("private-key-one", "sensitive-value", "service.example", "CUSTOMER_ONE_KEY", "private.example");
    }
    @Test void tokenExchangeIsNotPerformedAsSideEffect() {
        draft.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"datasetId":"inventory","syncConnector":{"connectorType":"HTTP_JSON",
            "connectionProfile":{"baseUrl":"https://service.example","setupCheckPath":"/health","auth":{"strategy":"FORM_TOKEN_EXCHANGE"}}}}]}
            """);
        assertThat(checks.run("dep-one", ALL).services()).anyMatch(item -> item.message().contains("No token request"));
        verifyNoInteractions(http);
    }
    @Test void dataProfileUsesSuppliedScopedAuthentication() {
        draft.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"datasetId":"inventory","syncConnector":{"connectorType":"HTTP_JSON",
            "connectionProfile":{"baseUrl":"https://service.example","setupCheckPath":"/health",
            "auth":{"strategy":"API_KEY","apiKeyHeader":"X-API-Key","apiKeyValue":"${CUSTOMER_ONE_KEY}"}}}}]}
            """);
        assertThat(checks.run("dep-one", ALL).verifiedCount()).isEqualTo(1);
        verify(http).probe("https://service.example/health", Map.of("X-API-Key", "private-key-one"));
    }
    @Test void providerChecksOnlyMetadataNeverPaidInference() {
        draft.setProviderConfigJson("{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\",\"vectorStrategy\":\"lucene\"}");
        when(providers.resolve("dep-one", "OPENAI_API_KEY", null)).thenReturn(
                new DeploymentProviderSecretResolutionService.ResolvedSecretValue(
                        new DeploymentSecretResolutionSummary("dep-one", "OPENAI_API_KEY", "OpenAI", false, true, "ALLOW_STANDARD_PRECEDENCE",
                                "GLOBAL_PLATFORM", "PLATFORM", "OPENAI_API_KEY", null, false, "RESOLVED", "Ready"), "vendor-secret", null));
        var result = checks.run("dep-one", ALL);
        assertThat(result.verifiedCount()).isEqualTo(1);
        verify(http).probe("https://api.openai.com/v1/models", Map.of("Authorization", "Bearer vendor-secret"));
        verifyNoMoreInteractions(http);
    }
    @Test void providerGlobalKeyIsBlockedForArbitraryOpenaiCompatibleHost() {
        draft.setProviderConfigJson("{\"llmProvider\":\"openai\",\"openaiBaseUrl\":\"https://untrusted.example\",\"vectorStrategy\":\"lucene\"}");
        when(providers.resolve("dep-one", "OPENAI_API_KEY", null)).thenReturn(
                new DeploymentProviderSecretResolutionService.ResolvedSecretValue(
                        new DeploymentSecretResolutionSummary("dep-one", "OPENAI_API_KEY", "OpenAI", false, true, "ALLOW_STANDARD_PRECEDENCE",
                                "GLOBAL_PLATFORM", "PLATFORM", "OPENAI_API_KEY", null, false, "RESOLVED", "Ready"), "vendor-secret", null));
        assertThat(checks.run("dep-one", ALL).verifiedCount()).isZero(); verifyNoInteractions(http);
    }
    @ParameterizedTest @ValueSource(strings = {"https://api.openai.com", "https://api.openai.com/v1", "https://api.openai.com/v1/chat/completions"})
    void openaiBaseFormatsUseTheSameReadOnlyMetadataEndpoint(String base) {
        draft.setProviderConfigJson("{\"llmProvider\":\"openai\",\"openaiBaseUrl\":\"" + base + "\",\"embeddingProvider\":\"onnx\",\"vectorStrategy\":\"lucene\"}");
        when(providers.resolve("dep-one", "OPENAI_API_KEY", null)).thenReturn(
                new DeploymentProviderSecretResolutionService.ResolvedSecretValue(
                        new DeploymentSecretResolutionSummary("dep-one", "OPENAI_API_KEY", "OpenAI", false, true, "ALLOW_STANDARD_PRECEDENCE",
                                "GLOBAL_PLATFORM", "PLATFORM", "OPENAI_API_KEY", null, false, "RESOLVED", "Ready"), "vendor-secret", null));
        assertThat(checks.run("dep-one", ALL).verifiedCount()).isEqualTo(1);
        verify(http).probe("https://api.openai.com/v1/models", Map.of("Authorization", "Bearer vendor-secret"));
        verifyNoMoreInteractions(http);
    }
    @Test void inlineRouteAndExplicitHeaderOverrideUseTheExistingCompilerPrecedence() {
        draft.setActionsConfigJson("""
            {"actions":[{"name":"search","displayName":"Find stock","route":{"url":"https://service.example/inventory",
            "setup-check-path":"/health","headers":{"X-API-Key":"${CUSTOMER_ONE_KEY}","X-Version":"one"}}}]}
            """);
        draft.setRoutingConfigJson("{\"actions\":{\"search\":{\"headers\":{\"X-Version\":\"two\"}}}}");
        var result = checks.run("dep-one", ALL);
        assertThat(result.verifiedCount()).isEqualTo(1);
        assertThat(result.services()).filteredOn(item -> "VERIFIED".equals(item.status())).singleElement()
                .satisfies(item -> assertThat(item.usedBy()).containsExactly("Find stock"));
        verify(http).probe("https://service.example/health", Map.of("X-API-Key", "private-key-one", "X-Version", "two"));
    }
    @Test void allowedHostMismatchAndMissingAuthenticationNeverMakeARequest() {
        draft.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"datasetId":"inventory","syncConnector":{"connectorType":"HTTP_JSON",
            "connectionProfile":{"baseUrl":"https://service.example","allowedHosts":["other.example"],"setupCheckPath":"/health",
            "auth":{"strategy":"API_KEY","apiKeyHeader":"X-API-Key","apiKeyValue":"${CUSTOMER_ONE_KEY}"}}}}]}
            """);
        assertThat(checks.run("dep-one", ALL).failedCount()).isEqualTo(1); verifyNoInteractions(http);
        actions("CUSTOMER_ONE_KEY", "/health"); draft.setMarketplaceDatasetConfigJson("{}");
        when(secrets.resolveSecretForDeployment("CUSTOMER_ONE_KEY", "dep-one", null)).thenReturn("");
        assertThat(checks.run("dep-one", ALL).failedCount()).isEqualTo(1); verifyNoInteractions(http);
    }
}
