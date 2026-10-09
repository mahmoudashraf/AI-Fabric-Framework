package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetCatalog;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIWorkspaceAssetCatalogServiceTest {

    @TempDir
    Path root;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesEveryPackagedByteAndResolvesOnlyCataloguedPaths() throws Exception {
        AIWorkspaceAssetDescriptor installer = asset("installer", "installer", "1.0.0", "/api/public/ai-workspace/install.js", "install.js", "install");
        AIWorkspaceAssetDescriptor workspace = asset("workspace", "max-mode-widget", "1.0.0", "/api/public/ai-workspace/assets/workspace/1.0.0/widget.abc.js", "widget.abc.js", "widget");
        AIWorkspaceAssetDescriptor pack = asset("experience-pack", "dealership", "1.0.0", "/api/public/ai-workspace/assets/dealership/1.0.0/pack.abc.js", "pack.abc.js", "pack");
        objectMapper.writeValue(root.resolve("catalog.json").toFile(), new AIWorkspaceAssetCatalog(
            AIWorkspaceAssetCatalogService.CATALOG_SCHEMA, installer, workspace, List.of(pack)
        ));

        AIWorkspaceAssetCatalogService service = service();
        assertThat(service.health().ready()).isTrue();
        assertThat(service.findByRequestPath(pack.requestPath())).contains(pack);
        assertThat(service.read(pack)).isEqualTo("pack".getBytes());
        assertThat(service.findByRequestPath("/api/public/ai-workspace/assets/unknown.js")).isEmpty();
    }

    @Test
    void failsClosedWhenPackagedBytesNoLongerMatchCatalog() throws Exception {
        AIWorkspaceAssetDescriptor installer = asset("installer", "installer", "1.0.0", "/api/public/ai-workspace/install.js", "install.js", "install");
        AIWorkspaceAssetDescriptor workspace = asset("workspace", "max-mode-widget", "1.0.0", "/api/public/ai-workspace/assets/workspace/1.0.0/widget.abc.js", "widget.abc.js", "widget");
        AIWorkspaceAssetDescriptor pack = asset("experience-pack", "dealership", "1.0.0", "/api/public/ai-workspace/assets/dealership/1.0.0/pack.abc.js", "pack.abc.js", "pack");
        objectMapper.writeValue(root.resolve("catalog.json").toFile(), new AIWorkspaceAssetCatalog(
            AIWorkspaceAssetCatalogService.CATALOG_SCHEMA, installer, workspace, List.of(pack)
        ));
        Files.writeString(root.resolve("pack.abc.js"), "tampered");

        assertThatThrownBy(() -> service().catalog())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("size does not match");
    }

    private AIWorkspaceAssetCatalogService service() {
        return new AIWorkspaceAssetCatalogService(
            new PlatformAIWorkspaceProperties(root, false, 120, Duration.ofSeconds(60), null, null),
            objectMapper
        );
    }

    private AIWorkspaceAssetDescriptor asset(String role,
                                              String code,
                                              String version,
                                              String requestPath,
                                              String file,
                                              String content) throws Exception {
        byte[] bytes = content.getBytes();
        Files.write(root.resolve(file), bytes);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String integrity = "sha384-" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-384").digest(bytes));
        return new AIWorkspaceAssetDescriptor(role, code, version, requestPath, file, sha256, integrity, bytes.length);
    }
}
