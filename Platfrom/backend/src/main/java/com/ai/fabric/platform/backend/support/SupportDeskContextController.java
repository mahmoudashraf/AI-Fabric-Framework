package com.ai.fabric.platform.backend.support;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;

@RestController
public class SupportDeskContextController {
    private final SupportDeskContextService context;
    public SupportDeskContextController(SupportDeskContextService context) { this.context = context; }

    @PostMapping("/api/integrations/support/desk/context")
    public ResponseEntity<Response> context(@Valid @RequestBody Request input, @RequestHeader HttpHeaders headers) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(context.handle(input, headers));
    }

}
