package com.ai.fabric.platform.backend.aiworkspace.model;

import java.util.List;

public record AIWorkspaceAssetCatalog(
    String schemaVersion,
    AIWorkspaceAssetDescriptor installer,
    AIWorkspaceAssetDescriptor workspace,
    List<AIWorkspaceAssetDescriptor> experiencePacks
) {
}
