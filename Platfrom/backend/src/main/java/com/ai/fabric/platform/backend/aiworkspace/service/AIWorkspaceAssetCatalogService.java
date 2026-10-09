package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetCatalog;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AIWorkspaceAssetCatalogService {

    public static final String CATALOG_SCHEMA = "loomai-ai-workspace-assets-v1";

    private final PlatformAIWorkspaceProperties properties;
    private final ObjectMapper objectMapper;
    private volatile CatalogSnapshot snapshot;

    public AIWorkspaceAssetCatalogService(PlatformAIWorkspaceProperties properties,
                                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CatalogHealth health() {
        try {
            AIWorkspaceAssetCatalog catalog = catalog();
            return new CatalogHealth(true, "READY", catalog.workspace().version());
        } catch (RuntimeException ex) {
            return new CatalogHealth(false, ex.getMessage(), null);
        }
    }

    public AIWorkspaceAssetCatalog catalog() {
        Path catalogPath = properties.assetRoot().resolve("catalog.json").normalize();
        long lastModified = lastModified(catalogPath);
        CatalogSnapshot current = snapshot;
        if (current != null && current.lastModified() == lastModified) {
            return current.catalog();
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && current.lastModified() == lastModified) {
                return current.catalog();
            }
            AIWorkspaceAssetCatalog loaded = readAndVerify(catalogPath);
            snapshot = new CatalogSnapshot(lastModified, loaded);
            return loaded;
        }
    }

    public AIWorkspaceAssetDescriptor installer() {
        return catalog().installer();
    }

    public AIWorkspaceAssetDescriptor workspace() {
        return catalog().workspace();
    }

    public AIWorkspaceAssetDescriptor experiencePack(String code, String version) {
        return catalog().experiencePacks().stream()
            .filter(asset -> asset.code().equals(code) && asset.version().equals(version))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Experience-pack asset is not packaged: " + code + "@" + version
            ));
    }

    public Optional<AIWorkspaceAssetDescriptor> findByRequestPath(String requestPath) {
        AIWorkspaceAssetCatalog catalog = catalog();
        return allAssets(catalog).stream()
            .filter(asset -> asset.requestPath().equals(requestPath))
            .findFirst();
    }

    public byte[] read(AIWorkspaceAssetDescriptor descriptor) {
        Path file = safeAssetPath(descriptor.file());
        try {
            byte[] bytes = Files.readAllBytes(file);
            verifyBytes(descriptor, bytes);
            return bytes;
        } catch (IOException ex) {
            throw new IllegalStateException("AI Workspace asset cannot be read: " + descriptor.file(), ex);
        }
    }

    private AIWorkspaceAssetCatalog readAndVerify(Path catalogPath) {
        if (!Files.isRegularFile(catalogPath)) {
            throw new IllegalStateException("AI Workspace asset catalog is unavailable.");
        }
        try {
            AIWorkspaceAssetCatalog catalog = objectMapper.readValue(catalogPath.toFile(), AIWorkspaceAssetCatalog.class);
            if (!CATALOG_SCHEMA.equals(catalog.schemaVersion())) {
                throw new IllegalStateException("AI Workspace asset catalog schema is unsupported.");
            }
            if (catalog.installer() == null || catalog.workspace() == null || catalog.experiencePacks() == null) {
                throw new IllegalStateException("AI Workspace asset catalog is incomplete.");
            }
            for (AIWorkspaceAssetDescriptor asset : allAssets(catalog)) {
                validateDescriptor(asset);
                verifyBytes(asset, Files.readAllBytes(safeAssetPath(asset.file())));
            }
            return catalog;
        } catch (IOException ex) {
            throw new IllegalStateException("AI Workspace asset catalog cannot be read.", ex);
        }
    }

    private List<AIWorkspaceAssetDescriptor> allAssets(AIWorkspaceAssetCatalog catalog) {
        return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(catalog.installer(), catalog.workspace()),
            catalog.experiencePacks().stream()
        ).toList();
    }

    private void validateDescriptor(AIWorkspaceAssetDescriptor asset) {
        if (asset == null
            || isBlank(asset.role())
            || isBlank(asset.code())
            || isBlank(asset.version())
            || isBlank(asset.requestPath())
            || isBlank(asset.file())
            || !asset.requestPath().startsWith("/api/public/ai-workspace/")
            || !asset.file().matches("[A-Za-z0-9._/-]+\\.js")
            || isBlank(asset.sha256())
            || !asset.sha256().matches("[a-f0-9]{64}")
            || isBlank(asset.integrity())
            || !asset.integrity().matches("sha384-[A-Za-z0-9+/=]+")) {
            throw new IllegalStateException("AI Workspace asset catalog contains an invalid descriptor.");
        }
    }

    private Path safeAssetPath(String relativePath) {
        Path root = properties.assetRoot().toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalStateException("AI Workspace asset path escapes the configured root.");
        }
        return resolved;
    }

    private void verifyBytes(AIWorkspaceAssetDescriptor descriptor, byte[] bytes) {
        if (descriptor.size() != bytes.length) {
            throw new IllegalStateException("AI Workspace asset size does not match its catalog entry.");
        }
        String digest = digest("SHA-256", bytes);
        if (!Objects.equals(descriptor.sha256(), digest)) {
            throw new IllegalStateException("AI Workspace asset digest does not match its catalog entry.");
        }
    }

    private String digest(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Digest algorithm is unavailable: " + algorithm, ex);
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (IOException ex) {
            return -1L;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CatalogSnapshot(long lastModified, AIWorkspaceAssetCatalog catalog) {
    }

    public record CatalogHealth(boolean ready, String status, String workspaceVersion) {
    }
}
