package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

public class DeskReviewBridgeAuthenticationFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/desk/bridge/v1/bindings/";
    private final DeskActivationService activations;
    private final CustomerSupabaseIdentityService identities;
    private final DeskServiceClient desk;
    private final DeploymentTargetProfileRepository targets;
    private final ObjectMapper mapper;

    public DeskReviewBridgeAuthenticationFilter(DeskActivationService activations, CustomerSupabaseIdentityService identities,
        DeskServiceClient desk, DeploymentTargetProfileRepository targets, ObjectMapper mapper) {
        this.activations = activations; this.identities = identities; this.desk = desk; this.targets = targets; this.mapper = mapper;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith(PREFIX); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            String suffix = request.getRequestURI().substring(PREFIX.length());
            int separator = suffix.indexOf('/');
            if (separator < 1) throw new ResponseStatusException(UNAUTHORIZED);
            String bindingId = suffix.substring(0, separator);
            if (!bindingId.matches("[A-Za-z0-9-]{1,64}")) throw new ResponseStatusException(UNAUTHORIZED);
            var binding = activations.requireBridgeBinding(bindingId, request.getHeader("X-LoomAI-Desk-Service-Key"));
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new ResponseStatusException(UNAUTHORIZED);
            var jwt = identities.verifyIdentity(authorization.substring(7));
            var target = targets.findById(binding.getTargetProfileId()).filter(value -> value.isActive())
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN));
            var allowed = desk.get(desk.connection(target.getEnvironmentName()),
                "/api/desk/admin/v1/bindings/" + bindingId + "/authorizations?issuer="
                    + encode(jwt.getIssuer().toString()) + "&subject=" + encode(jwt.getSubject()) + "&permission=REVIEWER");
            if (!allowed.path("authorized").asBoolean(false)) throw new ResponseStatusException(FORBIDDEN);
            var principal = new DeskReviewPrincipal(bindingId, jwt.getIssuer().toString(), jwt.getSubject());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("DESK_REVIEW_INTEGRATION"))));
        } catch (ResponseStatusException error) {
            SecurityContextHolder.clearContext(); response.setStatus(error.getStatusCode().value());
            response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(), Map.of("errorCode", "DESK_REVIEW_ACCESS_DENIED",
                "message", "An active, authenticated Desk reviewer assignment is required."));
            return;
        }
        chain.doFilter(request, response);
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
