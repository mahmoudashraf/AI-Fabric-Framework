package com.ai.fabric.platform.backend.mcp.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/mcp/oauth")
public class McpOAuthCallbackController {
    public static final String COOKIE_NAME = "loomai_mcp_oauth";
    private final McpBusinessOAuthService oauth;
    private final McpConnectionProperties properties;
    private final ObjectMapper mapper;
    public McpOAuthCallbackController(McpBusinessOAuthService oauth, McpConnectionProperties properties, ObjectMapper mapper) {
        this.oauth = oauth; this.properties = properties; this.mapper = mapper;
    }
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String state, @RequestParam(required = false) String code,
        @RequestParam(required = false) String error, @RequestParam(name = "iss", required = false) String issuer,
        @CookieValue(name = COOKIE_NAME, required = false) String browserBinding) {
        var result = oauth.callback(state, code, error, issuer, browserBinding);
        var cookie = ResponseCookie.from(COOKIE_NAME, "").httpOnly(true).secure(true).sameSite("Lax")
            .path("/api/mcp/oauth").maxAge(Duration.ZERO).build();
        // Only the service-validated local path and a fixed outcome enter the redirect. Codes never do.
        URI console = McpSafeHttpTransport.validate(properties.getConsoleBaseUrl());
        if (console.getRawQuery() != null || !(console.getPath().isEmpty() || "/".equals(console.getPath())))
            throw new IllegalStateException("MCP Console origin must not include a query or path.");
        URI relative = URI.create(result.returnPath());
        if (relative.isAbsolute() || relative.getRawAuthority() != null || relative.getFragment() != null
            || !relative.getPath().startsWith("/smart-brains/")) throw new IllegalStateException("Invalid MCP OAuth return path.");
        URI location = console.resolve(UriComponentsBuilder.fromUri(relative).replaceQueryParam("mcpOAuth", result.outcome()).build().toUri());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookie.toString()).header("Referrer-Policy", "no-referrer").location(location).build();
    }
    @GetMapping("/client-metadata")
    public ResponseEntity<ObjectNode> clientMetadata() {
        if (properties.getCallbackUrl().isBlank() || properties.getClientMetadataUrl().isBlank())
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MCP OAuth client metadata is not configured.");
        URI callback = McpSafeHttpTransport.validate(properties.getCallbackUrl());
        var metadata = mapper.createObjectNode().put("client_id", properties.getClientMetadataUrl())
            .put("client_name", "LoomAI business MCP integration").put("token_endpoint_auth_method", "none");
        metadata.putArray("redirect_uris").add(callback.toString());
        metadata.putArray("grant_types").add("authorization_code").add("refresh_token");
        metadata.putArray("response_types").add("code");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(metadata);
    }
}
