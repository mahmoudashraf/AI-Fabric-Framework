package com.ai.fabric.platform.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformApiExceptionHandlerTest {
    @Test
    void concurrentModificationIsAConflictWithoutDatabaseDetails() {
        var result = new PlatformApiExceptionHandler().handleConcurrentModification(
            new OptimisticLockingFailureException("private database record and SQL"));
        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody()).containsEntry("success", false)
            .containsEntry("errorCode", "CONCURRENT_MODIFICATION");
        assertThat(result.getBody().toString()).doesNotContain("private database record", "SQL");
    }
}
