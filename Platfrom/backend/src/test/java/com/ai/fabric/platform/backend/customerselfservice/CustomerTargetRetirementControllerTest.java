package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetRetirementService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerSelfServiceExceptionHandler;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerTargetRetirementController;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementController;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(CustomerTargetRetirementControllerTest.Config.class)
@WebAppConfiguration
class CustomerTargetRetirementControllerTest {
    private static final String ROUTE = "/api/customer-self-service/catalogue/offer/deployments/deployment/retirements/plan";
    private static final String BASE = "/api/customer-self-service/catalogue/offer/deployments/deployment/retirements";
    private static final String GENERIC = "/api/deployments/deployment/targets/staging-target/retirements";
    private static final String BODY = """
        {"targetProfileId":"staging-target","releaseIds":["release"],
         "drainDeadline":"2026-10-17T00:00:00Z",
         "retention":{"decision":"RECLAIM","backupDecisionReference":"rebuild-from-source"},
         "operationKey":"retire-key","reason":"Finished staging"}
        """;
    @Autowired WebApplicationContext context;
    @Autowired CustomerTargetRetirementService service;
    @Autowired TargetRetirementService genericService;
    private TargetRetirementService genericMock;
    private MockMvc mvc;

    @BeforeEach void setup() {
        genericMock = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(genericService);
        reset(service, genericMock);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void customerCanPlanWithoutInternalConsumerIdentifiers() throws Exception {
        mvc.perform(post(ROUTE).contentType("application/json").content(BODY).with(auth(PlatformRole.CUSTOMER_ADMIN)))
            .andExpect(status().isOk());
        verify(service).plan(eq("offer"), eq("deployment"), argThat(request ->
            "staging-target".equals(request.targetProfileId()) && List.of("release").equals(request.releaseIds())
                && "retire-key".equals(request.operationKey())));
    }

    @Test void staffPublicAndUnauthenticatedIdentitiesCannotEnterTheCustomerWrapper() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(post(ROUTE).contentType("application/json").content(BODY).with(auth(role)))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post(ROUTE).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void browserCannotSupplyConsumerIdsBindingRevisionsOrOwnership() throws Exception {
        for (String extra : List.of(
            "\"consumers\":[{\"consumerEntityId\":\"guessed\",\"expectedBindingRevision\":1}]",
            "\"consumerEntityId\":\"guessed\"", "\"customerId\":\"foreign\"", "\"expectedBindingRevision\":1")) {
            var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(BODY);
            var expanded = (com.fasterxml.jackson.databind.node.ObjectNode) body;
            expanded.setAll((com.fasterxml.jackson.databind.node.ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper().readTree("{" + extra + "}"));
            mvc.perform(post(ROUTE).contentType("application/json").content(expanded.toString()).with(auth(PlatformRole.CUSTOMER_ADMIN)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_CONTRACT"));
        }
        verifyNoInteractions(service);
    }

    @Test void customerListStatusAndCommandsUseOnlyTheSafeProjection() throws Exception {
        var summary = summary();
        when(service.list("offer", "deployment", "staging-target")).thenReturn(List.of(summary));
        when(service.status("offer", "deployment", "retirement")).thenReturn(summary);
        mvc.perform(get(BASE).param("targetProfileId", "staging-target").with(auth(PlatformRole.CUSTOMER_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("retirement"))
            .andExpect(jsonPath("$[0].snapshot").doesNotExist()).andExpect(jsonPath("$[0].consumers").doesNotExist());
        mvc.perform(get(BASE + "/retirement").with(auth(PlatformRole.CUSTOMER_ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requestFingerprint").value("planned-fingerprint"))
            .andExpect(jsonPath("$.snapshot").doesNotExist()).andExpect(jsonPath("$.resources").doesNotExist());
        when(service.execute(eq("offer"), eq("deployment"), eq("retirement"), any())).thenReturn(summary);
        when(service.retry(eq("offer"), eq("deployment"), eq("retirement"), any())).thenReturn(summary);
        when(service.cancel(eq("offer"), eq("deployment"), eq("retirement"), any())).thenReturn(summary);
        for (String action : List.of("execute", "retry", "cancel")) {
            mvc.perform(post(BASE + "/retirement/" + action).contentType("application/json")
                .content("{\"requestFingerprint\":\"planned-fingerprint\"}").with(auth(PlatformRole.CUSTOMER_ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("retirement"))
                .andExpect(jsonPath("$.snapshot").doesNotExist()).andExpect(jsonPath("$.operationKey").doesNotExist());
        }
        verify(service).execute(eq("offer"), eq("deployment"), eq("retirement"), argThat(command ->
            "planned-fingerprint".equals(command.requestFingerprint())));
        verify(service).retry(eq("offer"), eq("deployment"), eq("retirement"), any());
        verify(service).cancel(eq("offer"), eq("deployment"), eq("retirement"), any());
    }

    @Test void everyCustomerRetirementRouteRejectsStaffAndPublicRoles() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(get(BASE).param("targetProfileId", "staging-target").with(auth(role))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/retirement").with(auth(role))).andExpect(status().isForbidden());
            for (String action : List.of("execute", "retry", "cancel")) {
                mvc.perform(post(BASE + "/retirement/" + action).contentType("application/json")
                    .content("{\"requestFingerprint\":\"planned-fingerprint\"}").with(auth(role))).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test void commandBodiesRejectInternalConsumerAndTargetOverrides() throws Exception {
        for (String action : List.of("execute", "retry", "cancel")) {
            for (String field : List.of("consumers", "consumerEntityId", "expectedBindingRevision", "customerId", "targetProfileId", "operationKey")) {
                var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                body.put("requestFingerprint", "planned-fingerprint"); body.put(field, "caller-override");
                mvc.perform(post(BASE + "/retirement/" + action).contentType("application/json")
                    .content(body.toString()).with(auth(PlatformRole.CUSTOMER_ADMIN)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_CONTRACT"));
            }
        }
        verifyNoInteractions(service);
    }

    @Test void genericInventoryAndInternalExpectationRoutesAreStaffOnlyForEveryOperation() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(get(GENERIC).with(auth(role))).andExpect(status().isForbidden());
            mvc.perform(get(GENERIC + "/retirement").with(auth(role))).andExpect(status().isForbidden());
            mvc.perform(post(GENERIC + "/plan").contentType("application/json").content("{}").with(auth(role)))
                .andExpect(status().isForbidden());
            for (String action : List.of("execute", "retry", "cancel")) {
                mvc.perform(post(GENERIC + "/retirement/" + action).contentType("application/json")
                    .content("{\"requestFingerprint\":\"planned-fingerprint\"}").with(auth(role)))
                    .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(genericMock);
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR)) {
            mvc.perform(get(GENERIC).with(auth(role))).andExpect(status().isOk());
        }
        verify(genericMock, times(2)).list("deployment", "staging-target");
    }

    private Summary summary() {
        var now = java.time.Instant.parse("2026-10-16T00:00:00Z");
        return new Summary("retirement", "deployment", "staging-target", "planned-fingerprint", TargetRetirementContracts.State.PLANNED,
            TargetRetirementContracts.Step.DRAIN, now.plusSeconds(3600),
            new TargetRetirementContracts.Retention(TargetRetirementContracts.RetentionDecision.RECLAIM, null, null, "rebuild"),
            0, null, null, List.of(), false, true, now, null, null, now);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auth(PlatformRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal("owner@test.invalid", role,
            "Owner", "SESSION"), null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean CustomerTargetRetirementService service() { return mock(CustomerTargetRetirementService.class); }
        @Bean TargetRetirementService genericService() { return mock(TargetRetirementService.class); }
        @Bean CustomerOfferCodec codec() { return CustomerOfferTestFixtures.codec(); }
        @Bean CustomerTargetRetirementController controller(CustomerTargetRetirementService service, CustomerOfferCodec codec) {
            return new CustomerTargetRetirementController(service, codec);
        }
        @Bean TargetRetirementController genericController(TargetRetirementService genericService) {
            return new TargetRetirementController(genericService);
        }
        @Bean CustomerSelfServiceExceptionHandler errors() { return new CustomerSelfServiceExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
