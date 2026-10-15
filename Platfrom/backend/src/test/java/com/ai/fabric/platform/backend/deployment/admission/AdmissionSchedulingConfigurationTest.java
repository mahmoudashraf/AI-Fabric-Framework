package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "platform.deployment.admission.lifecycle.worker-enabled=true",
    "platform.deployment.admission.lifecycle.initial-delay=PT1H",
    "platform.deployment.admission.lifecycle.poll-delay=PT1H",
    "platform.deployment.admission.release.worker-enabled=true",
    "platform.deployment.admission.release.initial-delay=PT1H",
    "platform.deployment.admission.release.poll-delay=PT1H"
})
@ActiveProfiles("test")
class AdmissionSchedulingConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithIsoDurationSchedulingConfiguration() {
        assertThat(context.getBean(AdmissionLifecycleWorker.class)).isNotNull();
        assertThat(context.getBean(AdmissionReleaseCoordinator.class)).isNotNull();
        assertThat(context.getBean(AdmissionReleaseReconciliationWorker.class)).isNotNull();
    }
}
