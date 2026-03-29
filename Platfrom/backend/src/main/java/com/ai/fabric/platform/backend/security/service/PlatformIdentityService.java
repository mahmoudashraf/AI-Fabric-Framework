package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserSessionEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class PlatformIdentityService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformAuthProperties properties;
    private final PlatformUserRepository userRepository;
    private final PlatformUserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;

    public PlatformIdentityService(PlatformAuthProperties properties,
                                   PlatformUserRepository userRepository,
                                   PlatformUserSessionRepository sessionRepository,
                                   PasswordEncoder passwordEncoder,
                                   PlatformAuditService auditService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void ensureBootstrapAdmin() {
        if (!properties.bootstrapAdminEnabled()
            || !StringUtils.hasText(properties.bootstrapAdminEmail())
            || !StringUtils.hasText(properties.bootstrapAdminPassword())) {
            return;
        }

        String normalizedEmail = normalizeEmail(properties.bootstrapAdminEmail());
        Optional<PlatformUserEntity> existing = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            return;
        }

        PlatformUserEntity user = new PlatformUserEntity();
        Instant now = Instant.now();
        user.setId("usr-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(normalizedEmail);
        user.setDisplayName(StringUtils.hasText(properties.bootstrapAdminDisplayName())
            ? properties.bootstrapAdminDisplayName().trim()
            : "Platform Admin");
        user.setPasswordHash(passwordEncoder.encode(properties.bootstrapAdminPassword()));
        user.setRole(PlatformRole.PLATFORM_ADMIN.name());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditService.record(
            "IDENTITY_BOOTSTRAP_ADMIN_CREATED",
            "PLATFORM_USER",
            user.getId(),
            Map.of(
                "email", user.getEmail(),
                "role", user.getRole()
            )
        );
    }

    @Transactional
    public PlatformAuthenticatedSession login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        PlatformUserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .filter(this::isActive)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid platform credentials."));

        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid platform credentials.");
        }

        Instant now = Instant.now();
        String rawToken = generateToken();
        PlatformUserSessionEntity session = new PlatformUserSessionEntity();
        session.setId("ses-" + UUID.randomUUID().toString().substring(0, 8));
        session.setUserId(user.getId());
        session.setTokenHash(hashToken(rawToken));
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(properties.sessionTtl()));
        sessionRepository.save(session);

        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditService.record(
            "IDENTITY_LOGIN_SUCCEEDED",
            "PLATFORM_USER_SESSION",
            session.getId(),
            Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole()
            )
        );

        return new PlatformAuthenticatedSession(
            principalForUser(user, "SESSION"),
            rawToken,
            session.getExpiresAt()
        );
    }

    @Transactional
    public PlatformPrincipal authenticateSessionToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }

        String tokenHash = hashToken(rawToken);
        PlatformUserSessionEntity session = sessionRepository.findByTokenHash(tokenHash)
            .orElse(null);
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }

        PlatformUserEntity user = userRepository.findById(session.getUserId())
            .filter(this::isActive)
            .orElse(null);
        if (user == null) {
            return null;
        }

        session.setLastSeenAt(Instant.now());
        sessionRepository.save(session);
        return principalForUser(user, "SESSION");
    }

    @Transactional
    public void logout(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        sessionRepository.findByTokenHash(hashToken(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                sessionRepository.save(session);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("sessionId", session.getId());
                details.put("userId", session.getUserId());
                auditService.record(
                    "IDENTITY_LOGOUT",
                    "PLATFORM_USER_SESSION",
                    session.getId(),
                    details
                );
            }
        });
    }

    private PlatformPrincipal principalForUser(PlatformUserEntity user, String authenticationMode) {
        return new PlatformPrincipal(
            user.getEmail(),
            PlatformRole.valueOf(user.getRole()),
            user.getDisplayName(),
            authenticationMode
        );
    }

    private boolean isActive(PlatformUserEntity user) {
        return "ACTIVE".equalsIgnoreCase(user.getStatus());
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash platform session token.", ex);
        }
    }
}
