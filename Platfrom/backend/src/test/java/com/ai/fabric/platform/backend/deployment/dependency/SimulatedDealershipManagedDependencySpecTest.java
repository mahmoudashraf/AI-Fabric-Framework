package com.ai.fabric.platform.backend.deployment.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedDealershipManagedDependencySpecTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);

    @Test
    void reviewedTemplateCoversTheCompleteManagedPilotTopology() throws Exception {
        ManagedDependencySpec spec;
        try (var input = getClass().getResourceAsStream(
            "/managed-dependency/simulated-dealership-experience-v1.template.json")) {
            spec = mapper.readValue(input, ManagedDependencySpec.class);
        }

        validator.validate(spec);

        assertThat(spec.services()).extracting(ManagedDependencySpec.Service::slot)
            .containsExactly("simulator", "inbox", "receiver");
        assertThat(spec.totalCapacity())
            .isEqualTo(new ManagedDependencySpec.Capacity(1500, 2816, 14336, 4));

        Map<String, ManagedDependencySpec.Service> services = spec.services().stream()
            .collect(Collectors.toMap(ManagedDependencySpec.Service::slot, service -> service));
        assertThat(services.get("simulator").environment().keySet()).contains(
            "SIMULATOR_AUTOTRADER_ADVERTISER_ID",
            "SIMULATOR_AUTOTRADER_API_KEY",
            "SIMULATOR_AUTOTRADER_API_SECRET",
            "SIMULATOR_AUTOTRADER_NOTIFICATION_SECRET",
            "SIMULATOR_ALLOWED_WEBHOOK_HOSTS"
        );
        assertThat(services.get("receiver").environment()).containsEntry(
            "PROVIDER_SIMULATOR_ENABLED",
            new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.LITERAL, "false")
        ).containsEntry(
            "HUMAN_INBOX_ENABLED",
            new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.LITERAL, "false")
        );
        assertThat(services.get("receiver").dependsOn()).containsExactly("simulator", "inbox");

        Map<String, ManagedDependencySpec.Value> runtime = spec.runtimeEnvironment().stream()
            .collect(Collectors.toMap(ManagedDependencySpec.RuntimeEnvironmentBinding::name,
                ManagedDependencySpec.RuntimeEnvironmentBinding::value));
        assertThat(runtime.keySet()).containsAll(Set.of(
            "MANAGED_SIMULATOR_BASE_URL",
            "MANAGED_SIMULATOR_PROVIDER_KEY",
            "MANAGED_SIMULATOR_PROVIDER_SECRET",
            "MANAGED_SIMULATOR_NOTIFICATION_SECRET",
            "MANAGED_SIMULATOR_ACCOUNT_ID",
            "MANAGED_RECEIVER_ACTION_URL",
            "MANAGED_RECEIVER_API_KEY",
            "MANAGED_INBOX_BASE_URL",
            "MANAGED_INBOX_BINDING_ID",
            "MANAGED_INBOX_API_KEY",
            "MANAGED_INBOX_CALLBACK_SIGNING_SECRET",
            "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET"
        ));
        assertThat(runtime.get("MANAGED_INBOX_BINDING_ID").kind())
            .isEqualTo(ManagedDependencySpec.ValueKind.BINDING_ID);
        assertThat(runtime.get("MANAGED_INBOX_BINDING_ID").serviceSlot()).isEqualTo("inbox");
    }
}
