package com.easyluxury.security;

import com.easyluxury.entity.User;
import com.easyluxury.repository.UserRepository;
import com.easyluxury.service.MockUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Profile("dev")
@ConditionalOnProperty(prefix = "app.mock-auth", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
public class MockAuthenticationFilter extends OncePerRequestFilter {

    private final MockUserService mockUserService;
    private final UserRepository userRepository;
    private final Environment environment;
    
    @Value("${app.mock-auth.enabled:false}")
    private boolean mockAuthEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Only process if mock auth is enabled AND we're in dev profile
        if (!mockAuthEnabled || !isDevProfile()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            // Check if it's a mock token
            if (token.startsWith("mock-token-")) {
                try {
                    // Verify mock token and get user
                    User user = getCurrentMockUser(token);
                    
                    if (user != null) {
                        // Set authentication with email as principal (so authentication.getName() returns email)
                        UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(
                                        user.getEmail(),
                                        null,
                                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        
                        log.debug("Mock authentication set for user: {}", user.getEmail());
                    }
                } catch (Exception e) {
                    log.warn("Mock authentication failed: {}", e.getMessage());
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private User getCurrentMockUser(String token) {
        try {
            var userDto = mockUserService.getCurrentMockUser(token);
            // Instead of creating a new User object, fetch from database
            // This ensures the User object exists in the database
            return userRepository.findByEmail(userDto.getEmail())
                    .orElseThrow(() -> new IllegalStateException("Mock user not found in database: " + userDto.getEmail()));
        } catch (Exception e) {
            log.warn("Failed to get mock user from database: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isDevProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("dev".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
