package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeskSupportTicketConfigurationTest {
    final DeskBindingRepository bindings = mock(DeskBindingRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final DeskSupportTicketConfiguration resolver = new DeskSupportTicketConfiguration(bindings, targets, desk);
    final ObjectMapper mapper = new ObjectMapper();

    @Test void selectedTargetEnvironmentAndCredentialAreIndependentOfDeploymentCreationEnvironment() {
        var deployment = new DeploymentEntity(); deployment.setId("dep-a"); deployment.setCustomerId("customer-a");
        deployment.setTenantId("tenant-a"); deployment.setEnvironmentName("staging");
        var version = new DeploymentVersionEntity(); version.setDeploymentId("dep-a");
        var target = new DeploymentTargetProfileEntity(); target.setId("production-target"); target.setEnvironmentName("production"); target.setActive(true);
        when(targets.findById(target.getId())).thenReturn(Optional.of(target));
        when(desk.connection("production")).thenReturn(new DeskServiceClient.Connection("https://desk-production.example", "server-secret"));
        var binding = DeskBindingProvisioner.projection(deployment, target.getId());
        var values = resolver.connectorEnvironment(deployment, version, target.getId());
        assertThat(values).containsEntry(DeskSupportTicketConfiguration.COMMANDS_ENV,
            "https://desk-production.example/api/desk/integrations/v1/spaces/" + binding.getId() + "/server-ticket-commands");
        assertThat(values.get(DeskSupportTicketConfiguration.CONNECTOR_KEY_ENV))
            .isEqualTo("${secret:" + DeskBindingProvisioner.ticketConnectorKeyReference(binding) + "}");
        assertThat(values.toString()).doesNotContain("server-secret");
        verify(desk, never()).connection("staging");
        verify(bindings, never()).save(any());
    }

    @Test void missingTargetAndForeignOwnedBindingsCannotSelectCredentials() {
        var deployment = new DeploymentEntity(); deployment.setId("dep-a"); deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a");
        var version = new DeploymentVersionEntity(); version.setDeploymentId("dep-a");
        assertThatThrownBy(() -> resolver.connectorEnvironment(deployment, version, "missing")).hasMessageContaining("unavailable");
        var target = new DeploymentTargetProfileEntity(); target.setId("target-a"); target.setActive(true);
        when(targets.findById("target-a")).thenReturn(Optional.of(target));
        var binding = DeskBindingProvisioner.projection(deployment, "target-a"); binding.setCustomerId("foreign");
        when(bindings.findByDeploymentIdAndTargetProfileId("dep-a", "target-a")).thenReturn(Optional.of(binding));
        assertThatThrownBy(() -> resolver.connectorEnvironment(deployment, version, "target-a")).hasMessageContaining("does not belong");
        verifyNoInteractions(desk);
    }

    @Test void onlyTheExplicitManagedActionContractRequestsEnvironmentMaterialization() throws Exception {
        var action = mapper.readTree("""
            {"actions":[{"route":{"url":"${MANAGED_DESK_TICKET_COMMANDS_URL}",
              "headers":{"X-LoomAI-Ticket-Connector-Key":"${MANAGED_DESK_TICKET_CONNECTOR_KEY}"}}}]}
            """);
        assertThat(DeskSupportTicketConfiguration.requested(action, mapper.createObjectNode())).isTrue();
        assertThat(DeskSupportTicketConfiguration.requested(mapper.createObjectNode(), mapper.readTree("""
            {"actions":{"custom_request":{"url":"${MANAGED_DESK_TICKET_COMMANDS_URL}",
             "headers":{"X-LoomAI-Ticket-Connector-Key":"${MANAGED_DESK_TICKET_CONNECTOR_KEY}"}}}}
            """))).isTrue();
        assertThat(DeskSupportTicketConfiguration.requested(mapper.readTree("{\"actions\":[{\"name\":\"support_create_request\"}]}"), mapper.createObjectNode())).isFalse();
    }
}
