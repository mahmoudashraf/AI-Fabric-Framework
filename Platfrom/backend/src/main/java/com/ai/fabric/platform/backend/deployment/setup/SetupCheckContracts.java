package com.ai.fabric.platform.backend.deployment.setup;

import java.time.Instant;
import java.util.List;

public final class SetupCheckContracts {
    private SetupCheckContracts() { }
    public enum Mode { ALL, CHANGED, FAILED }
    public record Request(Mode mode) { }
    public record ServiceResult(String id, String label, String status, String message,
                                Instant checkedAt, List<String> usedBy, Integer httpStatus) { }
    public record Summary(String deploymentId, String draftId, Integer revisionNumber, Instant checkedAt,
                          List<ServiceResult> services, long verifiedCount, long failedCount, long notTestedCount) { }
}
