package com.ai.fabric.vectorization.runner.service;

public class DataSyncWorkReconciliationException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final String workId;
    private final String indexingStatus;
    private final String entityType;
    private final String entityId;

    public DataSyncWorkReconciliationException(
        int httpStatus,
        String errorCode,
        String message,
        String workId,
        String indexingStatus,
        String entityType,
        String entityId
    ) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.workId = workId;
        this.indexingStatus = indexingStatus;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public String workId() {
        return workId;
    }

    public String indexingStatus() {
        return indexingStatus;
    }

    public String entityType() {
        return entityType;
    }

    public String entityId() {
        return entityId;
    }
}
