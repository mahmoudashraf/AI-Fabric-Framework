package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerPermissionMatrix;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManagedDependencyInboxRetirementTest {
    @Test
    void historicalInboxSpecStillDeserializesAndKeepsItsFingerprintWithoutGrantingExecution() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var validator = new ManagedDependencySpecValidator(mapper);
        var historical = ManagedDependencyFixtures.topology();
        String json = mapper.writeValueAsString(historical);
        var decoded = mapper.readValue(json, ManagedDependencySpec.class);

        assertThat(decoded).isEqualTo(historical);
        assertThat(validator.fingerprint(decoded)).isEqualTo(validator.fingerprint(historical));
        assertThatNoException().isThrownBy(() -> validator.validate(decoded));
        assertThatThrownBy(() -> ManagedDependencySpecValidator.requireProvisionable(decoded))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        assertThatNoException().isThrownBy(() -> ManagedDependencySpecValidator.requireProvisionable(
            ManagedDependencyFixtures.currentTopology()));
    }

    @Test
    void obsoleteOperatorKeyEndpointsHaveNoControllerMappings() throws Exception {
        var lifecycle = mock(ManagedDependencyLifecycleService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ManagedDependencyController(lifecycle)).build();
        String prefix = "/api/deployments/deployment/targets/target/managed-dependencies/operators";
        for (String suffix : new String[]{"", "/operator/rotate", "/operator/revoke", "/operator/credential-delivery"}) {
            mvc.perform(post(prefix + suffix).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        }
        verifyNoInteractions(lifecycle);
    }

    @Test
    void historicalPermissionEnumsDoNotAdvertiseActiveInboxOperations() {
        var retired = Set.of(Operation.INBOX_OPERATOR_ENROLL, Operation.INBOX_OPERATOR_ROTATE,
            Operation.INBOX_OPERATOR_DELIVER, Operation.INBOX_OPERATOR_REVOKE);
        assertThat(retired).allMatch(operation -> !CustomerPermissionMatrix.supported(operation));
        assertThat(CustomerPermissionMatrix.rows(retired)).isEmpty();
        assertThat(CustomerPermissionMatrix.rows(Set.of(Operation.READ, Operation.CONVERSATION_HANDOVER_RECOVER)))
            .extracting(row -> row.operation()).containsExactlyInAnyOrder(Operation.READ, Operation.CONVERSATION_HANDOVER_RECOVER);
    }
}
