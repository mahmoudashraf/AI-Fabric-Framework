package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.config.*;
import com.ai.fabric.platform.backend.partner.config.PartnerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import com.ai.fabric.platform.backend.web.PlatformApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Real loopback HTTP and servlet ERROR dispatch, with production authentication and synthetic collaborators. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AdmissionUnusedReservationCancellationHttpTest.HttpApp.class,
    properties = {"spring.config.name=unused-cancellation-http-test", "server.address=127.0.0.1", "spring.main.banner-mode=off"})
class AdmissionUnusedReservationCancellationHttpTest {
    private static final String PATH = "/api/platform/deployments/deployment/targets/target/admission-reservations/cancel-unused";
    private static final String VALID = """
        {"fence":{"reservationId":"reservation","generation":"generation","revision":0},
         "operationKey":"synthetic-body-marker","authorizationOperationKey":"apply",
         "authorizationRequestFingerprint":"authorization","manifestFingerprint":"manifest",
         "versionId":"version","versionConfigHash":"config","bindingFingerprint":"binding"}
        """;
    private static final String MISSING_REVISION = """
        {"fence":{"reservationId":"reservation","generation":"generation"},"operationKey":"synthetic-body-marker"}
        """;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired DeploymentAdmissionService admissions;
    DeploymentAdmissionService commandMock;
    @Autowired ObjectMapper mapper;
    @Autowired AtomicInteger errorDispatches;

    @BeforeEach void resetCommandBoundary() {
        commandMock = AopTestUtils.getUltimateTargetObject(admissions);
        reset(commandMock); errorDispatches.set(0);
    }

    @Test void adminMalformedRevisionReturnsSanitized400WithoutErrorRedispatch() throws Exception {
        assertInvalid(post(PATH, MISSING_REVISION, "synthetic-admin", null));
    }

    @ParameterizedTest @MethodSource("malformedBodies")
    void malformedCancellationBodiesNeverReachTheCommandOrExposeParserDetails(String input) throws Exception {
        for (String key : List.of("synthetic-admin", "synthetic-operator")) assertInvalid(post(PATH, input, key, null));
    }

    static Stream<String> malformedBodies() {
        return Stream.of("{}", "{\"fence\":null}", MISSING_REVISION,
            "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":null}}",
            "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":9223372036854775808}}",
            "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":0.5}}",
            "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":\"0\"}}",
            "{\"fence\":{\"reservationId\":7,\"generation\":\"generation\",\"revision\":0}}",
            "{\"fence\":[]}", "{\"operationKey\":\"synthetic-body-marker\",", "synthetic-body-marker", "");
    }

    @ParameterizedTest @ValueSource(strings = {"synthetic-admin", "synthetic-operator"})
    void validZeroRevisionStillReachesStaffCommandAndPreservesStaleFence409(String key) throws Exception {
        doThrow(new AdmissionFailure(CONFLICT, "STALE_RESERVATION_FENCE"))
            .when(commandMock).cancelUnusedReservation(eq("deployment"), eq("target"), any());
        var response = post(PATH, VALID, key, null);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(mapper.readTree(response.body()).path("message").textValue()).isEqualTo("STALE_RESERVATION_FENCE");
        verify(commandMock).cancelUnusedReservation(eq("deployment"), eq("target"),
            argThat(command -> command.fence().revision() == 0));
        assertThat(errorDispatches.get()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void customerRemainsForbiddenForValidAndMalformedCancellation(boolean malformed) throws Exception {
        var response = post(PATH, malformed ? MISSING_REVISION : VALID, null, "session=synthetic-customer-session");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(response.body()).path("errorCode").textValue()).isEqualTo("FORBIDDEN");
        verifyNoInteractions(commandMock);
        assertThat(errorDispatches.get()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anonymousAndInvalidKeysRemainUnauthorizedForValidAndMalformedCancellation(boolean malformed) throws Exception {
        for (String key : new String[]{null, "synthetic-invalid"}) {
            var response = post(PATH, malformed ? MISSING_REVISION : VALID, key, null);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(response.body()).path("errorCode").textValue()).isEqualTo("UNAUTHORIZED");
        }
        verifyNoInteractions(commandMock);
        assertThat(errorDispatches.get()).isZero();
    }

    @Test void controllerSpecificHandlingDoesNotChangeTheExistingUnrelatedErrorDispatchBoundary() throws Exception {
        var response = post("/synthetic-unrelated", "synthetic-body-marker", "synthetic-admin", null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(errorDispatches.get()).isEqualTo(1);
        verifyNoInteractions(commandMock);
    }

    private void assertInvalid(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(mapper.readTree(response.body())).isEqualTo(mapper.valueToTree(Map.of(
            "success", false, "errorCode", "UNUSED_CANCELLATION_REQUEST_INVALID",
            "message", "Invalid unused-reservation cancellation request.")));
        assertThat(response.body()).doesNotContain("synthetic-body-marker", "Json", "Exception", "revision", "stackTrace", "line", "column");
        verifyNoInteractions(commandMock);
        assertThat(errorDispatches.get()).isZero();
    }

    private HttpResponse<String> post(String path, String body, String key, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) request.header("X-Platform-API-Key", key);
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({AdmissionUnusedReservationCancellationController.class, PlatformSecurityConfiguration.class,
        AdmissionUnusedReservationCancellationController.InvalidCancellationRequestHandler.class,
        PlatformApiExceptionHandler.class, UnrelatedController.class})
    static class HttpApp {
        @Bean DeploymentAdmissionService admissions() { return mock(DeploymentAdmissionService.class); }
        @Bean InitialAdmissionRenewalAuthorizer initialRenewalAuthorizer() { return mock(InitialAdmissionRenewalAuthorizer.class); }
        @Bean AdmissionPolicyPlacementVerifier policyPlacementVerifier() { return mock(AdmissionPolicyPlacementVerifier.class); }
        @Bean jakarta.persistence.EntityManagerFactory entityManagerFactory() { return mock(jakarta.persistence.EntityManagerFactory.class); }
        @Bean PlatformAuthProperties auth() {
            return new PlatformAuthProperties(true, "X-Platform-API-Key", true, true, "session", Duration.ofHours(1),
                false, "Lax", "synthetic-operator", "synthetic-admin", false, null, null, null);
        }
        @Bean PlatformPublicApiProperties publicApi() { return new PlatformPublicApiProperties(false, "client", "key", Map.of()); }
        @Bean PlatformConsumerRuntimeAssignmentApiProperties consumerApi() {
            return new PlatformConsumerRuntimeAssignmentApiProperties(false, null, "key", null, null);
        }
        @Bean PartnerSupabaseAuthProperties partnerAuth() {
            return new PartnerSupabaseAuthProperties(false, null, null, null, null, false, List.of(), Duration.ofDays(1), null);
        }
        @Bean PlatformIdentityService identity() {
            var identity = mock(PlatformIdentityService.class);
            when(identity.authenticateSessionToken("synthetic-customer-session")).thenReturn(
                new PlatformPrincipal("synthetic-customer", PlatformRole.CUSTOMER_ADMIN, "Synthetic customer", "SESSION"));
            return identity;
        }
        @Bean PlatformSecretService secrets() { return mock(PlatformSecretService.class); }
        @Bean PlatformManagedProductServiceRepository productServices() { return mock(PlatformManagedProductServiceRepository.class); }
        @Bean PartnerMemberRepository partnerMembers() { return mock(PartnerMemberRepository.class); }
        @Bean AtomicInteger errorDispatches() { return new AtomicInteger(); }
        @Bean FilterRegistrationBean<Filter> recordErrorDispatches(AtomicInteger count) {
            var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                if (request.getDispatcherType() == DispatcherType.ERROR) count.incrementAndGet();
                chain.doFilter(request, response);
            });
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    @RestController static class UnrelatedController {
        @PostMapping("/synthetic-unrelated") Map<String, Object> post(@RequestBody Map<String, Object> input) { return input; }
    }
}
