package com.ai.fabric.vectorization.runner.service;

public enum DataSyncRetryDisposition {
    PERMANENT_INPUT,
    IDENTITY_OR_POLICY_CHANGE_REQUIRED,
    CONTRACT_DRIFT,
    SAFE_RESUBMIT,
    RECONCILE_DURABLE_WORK,
    OPERATOR_REVIEW,
    UNKNOWN
}
