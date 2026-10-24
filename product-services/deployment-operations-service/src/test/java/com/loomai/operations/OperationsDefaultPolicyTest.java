package com.loomai.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.loomai.operations.config.TargetCapacityPolicy;
import com.loomai.operations.model.OperationsContracts.Decision;
import com.loomai.operations.service.DeploymentOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + OperationsTestConfiguration.API_KEY,
    "spring.datasource.url=jdbc:h2:mem:operations-default-policy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@Import(OperationsTestConfiguration.class)
class OperationsDefaultPolicyTest {

    @Autowired TargetCapacityPolicy policy;
    @Autowired DeploymentOperationsService service;

    @Test
    void noConfiguredTargetsMeansUnavailableNotAnArbitraryDenial() {
        assertThat(policy.revision()).isEqualTo("unconfigured");
        assertThat(policy.targets()).isEmpty();
        assertThat(service.assess(AbstractOperationsServiceTest.request("default-policy", "dep-default")).decision())
            .isEqualTo(Decision.UNAVAILABLE);
    }
}
