package com.ai.fabric.platform.backend.deployment.service;

/** Published inside the transaction that persists the release's final verification projection. */
public record DeploymentReleaseVerificationCompleted(String releaseId, String verificationRunId) { }
