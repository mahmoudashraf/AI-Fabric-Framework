package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.model.CreatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.model.PlatformUserSummary;
import com.ai.fabric.platform.backend.security.model.UpdatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformUserAdminService {

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService platformAuditService;

    public PlatformUserAdminService(PlatformUserRepository platformUserRepository,
                                    PasswordEncoder passwordEncoder,
                                    PlatformAuditService platformAuditService) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.platformAuditService = platformAuditService;
    }

    public List<PlatformUserSummary> listUsers() {
        return platformUserRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public PlatformUserSummary createUser(CreatePlatformUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (platformUserRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "A platform user already exists for that email.");
        }

        PlatformUserEntity user = new PlatformUserEntity();
        Instant now = Instant.now();
        user.setId("usr-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(normalizedEmail);
        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setPasswordHash(passwordEncoder.encode(normalizePassword(request.password())));
        user.setRole(normalizeRole(request.role()).name());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        platformUserRepository.save(user);

        platformAuditService.record(
            "PLATFORM_USER_CREATED",
            "PLATFORM_USER",
            user.getId(),
            Map.of(
                "email", user.getEmail(),
                "role", user.getRole(),
                "status", user.getStatus()
            )
        );
        return toSummary(user);
    }

    @Transactional
    public PlatformUserSummary updateUser(String userId, UpdatePlatformUserRequest request) {
        PlatformUserEntity user = platformUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Platform user not found: " + userId));

        PlatformRole nextRole = normalizeRole(request.role());
        String nextStatus = normalizeStatus(request.status());
        enforceAdminGuardrails(user, nextRole, nextStatus);

        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setRole(nextRole.name());
        user.setStatus(nextStatus);
        user.setUpdatedAt(Instant.now());
        platformUserRepository.save(user);

        platformAuditService.record(
            "PLATFORM_USER_UPDATED",
            "PLATFORM_USER",
            user.getId(),
            Map.of(
                "email", user.getEmail(),
                "role", user.getRole(),
                "status", user.getStatus()
            )
        );
        return toSummary(user);
    }

    @Transactional
    public PlatformUserSummary resetPassword(String userId, String password) {
        PlatformUserEntity user = platformUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Platform user not found: " + userId));

        user.setPasswordHash(passwordEncoder.encode(normalizePassword(password)));
        user.setUpdatedAt(Instant.now());
        platformUserRepository.save(user);

        platformAuditService.record(
            "PLATFORM_USER_PASSWORD_RESET",
            "PLATFORM_USER",
            user.getId(),
            Map.of("email", user.getEmail())
        );
        return toSummary(user);
    }

    private PlatformUserSummary toSummary(PlatformUserEntity user) {
        return new PlatformUserSummary(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.getStatus(),
            user.getLastLoginAt(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            throw new ResponseStatusException(BAD_REQUEST, "Display name is required.");
        }
        return displayName.trim();
    }

    private PlatformRole normalizeRole(String role) {
        try {
            PlatformRole normalized = PlatformRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
            if (normalized == PlatformRole.PUBLIC_API_CLIENT) {
                throw new ResponseStatusException(BAD_REQUEST, "PUBLIC_API_CLIENT is not a valid platform user role.");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported platform role: " + role);
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported user status: " + status);
        }
        return normalized;
    }

    private String normalizePassword(String password) {
        if (!StringUtils.hasText(password) || password.trim().length() < 10) {
            throw new ResponseStatusException(BAD_REQUEST, "Password must be at least 10 characters.");
        }
        return password.trim();
    }

    private void enforceAdminGuardrails(PlatformUserEntity user,
                                        PlatformRole nextRole,
                                        String nextStatus) {
        boolean isCurrentlyActiveAdmin = PlatformRole.PLATFORM_ADMIN.name().equals(user.getRole())
            && "ACTIVE".equalsIgnoreCase(user.getStatus());
        boolean willRemainActiveAdmin = nextRole == PlatformRole.PLATFORM_ADMIN
            && "ACTIVE".equalsIgnoreCase(nextStatus);

        if (!isCurrentlyActiveAdmin || willRemainActiveAdmin) {
            return;
        }

        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal != null && principal.actorId().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot remove your own active platform admin access.");
        }

        long activeAdminCount = platformUserRepository.countByRoleIgnoreCaseAndStatusIgnoreCase(
            PlatformRole.PLATFORM_ADMIN.name(),
            "ACTIVE"
        );
        if (activeAdminCount <= 1) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one active platform admin must remain.");
        }
    }
}
