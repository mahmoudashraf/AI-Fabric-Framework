package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationManifest;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssetCatalogService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceManifestRateLimiter;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceObservability;
import com.ai.fabric.platform.backend.aiworkspace.service.PublicAIWorkspaceManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicAIWorkspaceControllerTest {

    private static final String INSTALLATION_ID = "awi_pub_0123456789abcdef0123456789abcdef";
    private static final String ORIGIN = "https://dealer.example";

    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final PublicAIWorkspaceManifestService manifests = mock(PublicAIWorkspaceManifestService.class);
    private final AIWorkspaceManifestRateLimiter rateLimiter = mock(AIWorkspaceManifestRateLimiter.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(rateLimiter.allow(anyString())).thenReturn(true);
        var filter = new AIWorkspacePublicBoundaryFilter(
            manifests, rateLimiter, new AIWorkspaceObservability(new SimpleMeterRegistry()));
        mvc = MockMvcBuilders.standaloneSetup(new PublicAIWorkspaceController(assets, manifests))
            .setMessageConverters(
                new ByteArrayHttpMessageConverter(),
                new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
            .addFilters(filter)
            .build();
    }

    @Test
    void servesAcceptedOriginManifestWithStrongRevalidationContract() throws Exception {
        var result = manifestResult();
        when(manifests.isOriginAllowed(INSTALLATION_ID, ORIGIN)).thenReturn(true);
        when(manifests.resolve(INSTALLATION_ID, ORIGIN)).thenReturn(result);

        mvc.perform(get(manifestPath()).header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ORIGIN))
            .andExpect(header().string(HttpHeaders.ETAG, result.etag()))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, must-revalidate, public"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mvc.perform(get(manifestPath())
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.IF_NONE_MATCH, result.etag()))
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ETAG, result.etag()));
    }

    @Test
    void wrongOriginFailsClosedWithoutCorsProjection() throws Exception {
        String attacker = "https://attacker.example";
        when(manifests.isOriginAllowed(INSTALLATION_ID, attacker)).thenReturn(false);
        when(manifests.resolve(INSTALLATION_ID, attacker))
            .thenThrow(new ResponseStatusException(NOT_FOUND, "AI Workspace installation is unavailable."));

        mvc.perform(get(manifestPath()).header(HttpHeaders.ORIGIN, attacker))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rateLimitStopsManifestResolutionBeforeInstallationLookup() throws Exception {
        reset(rateLimiter);
        when(rateLimiter.allow(anyString())).thenReturn(false);

        mvc.perform(get(manifestPath())
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header("X-Forwarded-For", "198.51.100.25, 10.0.0.2"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verify(manifests, never()).isOriginAllowed(anyString(), anyString());
        verify(manifests, never()).resolve(anyString(), anyString());
    }

    @Test
    void servesInstallerAndImmutableAssetsWithCrossOriginIntegrityHeaders() throws Exception {
        byte[] bytes = "console.log('workspace');".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AIWorkspaceAssetDescriptor installer = asset(
            "/api/public/ai-workspace/install.js", "installer.js", "a".repeat(64), bytes.length);
        AIWorkspaceAssetDescriptor workspace = asset(
            "/api/public/ai-workspace/assets/workspace/1.0.0/widget.js",
            "widget.js", "b".repeat(64), bytes.length);
        when(assets.installer()).thenReturn(installer);
        when(assets.read(installer)).thenReturn(bytes);
        when(assets.findByRequestPath(workspace.requestPath())).thenReturn(Optional.of(workspace));
        when(assets.read(workspace)).thenReturn(bytes);

        mvc.perform(get("/api/public/ai-workspace/install.js").header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, must-revalidate, public"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string("Cross-Origin-Resource-Policy", "cross-origin"));

        mvc.perform(get(workspace.requestPath()).header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
            .andExpect(header().string(HttpHeaders.ETAG, '"' + workspace.sha256() + '"'))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string("Cross-Origin-Resource-Policy", "cross-origin"));

        mvc.perform(options(workspace.requestPath())
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, HEAD, OPTIONS"));
    }

    private PublicAIWorkspaceManifestService.ManifestResult manifestResult() {
        ObjectMapper mapper = new ObjectMapper();
        String revision = "sha256:" + "a".repeat(64);
        var manifest = new AIWorkspaceInstallationManifest(
            "loomai-ai-workspace-installation-v1", INSTALLATION_ID, revision,
            "sha256:" + "b".repeat(64), Instant.parse("2026-10-09T00:00:00Z"), 60,
            mapper.createObjectNode().put("mode", "public-runtime-anonymous"), mapper.createObjectNode());
        return new PublicAIWorkspaceManifestService.ManifestResult(manifest, ORIGIN, '"' + revision + '"');
    }

    private AIWorkspaceAssetDescriptor asset(String requestPath, String file, String sha256, long size) {
        return new AIWorkspaceAssetDescriptor(
            "workspace", "workspace", "1.0.0", requestPath, file, sha256,
            "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", size);
    }

    private String manifestPath() {
        return "/api/public/ai-workspace/installations/" + INSTALLATION_ID + "/manifest";
    }
}
