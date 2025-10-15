package com.easyluxury.security;

import com.easyluxury.entity.User;
import com.easyluxury.repository.UserRepository;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    
    @Value("${supabase.url}")
    private String supabaseUrl;
    
    @Value("${supabase.api-key}")
    private String supabaseApiKey;
    

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                
                if (verifyToken(signedJWT, token)) {
                    String supabaseId = signedJWT.getJWTClaimsSet().getSubject();
                    String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
                    
                    if (supabaseId == null || email == null) {
                        log.warn("JWT token missing required claims (sub or email)");
                        return;
                    }
                    
                    // Find or create user
                    Optional<User> userOpt = userRepository.findBySupabaseId(supabaseId);
                    User user;
                    
                    if (userOpt.isPresent()) {
                        user = userOpt.get();
                        log.debug("Found existing user: {}", email);
                    } else {
                        // Create new user from JWT claims
                        user = User.builder()
                                .supabaseId(supabaseId)
                                .email(email)
                                .role(User.UserRole.ADMIN) // Default role (only ADMIN available)
                                .build();
                        user = userRepository.save(user);
                        log.info("Created new user: {}", email);
                    }
                    
                    // Set authentication
                    UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authentication set for user: {}", email);
                } else {
                    log.warn("JWT token verification failed");
                }
            } catch (Exception e) {
                log.error("Error processing JWT token", e);
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean verifyToken(SignedJWT signedJWT, String token) throws Exception {
        try {
            // Check if token is not expired
            java.util.Date now = new java.util.Date();
            java.util.Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (exp != null && exp.before(now)) {
                log.warn("JWT token has expired");
                return false;
            }
            
            // Check if token is issued by our Supabase instance
            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            if (issuer == null || !issuer.equals(supabaseUrl + "/auth/v1")) {
                log.warn("JWT token issuer not recognized: {}", issuer);
                return false;
            }
            
            // Verify token with Supabase's auth endpoint - PROPER VALIDATION
            if (!verifyTokenWithSupabase(token)) {
                log.warn("Token verification failed with Supabase");
                return false;
            }
            
            // Additional security checks
            String sub = signedJWT.getJWTClaimsSet().getSubject();
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
            
            if (sub == null || email == null) {
                log.warn("JWT token missing required claims (sub or email)");
                return false;
            }
            
            log.debug("JWT token verified successfully with Supabase validation");
            return true;
            
        } catch (Exception e) {
            log.error("Error verifying JWT token", e);
            return false;
        }
    }

    private boolean verifyTokenWithSupabase(String token) {
        try {
            String verifyUrl = supabaseUrl + "/auth/v1/user";
            log.debug("Verifying token with Supabase: {}", verifyUrl);
            
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
                
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(verifyUrl))
                .header("apikey", supabaseApiKey)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                log.debug("Token verified successfully with Supabase");
                return true;
            } else {
                log.warn("Token verification failed with Supabase: HTTP {} - {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Error verifying token with Supabase", e);
            return false;
        }
    }
}
