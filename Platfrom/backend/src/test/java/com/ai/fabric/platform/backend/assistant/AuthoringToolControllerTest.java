package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthoringToolControllerTest {
    final AuthoringToolController controller = new AuthoringToolController(mock(AuthoringToolAuthentication.class), mock(AuthoringDraftOperations.class));
    @Test void localTypedContractInstructionsReachTheRepairWithoutInventingAResult() {
        var response = controller.rejected(new AuthoringContractException("CANDIDATE_CHANGE_INVALID", "Named-item changes require an array field."));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("committed", false);
        assertThat(response.getBody().get("findings").toString()).contains("CANDIDATE_CHANGE_INVALID", "require an array field");
    }
    @Test void untypedProviderAndValidationBodiesNeverBecomeModelFeedback() {
        var response = controller.rejected(new ResponseStatusException(HttpStatus.BAD_REQUEST, "private-secret-canary provider payload"));
        assertThat(response.getBody().toString()).doesNotContain("private-secret-canary", "provider payload");
        assertThat(response.getBody().get("findings").toString()).contains("CANDIDATE_CONTRACT_INVALID");
    }
    @Test void authorizationDenialIsNotReclassifiedAsARepairableCandidate() {
        var denied = new ResponseStatusException(HttpStatus.FORBIDDEN, "Denied");
        assertThatThrownBy(() -> controller.rejected(denied)).isSameAs(denied);
    }
}
