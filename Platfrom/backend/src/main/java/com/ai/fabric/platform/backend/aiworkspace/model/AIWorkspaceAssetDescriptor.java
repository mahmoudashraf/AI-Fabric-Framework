package com.ai.fabric.platform.backend.aiworkspace.model;

public record AIWorkspaceAssetDescriptor(
    String role,
    String code,
    String version,
    String requestPath,
    String file,
    String sha256,
    String integrity,
    long size
) {
}
