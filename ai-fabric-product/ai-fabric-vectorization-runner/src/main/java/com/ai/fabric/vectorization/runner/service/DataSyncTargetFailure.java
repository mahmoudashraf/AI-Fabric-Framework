package com.ai.fabric.vectorization.runner.service;

public record DataSyncTargetFailure(
    int httpStatus,
    String errorCode,
    String message,
    String vectorSpace,
    String entityId,
    String indexingWorkId,
    String indexingStatus,
    DataSyncRetryDisposition retryDisposition,
    boolean durableHandoffAccepted,
    String providerRequestId
) {
}
