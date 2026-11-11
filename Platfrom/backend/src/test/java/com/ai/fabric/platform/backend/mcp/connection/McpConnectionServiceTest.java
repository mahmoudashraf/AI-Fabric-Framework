package com.ai.fabric.platform.backend.mcp.connection;

import static com.ai.fabric.platform.backend.mcp.connection.McpConnectionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.security.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class McpConnectionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final McpConnectionRepository connections = mock(McpConnectionRepository.class);
    private final McpDiscoverySnapshotRepository snapshots = mock(McpDiscoverySnapshotRepository.class);
    private final McpImportReceiptRepository receipts = mock(McpImportReceiptRepository.class);
    private final McpOAuthAttemptRepository attempts = mock(McpOAuthAttemptRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final DeploymentTargetProfileService targets = mock(DeploymentTargetProfileService.class);
    private final DeploymentService drafts = mock(DeploymentService.class);
    private final McpOwnedSecrets secrets = mock(McpOwnedSecrets.class);
    private final McpBusinessOAuthService oauth = mock(McpBusinessOAuthService.class);
    private final McpConnectionGatewayClient gateway = mock(McpConnectionGatewayClient.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final McpConnectionProperties properties = new McpConnectionProperties();
    private final McpConnectionService service = new McpConnectionService(connections, snapshots, receipts, attempts,
        deployments, access, targets, drafts, secrets, oauth, gateway, new McpToolCatalogue(mapper, properties), properties, mapper, audit,
        new McpConnectionExecutionResolver(connections, deployments, targets, secrets, oauth, properties, mapper));
    private final DeploymentEntity brain = new DeploymentEntity();
    private final Map<String, McpConnectionEntity> connectionStore = new HashMap<>();
    private final Map<String, McpDiscoverySnapshotEntity> snapshotStore = new HashMap<>();
    private final Map<String, McpImportReceiptEntity> receiptStore = new HashMap<>();
    private ObjectNode draftConfig;
    private int revision = 3;

    @BeforeEach void setup() throws Exception {
        signIn(PlatformRole.CUSTOMER_ADMIN);
        brain.setId("dep-one"); brain.setCustomerId("customer-one"); brain.setStatus("DRAFT");
        when(deployments.findById("dep-one")).thenReturn(Optional.of(brain));
        when(deployments.findByIdForUpdate("dep-one")).thenReturn(Optional.of(brain));
        when(access.requireDeploymentEditorAccess(brain)).thenReturn(brain);
        var target = new DeploymentTargetProfileEntity(); target.setId("target-stage"); target.setActive(true); target.setEnvironmentName("staging");
        when(targets.requireActiveProfile("target-stage")).thenReturn(target);
        when(connections.save(any())).thenAnswer(a -> { var c = (McpConnectionEntity) a.getArgument(0); connectionStore.put(c.getId(), c); return c; });
        when(connections.findById(anyString())).thenAnswer(a -> Optional.ofNullable(connectionStore.get(a.getArgument(0))));
        when(connections.findLocked(anyString())).thenAnswer(a -> Optional.ofNullable(connectionStore.get(a.getArgument(0))));
        when(connections.findByDeploymentIdOrderByCreatedAtAsc(anyString())).thenAnswer(a -> new ArrayList<>(connectionStore.values()));
        when(snapshots.save(any())).thenAnswer(a -> { var s = (McpDiscoverySnapshotEntity) a.getArgument(0); snapshotStore.put(s.getId(), s); return s; });
        when(snapshots.findById(anyString())).thenAnswer(a -> Optional.ofNullable(snapshotStore.get(a.getArgument(0))));
        when(receipts.save(any())).thenAnswer(a -> { var r = (McpImportReceiptEntity) a.getArgument(0); receiptStore.put(r.getId(), r); return r; });
        when(receipts.findById(anyString())).thenAnswer(a -> Optional.ofNullable(receiptStore.get(a.getArgument(0))));
        when(gateway.discover(anyString(), any(), anyMap())).thenReturn(discovery());
        draftConfig = (ObjectNode) mapper.readTree("""
            {"futureRoot":{"retained":true},"actions":[{"name":"existing_action","description":"Keep me","accessMode":"READ"}],
             "mcpServers":[{"serverRef":"existing_server","endpointUrl":"https://another.example/mcp"}]}
            """);
        when(drafts.getActiveDraftForDeployment("dep-one")).thenAnswer(a -> draft());
        when(drafts.updateDraft(eq("draft-one"), any())).thenAnswer(a -> {
            draftConfig = ((UpdateDeploymentDraftRequest) a.getArgument(1)).actionsConfig().deepCopy(); revision++; return draft();
        });
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void publicConnectionNeverReadsSecretsOrCreatesOAuthGrant() {
        var connection = service.create("dep-one", publicRequest(null));
        assertThat(connection.connectionModel()).isEqualTo("PUBLIC"); assertThat(connection.authStatus()).isEqualTo("NOT_REQUIRED");
        service.list("dep-one", "target-stage");
        verifyNoInteractions(secrets, oauth, attempts, gateway);
        assertThat(connectionStore.get(connection.id()).getEncryptedTokens()).isNull();
    }

    @Test void publicModeRejectsCredentialSmugglingAndPersonalModel() {
        assertBad(() -> service.create("dep-one", new SaveConnection(null, "Public", "https://tools.example/mcp", "target-stage", "PUBLIC", "AUTO",
            new AuthSettings("NONE", "CUSTOMER_OTHER", null, List.of(), null))));
        assertBad(() -> service.create("dep-one", new SaveConnection(null, "Personal", "https://tools.example/mcp", "target-stage", "PERSONAL", "AUTO", null)));
        verify(connections, never()).save(any()); verifyNoInteractions(secrets, oauth, gateway);
    }

    @Test void editingRequiresAuthenticationAndActualEditorAccessAndStaffDelegation() {
        SecurityContextHolder.clearContext(); assertStatus(HttpStatus.UNAUTHORIZED, () -> service.list("dep-one", null));
        signIn(PlatformRole.PLATFORM_OPERATOR); service.list("dep-one", null);
        verify(access).requireStaffCustomerAccess("customer-one");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireStaffCustomerAccess("customer-one");
        assertStatus(HttpStatus.FORBIDDEN, () -> service.create("dep-one", publicRequest(null)));
        verify(connections, never()).save(any());
    }

    @Test void staticCredentialIsOwnedAndNeverReturnedOrStoredInDiscovery() throws Exception {
        var connection = business();
        when(secrets.resolve("dep-one", "target-stage", "CUSTOMER_OWN_KEY")).thenReturn("fixture-private-key");
        var snapshot = service.discover("dep-one", connection.id());
        verify(secrets).check("dep-one", "target-stage", "CUSTOMER_OWN_KEY");
        verify(gateway).discover(eq(connection.serverRef()), argThat(server -> "CUSTOMER_OWN_KEY".equals(server.path("auth").path("secretRef").asText())),
            eq(Map.of("CUSTOMER_OWN_KEY", "fixture-private-key")));
        assertThat(mapper.writeValueAsString(service.list("dep-one", null))).doesNotContain("fixture-private-key");
        assertThat(snapshotStore.get(snapshot.id()).getSnapshotJson()).doesNotContain("fixture-private-key");
    }

    @Test void publicDiscoveryIsReadOnlyAndUsesAnImmutableSnapshot() throws Exception {
        var connection = service.create("dep-one", publicRequest(null));
        var snapshot = service.discover("dep-one", connection.id());
        assertThat(snapshot.complete()).isTrue(); assertThat(snapshot.tools()).hasSize(2);
        assertThat(snapshot.tools().getFirst().annotations().path("readOnlyHint").asBoolean()).isTrue();
        // JSON numeric values survive the stored round trip; Jackson numeric node implementation classes need not match.
        assertThat(mapper.writeValueAsString(service.getSnapshot("dep-one", connection.id(), snapshot.id())))
            .isEqualTo(mapper.writeValueAsString(snapshot));
        verify(gateway).discover(eq(connection.serverRef()), any(), eq(Map.of()));
        verifyNoInteractions(drafts, oauth, secrets);
    }

    @Test void defaultMechanicalImportIsConservativeAndPreservesNativeSchemaAndUnrelatedDraft() {
        var connection = service.create("dep-one", publicRequest(null));
        var snapshot = service.discover("dep-one", connection.id());
        var receipt = service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), connection.revision(), List.of(defaultSelection("search"))));
        JsonNode action = draftConfig.path("actions").get(1);
        assertThat(action.path("accessMode").asText()).isEqualTo("READ_WRITE");
        assertThat(action.path("requiresConfirmation").asBoolean()).isTrue();
        assertThat(action.path("anonymousAllowed").asBoolean()).isFalse();
        assertThat(action.path("groundingEligible").asBoolean()).isFalse();
        assertThat(action.path("inputSchema")).isEqualTo(snapshot.tools().getFirst().inputSchema());
        assertThat(action.path("outputSchema")).isEqualTo(snapshot.tools().getFirst().outputSchema());
        assertThat(action.path("execution").path("mcp").path("connectionId").asText()).isEqualTo(connection.id());
        assertThat(action.path("execution").path("mcp").path("toolName").asText()).isEqualTo("search");
        assertThat(action.path("execution").path("retry").path("maxAttempts").asInt()).isEqualTo(1);
        assertThat(draftConfig.path("futureRoot").path("retained").asBoolean()).isTrue();
        assertThat(draftConfig.path("actions").get(0).path("name").asText()).isEqualTo("existing_action");
        assertThat(draftConfig.path("mcpServers").get(0).path("serverRef").asText()).isEqualTo("existing_server");
        assertThat(draftConfig.path("mcpServers").get(1).path("connectionId").asText()).isEqualTo(connection.id());
        assertThat(receipt.createdCount()).isEqualTo(1); assertThat(receipt.updatedCount()).isZero();
        assertThat(receipt.draftRevision()).isEqualTo(4);
        var entity = ArgumentCaptor.forClass(McpImportReceiptEntity.class); verify(receipts).save(entity.capture());
        assertThat(entity.getValue().getReceiptJson()).contains(receipt.id(), "draft-one");
        verifyNoInteractions(oauth);
    }

    @Test void explicitReadWithoutVectorsAndNoConfirmationWriteAreBothSupported() {
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(
            new ToolSelection("search", "READ", false, true, true, true),
            new ToolSelection("book", "WRITE_ONLY", false, false, false, true))));
        assertThat(draftConfig.path("actions").get(1).path("accessMode").asText()).isEqualTo("READ");
        assertThat(draftConfig.path("actions").get(1).path("groundingVectorSpaces").isMissingNode()).isTrue();
        assertThat(draftConfig.path("actions").get(2).path("requiresConfirmation").asBoolean(true)).isFalse();
    }

    @Test void reimportPreservesEditedPoliciesLabelsResolversAndUnrelatedFields() {
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(new ToolSelection("search", "READ", false, true, true, true))));
        var action = (ObjectNode) draftConfig.path("actions").get(1);
        action.put("displayName", "My search").put("description", "My local description").put("futureField", "preserved");
        ((ObjectNode) action.path("params").get(0)).putObject("resolveFrom").put("source", "RUNTIME_CONTEXT").put("field", "subjectId");
        var second = service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(defaultSelection("search"))));
        assertThat(second.createdCount()).isZero(); assertThat(second.updatedCount()).isEqualTo(1);
        JsonNode kept = draftConfig.path("actions").get(1);
        assertThat(kept.path("displayName").asText()).isEqualTo("My search");
        assertThat(kept.path("description").asText()).isEqualTo("My local description");
        assertThat(kept.path("futureField").asText()).isEqualTo("preserved");
        assertThat(kept.path("accessMode").asText()).isEqualTo("READ");
        assertThat(kept.path("requiresConfirmation").asBoolean(true)).isFalse();
        assertThat(kept.path("anonymousAllowed").asBoolean()).isTrue();
        assertThat(kept.path("params").get(0).path("resolveFrom").path("field").asText()).isEqualTo("subjectId");
        assertThat(draftConfig.path("mcpServers")).hasSize(2);
    }

    @Test void importRejectsForeignStaleTamperedIncompleteAndUnselectedTools() {
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        assertBad(() -> service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(defaultSelection("not_in_catalogue")))));
        assertStatus(HttpStatus.CONFLICT, () -> service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 2, List.of(defaultSelection("search")))));
        snapshotStore.get(snapshot.id()).setConnectionId("foreign-connection");
        assertStatus(HttpStatus.CONFLICT, () -> service.getSnapshot("dep-one", connection.id(), snapshot.id()));
        snapshotStore.get(snapshot.id()).setConnectionId(connection.id()); snapshotStore.get(snapshot.id()).setContentHash("tampered");
        assertStatus(HttpStatus.CONFLICT, () -> service.getSnapshot("dep-one", connection.id(), snapshot.id()));
        verify(drafts, never()).updateDraft(anyString(), any());
    }

    @Test void changedConnectionInvalidatesPriorCatalogueWithoutExecutingAnyTool() {
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        var updated = service.update("dep-one", connection.id(), publicRequest(1L));
        assertThat(updated.revision()).isEqualTo(2);
        assertStatus(HttpStatus.CONFLICT, () -> service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 2, List.of(defaultSelection("search")))));
        verify(gateway, never()).execute(anyMap());
    }

    @Test void runtimeResolverRejectsForeignTargetAndReturnsNoSecretsForPublic() {
        var connection = service.create("dep-one", publicRequest(null));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.resolveExecution(connection.id(), "dep-one", "target-prod", "search", "hash"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.resolveExecution(connection.id(), "other-brain", "target-stage", "search", "hash"));
        var resolved = service.resolveExecution(connection.id(), "dep-one", "target-stage", "search", "hash");
        assertThat(resolved.resolvedSecretValues()).isEmpty();
        assertThat(resolved.server().path("allowedTools").get(0).asText()).isEqualTo("search");
        verifyNoInteractions(oauth, gateway, secrets);
    }

    @Test void disconnectDisablesRuntimeAndDiscoveryAndCancelsPendingAuthorization() {
        var connection = service.create("dep-one", publicRequest(null));
        var disabled = service.disconnect("dep-one", connection.id());
        assertThat(disabled.status()).isEqualTo("DISCONNECTED"); verify(attempts).cancelForConnection(eq(connection.id()), any());
        assertStatus(HttpStatus.CONFLICT, () -> service.resolveExecution(connection.id(), "dep-one", "target-stage", "search", "hash"));
        assertStatus(HttpStatus.CONFLICT, () -> service.discover("dep-one", connection.id()));
    }

    @Test void assistantCatalogueIsOwnedMetadataOnlyAndDoesNotInvokeAIOrOAuth() throws Exception {
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        clearInvocations(gateway);
        JsonNode result = service.getAssistantCatalogue("dep-one", connection.id(), snapshot.id(), List.of("search"));
        assertThat(result.path("tools")).hasSize(1); assertThat(result.path("tools").get(0).path("name").asText()).isEqualTo("search");
        assertThat(result.toString()).doesNotContain("endpointUrl", "secretRef", "encrypted", "connectionId");
        assertBad(() -> service.getAssistantCatalogue("dep-one", connection.id(), snapshot.id(), List.of("unknown")));
        verifyNoInteractions(oauth, gateway);
    }

    @Test void httpRoutingHeaderOverridesAreNeverAcceptedAsCredentials() {
        for (String header : List.of("Host", "Cookie", "Mcp-Session-Id", "Content-Type", "Connection", "Sec-Fetch-Site", "X-Key\r\nHost")) {
            assertBad(() -> service.create("dep-one", new SaveConnection(null, "Company", "https://tools.example/mcp", "target-stage", "BUSINESS", "AUTO",
                new AuthSettings("API_KEY_HEADER_SECRET", "CUSTOMER_OWN_KEY", header, null, null))));
        }
        verify(connections, never()).save(any());
    }

    @Test void explicitTargetReuseAndRevocationDoNotCopyTokensOrChangeImportedRevision() {
        var production = new DeploymentTargetProfileEntity(); production.setId("target-prod"); production.setActive(true); production.setEnvironmentName("production");
        when(targets.requireActiveProfile("target-prod")).thenReturn(production);
        var connection = service.create("dep-one", publicRequest(null));
        assertThat(connection.allowedTargetProfileIds()).containsExactly("target-stage");
        assertStatus(HttpStatus.NOT_FOUND, () -> service.resolveExecution(connection.id(), "dep-one", "target-prod", "search", "hash"));
        var enabled = service.bindTarget("dep-one", connection.id(), new TargetBinding(1L, "target-prod", true));
        assertThat(enabled.allowedTargetProfileIds()).containsExactly("target-stage", "target-prod"); assertThat(enabled.revision()).isEqualTo(1);
        var resolved = service.resolveExecution(connection.id(), "dep-one", "target-prod", "search", "hash");
        assertThat(resolved.server().path("targetProfileId").asText()).isEqualTo("target-prod");
        assertThat(resolved.resolvedSecretValues()).isEmpty();
        service.bindTarget("dep-one", connection.id(), new TargetBinding(1L, "target-prod", false));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.resolveExecution(connection.id(), "dep-one", "target-prod", "search", "hash"));
        verifyNoInteractions(oauth, secrets);
    }

    @Test void targetScopedCredentialCannotBeSharedByMerelyEnablingProduction() {
        var production = new DeploymentTargetProfileEntity(); production.setId("target-prod"); production.setActive(true); production.setEnvironmentName("production");
        when(targets.requireActiveProfile("target-prod")).thenReturn(production);
        var connection = business();
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key is staging scoped")).when(secrets).check("dep-one", "target-prod", "CUSTOMER_OWN_KEY");
        assertBad(() -> service.bindTarget("dep-one", connection.id(), new TargetBinding(1L, "target-prod", true)));
        assertThat(service.list("dep-one", null).connections().getFirst().allowedTargetProfileIds()).containsExactly("target-stage");
    }

    @Test void fractionalNativeSchemaBoundsKeepFullPrecisionAcrossDiscoveryStorageAndImport() throws Exception {
        ObjectNode discovery = (ObjectNode) discovery();
        var exact = new java.math.BigDecimal("12345678901234567890.123456789");
        ((ObjectNode) discovery.path("tools").get(0).path("inputSchema").path("properties"))
            .putObject("price").put("type", "number").put("minimum", exact);
        when(gateway.discover(anyString(), any(), anyMap())).thenReturn(discovery);
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        assertThat(service.getSnapshot("dep-one", connection.id(), snapshot.id()).tools().getFirst().inputSchema()
            .path("properties").path("price").path("minimum").decimalValue()).isEqualByComparingTo(exact);
        service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(defaultSelection("search"))));
        assertThat(draftConfig.path("actions").get(1).path("inputSchema").path("properties").path("price").path("minimum").decimalValue())
            .isEqualByComparingTo(exact);
    }

    @Test void importReturnsActualDiffAndUndoOnlyRestoresAnUnchangedActionDraft() {
        var original = draftConfig.deepCopy();
        var connection = service.create("dep-one", publicRequest(null)); var snapshot = service.discover("dep-one", connection.id());
        var receipt = service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(defaultSelection("search"))));
        assertThat(receipt.changes()).hasSize(1); assertThat(receipt.changes().getFirst().before()).isNull();
        assertThat(receipt.changes().getFirst().after().path("requiresConfirmation").asBoolean()).isTrue();
        var undone = service.undoImport("dep-one", connection.id(), receipt.id());
        assertThat(draftConfig).isEqualTo(original); assertThat(undone.importReceiptId()).isEqualTo(receipt.id());
        assertStatus(HttpStatus.CONFLICT, () -> service.undoImport("dep-one", connection.id(), receipt.id()));
        var newerReceipt = service.importTools("dep-one", connection.id(), new ImportTools(snapshot.id(), 1, List.of(defaultSelection("search"))));
        draftConfig.put("newerManualEdit", true);
        assertStatus(HttpStatus.CONFLICT, () -> service.undoImport("dep-one", connection.id(), newerReceipt.id()));
        assertThat(draftConfig.path("newerManualEdit").asBoolean()).isTrue();
    }

    private Connection business() { return service.create("dep-one", new SaveConnection(null, "Company", "https://tools.example/mcp", "target-stage", "BUSINESS", "AUTO",
        new AuthSettings("BEARER_TOKEN_SECRET_REF", "CUSTOMER_OWN_KEY", null, null, null))); }
    private static SaveConnection publicRequest(Long revision) { return new SaveConnection(revision, "Public docs", "https://tools.example/mcp", "target-stage", "PUBLIC", "AUTO", null); }
    private static ToolSelection defaultSelection(String name) { return new ToolSelection(name, null, null, null, null, null); }
    private DeploymentDraftResponse draft() { return new DeploymentDraftResponse("draft-one", "dep-one", revision, "DRAFT", draftConfig, null, null, null, null, null, Instant.now(), Instant.now()); }
    private JsonNode discovery() throws Exception { return mapper.readTree("""
        {"ready":true,"protocolVersion":"2025-11-25","tools":[
         {"name":"search","title":"Search documentation","description":"Find useful information","schemaHash":"schema-search",
          "annotations":{"readOnlyHint":true},"inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"],
           "oneOf":[{"properties":{"language":{"enum":["en",null]}}},{"properties":{"level":{"type":"number","minimum":0.5}}}]},
          "outputSchema":{"type":"object","properties":{"answer":{"type":"string"}}}},
         {"name":"book","title":"Book appointment","description":"Request a booking","schemaHash":"schema-book",
          "inputSchema":{"type":"object","properties":{"time":{"type":"string"}}}}]}
        """); }
    private static void signIn(PlatformRole role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new PlatformPrincipal("customer@example.test", role, "Customer", "LOCAL", "user-one"), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))); }
    private static void assertBad(Runnable call) { assertStatus(HttpStatus.BAD_REQUEST, call); }
    private static void assertStatus(HttpStatus status, Runnable call) { assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
        failure -> assertThat(failure.getStatusCode()).isEqualTo(status)); }
}
