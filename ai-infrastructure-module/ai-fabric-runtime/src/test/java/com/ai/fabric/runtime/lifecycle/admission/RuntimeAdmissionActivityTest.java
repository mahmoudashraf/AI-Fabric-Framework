package com.ai.fabric.runtime.lifecycle.admission;

import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.Target;
import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static com.ai.fabric.runtime.lifecycle.admission.RuntimeAdmissionActivityService.Kind.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeAdmissionActivityTest {
    private static final Target TARGET = new Target("deployment", "staging", "release");
    private static final Instant NOW = Instant.parse("2026-10-15T13:00:00Z");
    private final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:activity_" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
    private final JdbcTemplate jdbc = new JdbcTemplate(source);
    private final RuntimeTargetRetirementIdentity identity = new RuntimeTargetRetirementIdentity(true,
        TARGET.deploymentId(), TARGET.targetProfileId(), TARGET.releaseId(), "tenant", true);

    private RuntimeAdmissionActivityService service() {
        new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/V15_2__runtime_admission_activity.sql")).execute(source);
        return new RuntimeAdmissionActivityService(jdbc, identity, new DataSourceTransactionManager(source), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void durableWatermarksContainOnlyAcceptedWorkAndAreReleaseScoped() {
        var service = service();
        service.accepted(ACCEPTED_SESSION); service.accepted(ACCEPTED_SESSION);
        service.accepted(ACCEPTED_ACTION); service.accepted(HUMAN_WORK);
        var readback = service.read(TARGET);
        assertThat(readback.signals()).hasSize(3);
        assertThat(readback.signals().stream().filter(s -> s.kind() == ACCEPTED_SESSION).findFirst().orElseThrow().sequence()).isEqualTo(2);
        var restarted = new RuntimeAdmissionActivityService(jdbc, identity, new DataSourceTransactionManager(source), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(restarted.read(TARGET)).isEqualTo(readback);
        var nextIdentity = new RuntimeTargetRetirementIdentity(true, "deployment", "staging", "next-release", "tenant", true);
        var next = new RuntimeAdmissionActivityService(jdbc, nextIdentity, new DataSourceTransactionManager(source), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(next.read(nextIdentity.target()).signals()).isEmpty();
        assertThatThrownBy(() -> next.read(TARGET)).hasMessageContaining("RUNTIME_TARGET_SCOPE_MISMATCH");
    }

    @Test
    void concurrentAcceptedWorkHasNoLostUpdates() throws Exception {
        var service = service();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 40; i++) tasks.add(workers.submit(() -> service.accepted(ACCEPTED_ACTION)));
            for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(service.read(TARGET).signals().getFirst().sequence()).isEqualTo(40);
    }

    @Test
    void readbackRequiresPrivateSystemAssertionExactTenantAndScope() {
        var service = service();
        var properties = new RuntimeAuthProperties();
        properties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        properties.getIngress().getPrivateAssertions().setSigningKey("activity-private-signing-secret");
        properties.getIngress().setAcceptedIssuers(List.of("platform"));
        properties.getIngress().setAcceptedAudiences(List.of("deployment"));
        var controller = new RuntimeAdmissionActivityController(new RuntimeRequestAuthResolver(properties), identity, service);
        assertThatThrownBy(() -> controller.readback(TARGET, new MockHttpServletRequest())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> controller.readback(TARGET, request(properties, RuntimeAuthSubjectType.END_USER, "tenant", RuntimeAdmissionActivityController.READ_SCOPE)))
            .hasMessageContaining("PRIVATE_ACTIVITY_SERVER_SCOPE_REQUIRED");
        assertThatThrownBy(() -> controller.readback(TARGET, request(properties, RuntimeAuthSubjectType.SYSTEM_PROCESS, "foreign", RuntimeAdmissionActivityController.READ_SCOPE)))
            .hasMessageContaining("PRIVATE_ACTIVITY_SERVER_SCOPE_REQUIRED");
        assertThatThrownBy(() -> controller.readback(TARGET, request(properties, RuntimeAuthSubjectType.SYSTEM_PROCESS, "tenant", "chat:query")))
            .hasMessageContaining("missing required scope");
        assertThat(controller.readback(TARGET, request(properties, RuntimeAuthSubjectType.SYSTEM_PROCESS, "tenant", RuntimeAdmissionActivityController.READ_SCOPE))
            .getBody().releaseId()).isEqualTo("release");
    }

    private MockHttpServletRequest request(RuntimeAuthProperties properties, RuntimeAuthSubjectType type, String tenant, String scope) {
        var context = RuntimeAuthContext.builder().subjectId("platform-worker").subjectType(type)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .deploymentId("deployment").tenantId(tenant).issuer("platform").audiences(List.of("deployment"))
            .grantedScopes(List.of(scope)).expiresAt(Instant.now().plusSeconds(60)).build();
        var request = new MockHttpServletRequest();
        request.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        request.addHeader("X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer " + new RuntimePrivateAssertionService(properties).issueAssertion(context));
        return request;
    }
}
