package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

/** Real authentication filters, dedicated callback authentication and persisted goal renewal. */
@SpringBootTest(properties = {
    "platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:authoring-authority-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "platform.auth.enabled=true", "platform.auth.api-key-enabled=true", "platform.auth.admin-api-key=console-api-test-key",
    "platform.auth.header-name=X-PLATFORM-API-KEY", "platform.auth.session-enabled=true",
    "platform.auth.session-cookie-name=platform_session", "platform.assistant.enabled=true",
    "platform.assistant.configured-brain-enabled=true",
    "platform.assistant.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthoringAuthoritySecurityIntegrationTest {
    static final String SERVICE_KEY = "dedicated-authoring-test-key";
    static final String RELEASE = "authoring-release";
    static final String WORK = "exec-authority-security";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthoringProperties properties;
    @Autowired ConfigurationAssistantService assistant;
    @Autowired AuthoringGoalRepository goalRecords;
    @Autowired AuthoringSessionRepository sessions;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformCustomerRepository customers;
    @SpyBean AuthoringGoalService goals;
    @MockBean AuthoringBrainBindingService bindings;
    @MockBean AuthoringResourceService resources;
    @MockBean ConfiguredAuthoringRuntimeClient runtime;
    @MockBean AuthoringGoalLoginAuthority logins;
    @MockBean PlatformSecretService secrets;
    @MockBean PlatformIdentityService identities;
    PlatformPrincipal originalActor;
    AuthoringBrainBindingService.Binding binding;
    String userId, customerId, resourceId, sessionId, turnId, goalId;

    @BeforeEach void prepareGoal() {
        doNothing().when(goals).refresh();
        String suffix = UUID.randomUUID().toString();
        userId = "user-" + suffix; customerId = "customer-" + suffix; resourceId = "dep-" + suffix;
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId);
        customer.setName("Authority fixture"); customer.setStatus("ACTIVE");
        customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now()); customers.saveAndFlush(customer);
        var user = new PlatformUserEntity(); user.setId(userId); user.setEmail(suffix + "@example.test");
        user.setDisplayName("Authority fixture"); user.setRole("PLATFORM_ADMIN"); user.setStatus("ACTIVE");
        user.setPasswordHash("not-a-login-credential"); user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now()); users.saveAndFlush(user);
        originalActor = new PlatformPrincipal(user.getEmail(), PlatformRole.PLATFORM_ADMIN, user.getDisplayName(), "SESSION", userId);
        signIn();
        when(secrets.resolveSecret(properties.getApiKeySecretRef())).thenReturn(SERVICE_KEY);
        when(identities.authenticateSessionToken("valid-console-cookie")).thenReturn(originalActor);
        binding = new AuthoringBrainBindingService.Binding(properties.getDeploymentId(), RELEASE, "https://runtime.example.test",
            "responses", "responses@1", "sha256:authority-test", 3, 2,
            List.of("configuration:author", "action:authoring_get_context", "action:authoring_update_draft"), true);
        when(bindings.resolve(anyString())).thenReturn(binding);
        when(bindings.conversationBinding()).thenReturn(binding);
        when(bindings.identity(anyString())).thenAnswer(call -> {
            if (!RELEASE.equals(call.getArgument(0))) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORING_MANAGED_BRAIN_UNAVAILABLE");
            return new RuntimeAuthorityBinding(properties.getCustomerId(), properties.getTenantId(), properties.getDeploymentId(),
                "target", "version", "hash", 1, "issuer", "audience", "public-issuer", "public-audience", false, false, List.of(), Map.of());
        });
        when(bindings.headers(eq(binding), anyString())).thenReturn(Map.of("X-Test-Fresh-Assertion", "renewed-authority"));
        when(logins.capture(any())).thenReturn(mapper.createObjectNode().put("loginSessionId", "original-login")
            .put("validUntil", Instant.now().plusSeconds(3600).toString()));
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString())).thenAnswer(call -> snapshot(customerId, List.of("/promptConfig")));
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode().put("valid", true));
        var response = mapper.createObjectNode().put("workId", WORK);
        response.putObject("goal").put("state", "RUNNING");
        when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(response);
        sessionId = assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", resourceId, "BEHAVIOUR")).path("id").asText();
        var goal = assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("Set the greeting.", false,
            null, null, null, null, null, "AUTONOMOUS"), "goal-start").path("goal");
        goalId = goal.path("id").asText(); turnId = goal.path("turnId").asText();
        clearInvocations(goals, logins, bindings, identities);
        SecurityContextHolder.clearContext();
    }

    @AfterEach void cleanup() {
        signIn();
        try {
            if (sessionId != null) sessions.findById(sessionId).ifPresent(sessions::delete);
            if (userId != null) users.deleteById(userId);
            if (customerId != null) customers.deleteById(customerId);
        } finally { SecurityContextHolder.clearContext(); }
    }

    void signIn() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(originalActor, null,
            List.of(new SimpleGrantedAuthority(originalActor.role().authority()))));
    }

    AuthoringResourceService.Snapshot snapshot(String owner, List<String> paths) {
        return new AuthoringResourceService.Snapshot(owner, "Owned draft", mapper.createObjectNode()
            .set("promptConfig", mapper.createObjectNode().put("systemPrompt", "Manual")), mapper.createObjectNode(), paths, "draft", true, "");
    }
    String path() { return "/api/internal/configuration-authoring/" + sessionId + "/" + turnId + "/authority"; }
    HttpHeaders proof() {
        var headers = new HttpHeaders(); headers.set(AuthoringToolAuthentication.KEY_HEADER, SERVICE_KEY);
        headers.set(AuthoringToolAuthentication.RELEASE_HEADER, RELEASE); return headers;
    }
    ObjectNode request() {
        return mapper.createObjectNode().put("goalId", goalId).put("workId", WORK).put("requestRevision", 1)
            .put("cancellationEpoch", 0).put("segment", 2);
    }
    MockHttpServletRequestBuilder renewal(HttpHeaders headers, JsonNode body) {
        return post(path()).headers(headers).contentType(MediaType.APPLICATION_JSON).content(body.toString());
    }

    @Test void dedicatedServiceProofAloneReachesRealGoalRenewalAndReturnsOnlyThatRootAuthority() throws Exception {
        mvc.perform(renewal(proof(), request()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.goalId").value(goalId)).andExpect(jsonPath("$.workId").value(WORK))
            .andExpect(jsonPath("$.operationNamespace").value(WORK)).andExpect(jsonPath("$.segment").value(2))
            .andExpect(jsonPath("$.runtimeHeaders.X-Test-Fresh-Assertion").value("renewed-authority"));
        verify(goals).renew(new AuthoringDraftOperations.Caller(sessionId, turnId, properties.getDeploymentId(), RELEASE),
            new AuthoringGoalService.Renewal(goalId, 1, 0, 2, WORK));
        verify(logins).requireCurrent(any(), eq(originalActor));
        assertThat(goalRecords.findById(goalId).orElseThrow().segment).isEqualTo(2);
        verify(identities).authenticateSessionToken(null);
    }

    @ParameterizedTest
    @CsvSource({"KEY,missing,403", "KEY,wrong,403", "KEY,blank,403", "KEY,duplicate,403", "KEY,comma,403",
        "RELEASE,missing,403", "RELEASE,blank,403", "RELEASE,duplicate,403", "RELEASE,wrong,503", "RELEASE,comma,503"})
    void missingWrongOrAmbiguousDedicatedProofCannotReachGoalRenewal(String field, String variant, int expectedStatus) throws Exception {
        var headers = proof(); String name = field.equals("KEY") ? AuthoringToolAuthentication.KEY_HEADER : AuthoringToolAuthentication.RELEASE_HEADER;
        switch (variant) {
            case "missing" -> headers.remove(name);
            case "wrong" -> headers.set(name, "foreign-value");
            case "blank" -> headers.set(name, " ");
            case "duplicate" -> headers.add(name, headers.getFirst(name));
            case "comma" -> headers.set(name, headers.getFirst(name) + "," + headers.getFirst(name));
        }
        var original = goalRecords.findById(goalId).orElseThrow().encryptedAuthority;
        mvc.perform(renewal(headers, request())).andExpect(status().is(expectedStatus));
        verify(goals, never()).renew(any(), any());
        assertThat(goalRecords.findById(goalId).orElseThrow().encryptedAuthority).isEqualTo(original);
        assertThat(goalRecords.findById(goalId).orElseThrow().segment).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void workingConsoleCookieOrAdminApiKeyAloneCannotRenew(boolean cookie) throws Exception {
        var console = get("/api/configuration-assistant/status");
        var renew = renewal(new HttpHeaders(), request());
        if (cookie) {
            console.cookie(new Cookie("platform_session", "valid-console-cookie"));
            renew.cookie(new Cookie("platform_session", "valid-console-cookie"));
        } else {
            console.header("X-PLATFORM-API-KEY", "console-api-test-key");
            renew.header("X-PLATFORM-API-KEY", "console-api-test-key");
        }
        mvc.perform(console).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));
        mvc.perform(renew).andExpect(status().isForbidden());
        verify(goals, never()).renew(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"turn,409", "goal,403", "work,403", "revision,409", "epoch,409", "login,401", "ownership,403", "paths,403", "actor,403"})
    void validHostProofDoesNotBypassStoredGoalAuthority(String boundary, int expectedStatus) throws Exception {
        var body = request(); String callbackPath = path();
        switch (boundary) {
            case "turn" -> callbackPath = callbackPath.replace(turnId, "foreign-turn");
            case "goal" -> body.put("goalId", "foreign-goal");
            case "work" -> body.put("workId", "foreign-work");
            case "revision" -> body.put("requestRevision", 2);
            case "epoch" -> body.put("cancellationEpoch", 1);
            case "login" -> doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHORING_REAUTHENTICATION_REQUIRED"))
                .when(logins).requireCurrent(any(), any());
            case "ownership" -> when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString()))
                .thenReturn(snapshot("foreign-customer", List.of("/promptConfig")));
            case "paths" -> when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString()))
                .thenReturn(snapshot(customerId, List.of("/promptConfig", "/actionsConfig")));
            case "actor" -> {
                var user = users.findById(userId).orElseThrow(); user.setStatus("DISABLED"); users.saveAndFlush(user);
            }
        }
        mvc.perform(post(callbackPath).headers(proof()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().is(expectedStatus));
        verify(goals).renew(any(), any());
        verify(bindings, never()).headers(any(), anyString());
        assertThat(goalRecords.findById(goalId).orElseThrow().segment).isEqualTo(1);
    }

    @Test void knownGoalFromAnotherSessionCannotBeSubstituted() throws Exception {
        signIn();
        String otherSession = assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", resourceId, "ACTIONS")).path("id").asText();
        assertThat(otherSession).isNotEqualTo(sessionId);
        SecurityContextHolder.clearContext();
        try {
            mvc.perform(post(path().replace(sessionId, otherSession)).headers(proof())
                    .contentType(MediaType.APPLICATION_JSON).content(request().toString()))
                .andExpect(status().isConflict());
            verify(goals).renew(any(), any());
            verify(bindings, never()).headers(any(), anyString());
        } finally { sessions.deleteById(otherSession); }
    }

    @ParameterizedTest @ValueSource(strings = {"unknown-tool", "authority/extra", "authority-extra"})
    void unknownInternalPathsDoNotInheritTheCallbackException(String suffix) throws Exception {
        String unknown = path().substring(0, path().length() - "authority".length()) + suffix;
        mvc.perform(post(unknown).headers(proof()).contentType(MediaType.APPLICATION_JSON).content(request().toString()))
            .andExpect(status().isUnauthorized());
        verify(goals, never()).renew(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void theAuthorityExceptionAppliesOnlyToPost(String method) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path()).headers(proof()))
            .andExpect(status().isUnauthorized());
        verify(goals, never()).renew(any(), any());
    }
}
