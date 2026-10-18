package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionUnusedReservationCancellationJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"fence\":null}", "{\"fence\":0}", "{\"fence\":true}", "{\"fence\":\"fence\"}", "{\"fence\":[]}",
        "{\"fence\":{}}", "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\"}}"
    })
    void cancellationRequiresAnExplicitObjectFenceAndRevision(String input) {
        assertThatThrownBy(() -> mapper.readValue(input, CancelUnusedReservation.class)).isInstanceOf(JsonMappingException.class);
    }

    @ParameterizedTest @ValueSource(strings = {
        "null", "9223372036854775808", "-9223372036854775809", "0.5", "0.0", "1e0", "\"0\"", "true", "{}", "[]"
    })
    void cancellationRejectsNullOverflowNonintegralAndCoercedRevisionTypes(String revision) {
        String input = "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":" + revision + "}}";
        assertThatThrownBy(() -> mapper.readValue(input, CancelUnusedReservation.class)).isInstanceOf(JsonMappingException.class);
    }

    @ParameterizedTest @ValueSource(strings = {
        "{\"generation\":\"generation\"}", "{\"reservationId\":\"reservation\"}",
        "{\"reservationId\":null,\"generation\":\"generation\"}", "{\"reservationId\":\"reservation\",\"generation\":null}",
        "{\"reservationId\":1,\"generation\":\"generation\"}", "{\"reservationId\":\"reservation\",\"generation\":1}",
        "{\"reservationId\":true,\"generation\":\"generation\"}", "{\"reservationId\":\"reservation\",\"generation\":true}",
        "{\"reservationId\":{},\"generation\":\"generation\"}", "{\"reservationId\":\"reservation\",\"generation\":{}}",
        "{\"reservationId\":[],\"generation\":\"generation\"}", "{\"reservationId\":\"reservation\",\"generation\":[]}"
    })
    void cancellationRequiresBothIdentifiersAsExplicitStrings(String identifiers) throws Exception {
        var fence = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(identifiers);
        fence.put("revision", 0);
        var input = mapper.createObjectNode(); input.set("fence", fence);
        assertThatThrownBy(() -> mapper.treeToValue(input, CancelUnusedReservation.class)).isInstanceOf(JsonMappingException.class);
    }

    @ParameterizedTest @ValueSource(longs = {0, 1, Long.MAX_VALUE})
    void explicitIntegralRevisionIncludingZeroIsAcceptedWithoutChangingItsValue(long revision) throws Exception {
        String input = "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":" + revision + "}}";
        assertThat(mapper.readValue(input, CancelUnusedReservation.class).fence()).isEqualTo(new Fence("reservation", revision, "generation"));
    }

    @ParameterizedTest @ValueSource(strings = {
        "{\"reservationId\":\"reservation\",\"generation\":\"generation\"}",
        "{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":null}",
        "{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":\"0\"}"
    })
    void sharedFenceDeserializationRetainsItsExistingDefaultAndCoercionBehavior(String input) throws Exception {
        assertThat(mapper.readValue(input, Fence.class)).isEqualTo(new Fence("reservation", 0, "generation"));
    }

    @Test void completeCancellationCommandRoundTripsWithExplicitZeroRevision() throws Exception {
        var command = new CancelUnusedReservation(new Fence("reservation", 0, "generation"), "cancel", "apply",
            "authorization-fingerprint", "manifest-fingerprint", "version", "config-hash", "binding-fingerprint");
        assertThat(mapper.readValue(mapper.writeValueAsString(command), CancelUnusedReservation.class)).isEqualTo(command);
    }
}
