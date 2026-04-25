package com.ai.fabric.platform.backend.partner.security;

import com.ai.fabric.platform.backend.partner.config.PartnerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.partner.entity.PartnerMemberEntity;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class PartnerSupabaseAuthenticationFilter extends OncePerRequestFilter {

    private final PartnerSupabaseAuthProperties properties;
    private final JwtDecoder jwtDecoder;
    private final PartnerMemberRepository memberRepository;

    public PartnerSupabaseAuthenticationFilter(PartnerSupabaseAuthProperties properties,
                                               JwtDecoder jwtDecoder,
                                               PartnerMemberRepository memberRepository) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
        this.memberRepository = memberRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/partners/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()).trim());
            PartnerPrincipal principal = principalFrom(jwt);
            SecurityContextHolder.getContext().setAuthentication(authenticationFor(principal));
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private PartnerPrincipal principalFrom(Jwt jwt) {
        validateIssuer(jwt);
        validateAudience(jwt);
        String supabaseUserId = requiredClaim(jwt, "sub");
        String email = normalizeEmail(jwt.getClaimAsString("email"));
        boolean emailVerified = emailVerified(jwt);
        if (properties.requireEmailVerified() && !emailVerified) {
            throw new IllegalArgumentException("Supabase email is not verified.");
        }
        String provider = provider(jwt);
        if (!properties.allowedProviderIds().contains(provider)) {
            throw new IllegalArgumentException("Supabase provider is not allowed.");
        }
        Optional<PartnerMemberEntity> member = memberRepository.findBySupabaseUserId(supabaseUserId)
            .or(() -> memberRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(email));
        PlatformRole role = member
            .map(value -> PlatformRole.valueOf(value.getRole()))
            .orElse(PlatformRole.PARTNER_AUTHENTICATED);
        return new PartnerPrincipal(
            supabaseUserId,
            email,
            emailVerified,
            firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("full_name"), email),
            jwt.getClaimAsString("avatar_url"),
            provider,
            providerSubject(jwt, supabaseUserId),
            member.map(PartnerMemberEntity::getPartnerAccountId).orElse(null),
            member.map(PartnerMemberEntity::getId).orElse(null),
            role,
            member.map(PartnerMemberEntity::getStatus).orElse("UNPROVISIONED")
        );
    }

    private void validateIssuer(Jwt jwt) {
        if (!StringUtils.hasText(properties.issuer())) {
            return;
        }
        var issuer = jwt.getIssuer();
        if (issuer == null || !properties.issuer().equals(issuer.toString())) {
            throw new IllegalArgumentException("Invalid Supabase issuer.");
        }
    }

    private void validateAudience(Jwt jwt) {
        if (!StringUtils.hasText(properties.audience())) {
            return;
        }
        if (!jwt.getAudience().contains(properties.audience())) {
            throw new IllegalArgumentException("Invalid Supabase audience.");
        }
    }

    private UsernamePasswordAuthenticationToken authenticationFor(PartnerPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
    }

    private String provider(Jwt jwt) {
        Object appMetadata = jwt.getClaims().get("app_metadata");
        if (appMetadata instanceof Map<?, ?> map) {
            Object provider = map.get("provider");
            if (provider != null && StringUtils.hasText(provider.toString())) {
                return provider.toString();
            }
        }
        String claim = jwt.getClaimAsString("provider");
        return StringUtils.hasText(claim) ? claim : "email";
    }

    private boolean emailVerified(Jwt jwt) {
        if (Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            return true;
        }
        Object userMetadata = jwt.getClaims().get("user_metadata");
        if (userMetadata instanceof Map<?, ?> map) {
            Object value = map.get("email_verified");
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        }
        return false;
    }

    private String providerSubject(Jwt jwt, String fallback) {
        String claim = jwt.getClaimAsString("provider_id");
        return StringUtils.hasText(claim) ? claim : fallback;
    }

    private String requiredClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing Supabase claim: " + claim);
        }
        return value;
    }

    private String normalizeEmail(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Supabase email is required.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
