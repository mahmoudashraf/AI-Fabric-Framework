package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnownNoEffectVectorHandoffEvidenceTest {
    @Test void cannotReadOrAuthorizeOutsideTheOwningTransaction() {
        var jdbc = mock(JdbcTemplate.class);
        assertThatThrownBy(() -> new KnownNoEffectVectorHandoffEvidence(jdbc, new AdmissionJson(new ObjectMapper()))
            .require(null, "receipt", "fingerprint", "customer", "destination", "approval", "new_")).isInstanceOf(AdmissionFailure.class);
        verifyNoInteractions(jdbc);
    }

    @Test void activeExternalHandleAloneIsNeverASettlementProof() {
        var jdbc = mock(JdbcTemplate.class);
        var handle = new DeploymentProviderResourceHandleEntity(); handle.setDeploymentId("source"); handle.setTargetProfileId("target");
        handle.setStatus("ACTIVE"); handle.setResourceKind(PreservedExternalBindingKinds.VECTOR_STORAGE); handle.setMetadataJson("{}");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> new KnownNoEffectVectorHandoffEvidence(jdbc, new AdmissionJson(new ObjectMapper()))
                .require(handle, "receipt", "fingerprint", "customer", "destination", "approval", "new_")).isInstanceOf(AdmissionFailure.class);
            verify(jdbc).queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
            verifyNoMoreInteractions(jdbc);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
