package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssetCatalogService;
import com.ai.fabric.platform.backend.aiworkspace.service.PublicAIWorkspaceManifestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/public/ai-workspace")
public class PublicAIWorkspaceController {

    private static final MediaType JAVASCRIPT = MediaType.parseMediaType("application/javascript");

    private final AIWorkspaceAssetCatalogService assets;
    private final PublicAIWorkspaceManifestService manifests;

    public PublicAIWorkspaceController(AIWorkspaceAssetCatalogService assets,
                                       PublicAIWorkspaceManifestService manifests) {
        this.assets = assets;
        this.manifests = manifests;
    }

    @GetMapping(value = "/install.js", produces = "application/javascript")
    public ResponseEntity<byte[]> installer() {
        AIWorkspaceAssetDescriptor asset = assets.installer();
        return javascript(asset, CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic().mustRevalidate());
    }

    @GetMapping("/installations/{installationId}/manifest")
    public ResponseEntity<?> manifest(@PathVariable String installationId,
                                      @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var result = manifests.resolve(installationId, origin);
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(result.etag());
        headers.setVary(java.util.List.of(HttpHeaders.ORIGIN));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofSeconds(result.manifest().cacheTtlSeconds())).cachePublic()
            .mustRevalidate().getHeaderValue());
        headers.set("X-Content-Type-Options", "nosniff");
        if (result.etag().equals(ifNoneMatch)) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }
        return new ResponseEntity<>(result.manifest(), headers, HttpStatus.OK);
    }

    @GetMapping(value = "/assets/{kind}/{version}/{assetName:.+}", produces = "application/javascript")
    public ResponseEntity<byte[]> asset(@PathVariable String kind,
                                        @PathVariable String version,
                                        @PathVariable String assetName,
                                        HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        AIWorkspaceAssetDescriptor descriptor = assets.findByRequestPath(requestPath)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        return javascript(descriptor, CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    private ResponseEntity<byte[]> javascript(AIWorkspaceAssetDescriptor asset, CacheControl cacheControl) {
        return ResponseEntity.ok()
            .contentType(JAVASCRIPT)
            .cacheControl(cacheControl)
            .eTag('"' + asset.sha256() + '"')
            .header("X-Content-Type-Options", "nosniff")
            .header("Cross-Origin-Resource-Policy", "cross-origin")
            .body(assets.read(asset));
    }
}
