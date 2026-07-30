package com.ai.fabric.vectorization.runner.service;

import java.util.List;

public class DataSyncTargetWriteException extends IllegalStateException {

    private final int httpStatus;
    private final int succeededOperations;
    private final int failedOperations;
    private final String providerRequestId;
    private final List<DataSyncTargetFailure> failures;

    public DataSyncTargetWriteException(int httpStatus,
                                        int succeededOperations,
                                        int failedOperations,
                                        String providerRequestId,
                                        List<DataSyncTargetFailure> failures) {
        super(message(httpStatus, providerRequestId, failures));
        this.httpStatus = httpStatus;
        this.succeededOperations = Math.max(0, succeededOperations);
        this.failedOperations = Math.max(0, failedOperations);
        this.providerRequestId = providerRequestId;
        this.failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int succeededOperations() {
        return succeededOperations;
    }

    public int failedOperations() {
        return failedOperations;
    }

    public String providerRequestId() {
        return providerRequestId;
    }

    public List<DataSyncTargetFailure> failures() {
        return failures;
    }

    public boolean hasDurableHandoff() {
        return failures.stream().anyMatch(DataSyncTargetFailure::durableHandoffAccepted);
    }

    private static String message(int httpStatus,
                                  String providerRequestId,
                                  List<DataSyncTargetFailure> failures) {
        StringBuilder out = new StringBuilder("Data Sync target failed (HTTP ")
            .append(httpStatus);
        if (providerRequestId != null && !providerRequestId.isBlank()) {
            out.append(", providerRequestId=").append(providerRequestId);
        }
        out.append(")");
        if (failures != null && !failures.isEmpty()) {
            int limit = Math.min(3, failures.size());
            for (int index = 0; index < limit; index++) {
                DataSyncTargetFailure failure = failures.get(index);
                out.append(index == 0 ? ": " : " | ")
                    .append(failure.errorCode());
                if (failure.vectorSpace() != null || failure.entityId() != null) {
                    out.append(" ")
                        .append(failure.vectorSpace() == null ? "?" : failure.vectorSpace())
                        .append("/")
                        .append(failure.entityId() == null ? "?" : failure.entityId());
                }
                out.append(" - ").append(failure.message())
                    .append(" [").append(failure.retryDisposition()).append("]");
                if (failure.indexingWorkId() != null) {
                    out.append(" workId=").append(failure.indexingWorkId());
                }
            }
            if (failures.size() > limit) {
                out.append(" | ").append(failures.size() - limit).append(" more failure(s)");
            }
        }
        return out.toString();
    }
}
