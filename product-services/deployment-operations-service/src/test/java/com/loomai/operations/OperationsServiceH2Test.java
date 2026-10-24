package com.loomai.operations;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + OperationsTestConfiguration.API_KEY,
    "DEPLOYMENT_OPERATIONS_CAPACITY_POLICY_JSON=" + OperationsTestConfiguration.POLICY,
    "spring.datasource.url=jdbc:h2:mem:operations-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.hikari.data-source-properties.connectTimeout=3",
    "spring.datasource.hikari.data-source-properties.socketTimeout=10"
})
@Import(OperationsTestConfiguration.class)
class OperationsServiceH2Test extends AbstractOperationsServiceTest {}
