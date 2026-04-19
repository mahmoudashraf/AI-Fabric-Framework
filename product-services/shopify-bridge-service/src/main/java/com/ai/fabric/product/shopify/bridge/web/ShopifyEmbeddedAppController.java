package com.ai.fabric.product.shopify.bridge.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class ShopifyEmbeddedAppController {

    private final Path merchantUiDirectory;

    public ShopifyEmbeddedAppController(@Value("${shopify.bridge.merchant-ui-directory:}") String merchantUiDirectory) {
        this.merchantUiDirectory = merchantUiDirectory == null || merchantUiDirectory.isBlank()
            ? null
            : Path.of(merchantUiDirectory).normalize();
    }

    @GetMapping(value = {"/", "/app"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        Resource resource = resolveFile("index.html");
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(resource);
    }

    @GetMapping("/assets/{*assetPath}")
    public ResponseEntity<Resource> asset(@PathVariable String assetPath) {
        String normalizedAssetPath = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        Resource resource = resolveFile("assets/" + normalizedAssetPath);
        MediaType contentType = MediaTypeFactory.getMediaType(resource)
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
            .contentType(contentType)
            .body(resource);
    }

    private Resource resolveFile(String relativePath) {
        if (merchantUiDirectory == null) {
            throw new ResponseStatusException(NOT_FOUND, "Shopify embedded app UI is not configured.");
        }
        Path candidate = merchantUiDirectory.resolve(relativePath).normalize();
        if (!candidate.startsWith(merchantUiDirectory) || !Files.exists(candidate) || !Files.isRegularFile(candidate)) {
            throw new ResponseStatusException(NOT_FOUND, "Shopify embedded app asset not found.");
        }
        return new FileSystemResource(candidate);
    }
}
