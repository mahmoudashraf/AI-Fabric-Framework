package com.ai.fabric.platform.backend.support;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Security registration must retain this controller's own dedicated service authentication. */
@RestController
@RequestMapping("/api/internal/support/diagnostics")
public class SupportDiagnosticController {
    private final SupportDiagnosticService diagnostics;
    public SupportDiagnosticController(SupportDiagnosticService diagnostics) { this.diagnostics = diagnostics; }

    @PostMapping("/{action}")
    public DiagnosticResult read(@PathVariable String action, @RequestHeader HttpHeaders headers) {
        return diagnostics.read(action, headers);
    }
}
