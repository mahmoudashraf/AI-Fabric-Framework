package com.ai.fabric.platform.backend.support;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/team")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class SupportTeamController {
    private final SupportTeamService team;

    public SupportTeamController(SupportTeamService team) { this.team = team; }

    @GetMapping
    public SupportTeamService.Team team(HttpServletResponse response) {
        privateResponse(response);
        return team.team();
    }

    @GetMapping("/{agentReference}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String agentReference, HttpServletResponse response) {
        privateResponse(response);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(team.photo(agentReference));
    }

    private static void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
