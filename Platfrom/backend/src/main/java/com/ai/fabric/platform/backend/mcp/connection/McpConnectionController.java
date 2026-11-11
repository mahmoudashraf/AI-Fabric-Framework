package com.ai.fabric.platform.backend.mcp.connection;

import static com.ai.fabric.platform.backend.mcp.connection.McpConnectionContracts.*;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/mcp-connections")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class McpConnectionController {
    private final McpConnectionService connections;
    private final McpBusinessOAuthService oauth;
    public McpConnectionController(McpConnectionService connections, McpBusinessOAuthService oauth) {
        this.connections = connections; this.oauth = oauth;
    }
    @GetMapping
    public ResponseEntity<Catalogue> list(@PathVariable String deploymentId, @RequestParam(required = false) String targetProfileId) {
        return response(connections.list(deploymentId, targetProfileId));
    }
    @PostMapping
    public ResponseEntity<Connection> create(@PathVariable String deploymentId, @RequestBody SaveConnection request) {
        return response(connections.create(deploymentId, request));
    }
    @PatchMapping("/{connectionId}")
    public ResponseEntity<Connection> update(@PathVariable String deploymentId, @PathVariable String connectionId, @RequestBody SaveConnection request) {
        return response(connections.update(deploymentId, connectionId, request));
    }
    @PostMapping("/{connectionId}/discover")
    public ResponseEntity<Snapshot> discover(@PathVariable String deploymentId, @PathVariable String connectionId) {
        return response(connections.discover(deploymentId, connectionId));
    }
    @GetMapping("/{connectionId}/snapshots/{snapshotId}")
    public ResponseEntity<Snapshot> snapshot(@PathVariable String deploymentId, @PathVariable String connectionId, @PathVariable String snapshotId) {
        return response(connections.getSnapshot(deploymentId, connectionId, snapshotId));
    }
    @PostMapping("/{connectionId}/import")
    public ResponseEntity<ImportReceipt> importTools(@PathVariable String deploymentId, @PathVariable String connectionId, @RequestBody ImportTools request) {
        return response(connections.importTools(deploymentId, connectionId, request));
    }
    @PostMapping("/{connectionId}/disconnect")
    public ResponseEntity<Connection> disconnect(@PathVariable String deploymentId, @PathVariable String connectionId) {
        return response(connections.disconnect(deploymentId, connectionId));
    }
    @PostMapping("/{connectionId}/imports/{receiptId}/undo")
    public ResponseEntity<UndoReceipt> undoImport(@PathVariable String deploymentId, @PathVariable String connectionId, @PathVariable String receiptId) {
        return response(connections.undoImport(deploymentId, connectionId, receiptId));
    }
    @PostMapping("/{connectionId}/target-bindings")
    public ResponseEntity<Connection> bindTarget(@PathVariable String deploymentId, @PathVariable String connectionId, @RequestBody TargetBinding request) {
        return response(connections.bindTarget(deploymentId, connectionId, request));
    }
    @PostMapping("/{connectionId}/authorize")
    public ResponseEntity<AuthorizationStart> authorize(@PathVariable String deploymentId, @PathVariable String connectionId, @RequestBody Authorize request) {
        String browserBinding = new com.nimbusds.oauth2.sdk.id.State().getValue();
        var result = oauth.authorize(deploymentId, connectionId, request, browserBinding);
        var cookie = ResponseCookie.from(McpOAuthCallbackController.COOKIE_NAME, browserBinding)
            .httpOnly(true).secure(true).sameSite("Lax").path("/api/mcp/oauth").maxAge(Duration.ofMinutes(10)).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result);
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
