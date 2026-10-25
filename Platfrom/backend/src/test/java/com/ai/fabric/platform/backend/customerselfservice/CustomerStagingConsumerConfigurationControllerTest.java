package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerConfigurationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerStagingConsumerConfigurationService;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerStagingConsumerConfigurationController;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerSelfServiceExceptionHandler;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC decoding and actual method-security proxies; no database, provider or network. */
class CustomerStagingConsumerConfigurationControllerTest {
    private static final String PATH = "/api/customer-self-service/catalogue/offer/deployments/deployment/staging-consumer-configurations";
    private static final String VALID = """
        {"consumerId":"existing-app","expectedBindingRevision":2,"targetProfileId":"staging-target",
         "operationKey":"configure-existing","expectedDraftWorkspaces":[{"installationEntityId":"awi-test","expectedRowVersion":0}]}
        """;
    private AnnotationConfigApplicationContext context;
    private CustomerStagingConsumerConfigurationService service;
    private CustomerStagingConsumerConfigurationController controller;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
            context.getBean(CustomerStagingConsumerConfigurationService.class));
        controller = context.getBean(CustomerStagingConsumerConfigurationController.class);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CustomerSelfServiceExceptionHandler()).build();
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
    }

    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test void exactBodyReachesOnlyTheConfigurationServiceWithPublicConsumerAndExplicitZeroRowVersion() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(VALID)).andExpect(status().isOk());
        verify(service).configure(eq("offer"),eq("deployment"),argThat(r -> r.consumerId().equals("existing-app")
            && r.expectedBindingRevision()==2 && r.targetProfileId().equals("staging-target")
            && r.expectedDraftWorkspaces().getFirst().expectedRowVersion()==0));
    }

    @ParameterizedTest @MethodSource("badProfileBodies")
    void configurationOnlyAssignmentDoesNotAcceptProfileMutationFields(String body) throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<String> badProfileBodies() {
        return Stream.of(withProfile("1"),withProfile("true"),withProfile("{}"),withProfile("[]"),
            withProfile("\"\""),withProfile("\" untrimmed\""),withProfile("\"untrimmed \""),
            withProfile("\"bad\\nprofile\""),withProfile("\""+"x".repeat(101)+"\""),
            VALID.replace("\"expectedRowVersion\":0","\"expectedRowVersion\":0,\"profileId\":\"reviewed-workspace-v2\""),
            withProfile("\"reviewed-workspace-v2\"").replace("\"expectedRowVersion\":0","\"expectedRowVersion\":0,\"extra\":true"));
    }

    private static String withProfile(String jsonValue) {
        return VALID.replace("\"expectedRowVersion\":0","\"expectedRowVersion\":0,\"selectedWorkspaceProfileId\":"+jsonValue);
    }

    @ParameterizedTest @MethodSource("badBodies")
    void malformedMissingCoercedUnknownAndAmbiguousFencesNeverReachService(String body) throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<String> badBodies() {
        return Stream.of("{}", "null", "[]", VALID.replace("\"expectedBindingRevision\":2,", ""),
            VALID.replace("\"expectedBindingRevision\":2", "\"expectedBindingRevision\":null"),
            VALID.replace("\"expectedBindingRevision\":2", "\"expectedBindingRevision\":\"2\""),
            VALID.replace("\"expectedBindingRevision\":2", "\"expectedBindingRevision\":2.0"),
            VALID.replace("\"expectedBindingRevision\":2", "\"expectedBindingRevision\":-1"),
            VALID.replace("\"expectedBindingRevision\":2", "\"expectedBindingRevision\":9223372036854775807"),
            VALID.replace("\"targetProfileId\":\"staging-target\"", "\"targetProfileId\":true"),
            VALID.replace("\"targetProfileId\":\"staging-target\",", ""),
            VALID.replace("\"targetProfileId\":\"staging-target\"", "\"expectedEntitlementRevision\":4"),
            VALID.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":\"0\""),
            VALID.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":9223372036854775808"),
            VALID.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":null"),
            VALID.replace("\"expectedDraftWorkspaces\":[", "\"extra\":true,\"expectedDraftWorkspaces\":["),
            VALID.replace("\"installationEntityId\":\"awi-test\"", "\"installationEntityId\":9"),
            VALID.replace("\"expectedDraftWorkspaces\":[{\"installationEntityId\":\"awi-test\",\"expectedRowVersion\":0}]",
                "\"expectedDraftWorkspaces\":null"),
            VALID.replace("\"expectedRowVersion\":0}", "\"expectedRowVersion\":0},{\"installationEntityId\":\"awi-test\",\"expectedRowVersion\":0}"),
            VALID.replace("configure-existing", "bad key"), VALID.replace("existing-app", " existing-app"));
    }

    @ParameterizedTest @EnumSource(value=PlatformRole.class, names={"PLATFORM_ADMIN","PLATFORM_OPERATOR"})
    void staffCannotUseTheCustomerConfigurationRoute(PlatformRole role) throws Exception {
        CustomerOfferTestFixtures.authenticate("staff",role,"API_KEY");
        var request = new ObjectMapper().readValue(VALID, Request.class);
        assertThatThrownBy(() -> controller.configure("offer","deployment",request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test void anonymousCannotUseTheCustomerConfigurationRoute() throws Exception {
        SecurityContextHolder.clearContext();
        var request = new ObjectMapper().readValue(VALID,Request.class);
        assertThatThrownBy(() -> controller.configure("offer","deployment",request))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(service);
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean CustomerStagingConsumerConfigurationService service() { return mock(CustomerStagingConsumerConfigurationService.class); }
        @Bean CustomerStagingConsumerConfigurationController controller(CustomerStagingConsumerConfigurationService service) {
            return new CustomerStagingConsumerConfigurationController(service);
        }
    }
}
