package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import com.ai.fabric.platform.backend.deployment.service.UnusedVectorTransferJson;
import com.ai.fabric.platform.backend.deployment.service.UnusedVectorTransferService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/external-vector-transfers")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class UnusedVectorTransferController {
    private final UnusedVectorTransferService service;
    private final UnusedVectorTransferJson json;
    public UnusedVectorTransferController(UnusedVectorTransferService service, UnusedVectorTransferJson json) { this.service = service; this.json = json; }
    @PostMapping("/source-releases") public ReleaseReceipt release(@RequestBody String body) { return service.release(json.read(body, ReleaseRequest.class)); }
    @GetMapping("/source-releases/{id}") public ReleaseReadback readback(@PathVariable String id) { return service.releaseReadback(id); }
    @PostMapping("/source-reviews") public ReviewReceipt enroll(@RequestBody String body) { return service.registerReviewedSourceProof(json.read(body, ReviewRequest.class)); }
    @GetMapping("/source-reviews/{id}") public ReviewReceipt review(@PathVariable String id) { return service.review(id); }
    @PostMapping("/claims") public ClaimReceipt claim(@RequestBody String body) { return service.claim(json.read(body, ClaimRequest.class)); }
    @GetMapping("/claims/{id}") public ClaimReceipt claimReceipt(@PathVariable String id) { return service.claimReceipt(id); }

    @RestControllerAdvice(assignableTypes = UnusedVectorTransferController.class)
    public static class InvalidTransferHandler {
        @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
        public ResponseEntity<Map<String, Object>> invalid() { return error(HttpStatus.BAD_REQUEST, "UNUSED_VECTOR_TRANSFER_JSON_INVALID"); }
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Map<String, Object>> conflict() { return error(HttpStatus.CONFLICT, "UNUSED_VECTOR_TRANSFER_EVIDENCE_CHANGED"); }
        private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code) {
            var p = PlatformSecurityContext.currentPrincipal();
            if (p == null) { status = HttpStatus.UNAUTHORIZED; code = "UNAUTHORIZED"; }
            else if (p.role() != PlatformRole.PLATFORM_ADMIN || !List.of("SESSION", "API_KEY").contains(p.authenticationMode())) { status = HttpStatus.FORBIDDEN; code = "FORBIDDEN"; }
            return ResponseEntity.status(status).body(Map.of("success", false, "errorCode", code));
        }
    }
}
