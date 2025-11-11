package com.ai.infrastructure.deletion;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Summary of a user data deletion operation.
 */
@Value
@Builder
public class UserDataDeletionResult {

    public enum Status {
        COMPLETED,
        SKIPPED
    }

    String userId;
    Status status;
    int behaviorsDeleted;
    int indexedEntitiesDeleted;
    int vectorsDeleted;
    int domainRecordsDeleted;
    int auditEntriesDeleted;
    LocalDateTime timestamp;
    String message;
}
