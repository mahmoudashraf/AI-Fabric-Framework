package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(ProviderUsageGrantControllerTest.Config.class)
@WebAppConfiguration
class ProviderUsageGrantControllerTest {
    private static final String ROUTE = "/api/deployments/provider-usage/grants";
    @Autowired WebApplicationContext context;
    @Autowired ProviderUsageAllowanceService service;
    private ProviderUsageAllowanceService target;
    private MockMvc mvc;
    private final Limits caps = new Limits(50, 100_000, 2_000_000_000L);
    private final Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
    private final ProviderUsageGrant grant = new ProviderUsageGrant("grant", "campaign", "customer", "target", "staging",
        caps, expiry, new ProviderBinding("provider", List.of("model"), "secret-purpose", "private-secret-reference"),
        "reviewed-price", "owner-evidence", "payer");

    @BeforeEach void setup() throws Exception {
        target = AopTestUtils.getTargetObject(service);
        reset(target); mvc = webAppContextSetup(context).apply(springSecurity()).build();
        var quota = new GrantQuotaView("grant", "campaign", "customer", "target", "staging", caps,
            new Limits(0, 0, 0), caps, expiry, "reviewed-price", GrantStatus.ACTIVE, "a".repeat(64), caps);
        when(target.approveGrant(any())).thenReturn(quota); when(target.readQuota("grant")).thenReturn(quota);
    }

    @Test void onlyAdministratorCanApproveAndResponseIsQuotaNotPrivateBinding() throws Exception {
        String body = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(grant);
        mvc.perform(as(post(ROUTE).contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.grantId").value("grant"))
            .andExpect(jsonPath("$.binding").doesNotExist()).andExpect(jsonPath("$.managedSecretRef").doesNotExist());
        verify(target).approveGrant(grant); clearInvocations(target);
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT))
            mvc.perform(as(post(ROUTE).contentType("application/json").content(body), role)).andExpect(status().isForbidden());
        verifyNoInteractions(target);
    }

    @Test void quotaRouteIsStaffOnlyAndContainsNoCredentialOrBindingDetails() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR))
            mvc.perform(as(get(ROUTE + "/grant/quota"), role)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lifetimeCredits.totalTokens").value(100_000))
                .andExpect(jsonPath("$.binding").doesNotExist()).andExpect(jsonPath("$.credentialSha256").doesNotExist());
        clearInvocations(target);
        for (PlatformRole role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT))
            mvc.perform(as(get(ROUTE + "/grant/quota"), role)).andExpect(status().isForbidden());
        verifyNoInteractions(target);
    }

    @Test void unauthenticatedRequestsNeverReachFinancialService() throws Exception {
        mvc.perform(get(ROUTE + "/grant/quota")).andExpect(status().isForbidden());
        mvc.perform(post(ROUTE).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(ROUTE + "/grant/void-unused").contentType("application/json").content(voidBody())).andExpect(status().isForbidden());
        verifyNoInteractions(target);
    }

    @Test void onlyAdministratorCanVoidUnusedAndReceiptHasNoPrivateBinding() throws Exception {
        var command = new VoidUnusedCommand("correction-1", "a".repeat(64), 0L,
            new ZeroAllocatedCredits(0L, 0L, 0L), "Correct reviewed scope", "private-owner-clarification-ref");
        var receipt = new VoidUnusedReceipt("receipt", "grant", GrantStatus.VOID_UNUSED, "a".repeat(64), "b".repeat(64),
            command, "staff", "PLATFORM_ADMIN", expiry);
        when(target.voidUnused("grant", command)).thenReturn(receipt);
        mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(voidBody()), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID_UNUSED"))
            .andExpect(jsonPath("$.command.operationKey").value("correction-1"))
            .andExpect(jsonPath("$.binding").doesNotExist()).andExpect(jsonPath("$.credentialSha256").doesNotExist());
        verify(target).voidUnused("grant", command); clearInvocations(target);
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT))
            mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(voidBody()), role))
                .andExpect(status().isForbidden());
        verifyNoInteractions(target);
    }

    @Test void voidQuotaShowsOriginalLifetimeCreditsAndZeroAvailableAllocation() throws Exception {
        when(target.readQuota("grant")).thenReturn(new GrantQuotaView("grant", "campaign", "customer", "target", "staging", caps,
            new Limits(0, 0, 0), caps, expiry, "reviewed-price", GrantStatus.VOID_UNUSED, "a".repeat(64), new Limits(0, 0, 0)));
        mvc.perform(as(get(ROUTE + "/grant/quota"), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VOID_UNUSED"))
            .andExpect(jsonPath("$.lifetimeCredits.costNanoUsd").value(2_000_000_000L))
            .andExpect(jsonPath("$.lifetimeAllocated.costNanoUsd").value(0))
            .andExpect(jsonPath("$.remaining.costNanoUsd").value(2_000_000_000L))
            .andExpect(jsonPath("$.availableForAllocation.costNanoUsd").value(0));
    }

    @Test void voidRequiresExplicitIntegralZeroExpectationsBeforeCallingService() throws Exception {
        var mapper = new ObjectMapper();
        for (String field : List.of("operationKey", "expectedGrantFingerprint", "expectedAllocationCount", "expectedAllocated", "reason", "ownerApprovalEvidenceRef")) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody()); body.remove(field);
            mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(body.toString()), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isBadRequest());
        }
        for (Object value : java.util.Arrays.asList(null, -1, 1, 0.5, 0.0, "0", false)) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody());
            body.set("expectedAllocationCount", mapper.valueToTree(value));
            mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(body.toString()), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isBadRequest());
            for (String field : List.of("chatAttempts", "totalTokens", "costNanoUsd")) {
                body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody());
                ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("expectedAllocated")).set(field, mapper.valueToTree(value));
                mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(body.toString()), PlatformRole.PLATFORM_ADMIN))
                    .andExpect(status().isBadRequest());
            }
        }
        for (String field : List.of("chatAttempts", "totalTokens", "costNanoUsd")) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody());
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("expectedAllocated")).remove(field);
            mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(body.toString()), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(target);
    }

    @Test void voidHttpRejectsUnknownFieldsDuplicateKeysAndTextCoercions() throws Exception {
        var mapper = new ObjectMapper();
        var unknown = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody()); unknown.put("refund", true);
        var nestedUnknown = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody());
        ((com.fasterxml.jackson.databind.node.ObjectNode) nestedUnknown.get("expectedAllocated")).put("balance", 0);
        for (String body : List.of(unknown.toString(), nestedUnknown.toString(),
            voidBody().replace("\"expectedGrantFingerprint\":", "\"expectedGrantFingerprint\":\"" + "b".repeat(64) + "\",\"expectedGrantFingerprint\":"),
            voidBody().replace("\"expectedGrantFingerprint\":", "\"expectedGrantFingerprint\":\"" + "a".repeat(64) + "\",\"expectedGrantFingerprint\":"),
            voidBody().replace("\"expectedAllocationCount\":0", "\"expectedAllocationCount\":0,\"expectedAllocationCount\":0"),
            voidBody().replace("\"expectedAllocationCount\":0", "\"expectedAllocationCount\":1,\"expectedAllocationCount\":0"),
            voidBody().replace("\"chatAttempts\":0", "\"chatAttempts\":0,\"chatAttempts\":0"),
            voidBody().replace("\"totalTokens\":0", "\"totalTokens\":0,\"totalTokens\":0"),
            voidBody().replace("\"costNanoUsd\":0", "\"costNanoUsd\":1,\"costNanoUsd\":0"),
            voidBody().replace("\"expectedAllocated\":", "\"expectedAllocated\":{},\"expectedAllocated\":")))
            mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isBadRequest());
        for (String field : List.of("operationKey", "expectedGrantFingerprint", "reason", "ownerApprovalEvidenceRef")) {
            for (Object value : List.of(1, 0.0, true, List.of("coerced"))) {
                var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(voidBody()); body.set(field, mapper.valueToTree(value));
                mvc.perform(as(post(ROUTE + "/grant/void-unused").contentType("application/json").content(body.toString()), PlatformRole.PLATFORM_ADMIN))
                    .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(target);
    }

    private String voidBody() {
        return """
            {"operationKey":"correction-1","expectedGrantFingerprint":"%s","expectedAllocationCount":0,
             "expectedAllocated":{"chatAttempts":0,"totalTokens":0,"costNanoUsd":0},
             "reason":"Correct reviewed scope","ownerApprovalEvidenceRef":"private-owner-clarification-ref"}
            """.formatted("a".repeat(64));
    }

    @Test void noRawAllocationOrRuntimeInitializationRoutesAreExposed() throws Exception {
        for (String suffix : List.of("/allocate", "/initialize", "/active"))
            mvc.perform(as(post(ROUTE + suffix).contentType("application/json").content("{}"), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isNotFound());
        verifyNoInteractions(target);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, PlatformRole role) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("staff", role, "fixture", "SESSION"), null,
            List.of(new SimpleGrantedAuthority(role.authority())))));
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean ProviderUsageAllowanceService allowances() { return mock(ProviderUsageAllowanceService.class); }
        @Bean ProviderUsageGrantController controller(ProviderUsageAllowanceService allowances) { return new ProviderUsageGrantController(allowances); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
