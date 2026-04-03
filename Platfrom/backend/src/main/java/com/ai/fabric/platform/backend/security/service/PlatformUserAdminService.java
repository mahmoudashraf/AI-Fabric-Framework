package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.model.CreatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.model.PlatformUserAccessSummary;
import com.ai.fabric.platform.backend.security.model.PlatformUserDeploymentAccessSummary;
import com.ai.fabric.platform.backend.security.model.PlatformUserSummary;
import com.ai.fabric.platform.backend.security.model.UpdatePlatformUserRequest;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformUserAdminService {

    private final PlatformUserRepository platformUserRepository;
    private final DeploymentAssignmentRepository deploymentAssignmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService platformAuditService;
    private final PlatformCustomerRepository platformCustomerRepository;
    private final PlatformCustomerAccessService platformCustomerAccessService;

    public PlatformUserAdminService(PlatformUserRepository platformUserRepository,
                                    DeploymentAssignmentRepository deploymentAssignmentRepository,
                                    DeploymentRepository deploymentRepository,
                                    PasswordEncoder passwordEncoder,
                                    PlatformAuditService platformAuditService,
                                    PlatformCustomerRepository platformCustomerRepository,
                                    PlatformCustomerAccessService platformCustomerAccessService) {
        this.platformUserRepository = platformUserRepository;
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.deploymentRepository = deploymentRepository;
        this.passwordEncoder = passwordEncoder;
        this.platformAuditService = platformAuditService;
        this.platformCustomerRepository = platformCustomerRepository;
        this.platformCustomerAccessService = platformCustomerAccessService;
    }

    public List<PlatformUserSummary> listUsers() {
        List<PlatformUserEntity> users = filterVisibleUsers(platformUserRepository.findAllByOrderByCreatedAtDesc());
        Map<String, PlatformCustomerEntity> customersById = loadCustomersById(users);
        return users.stream()
            .map(user -> toSummary(user, customersById.get(user.getCustomerId())))
            .toList();
    }

    public List<PlatformUserAccessSummary> listUsersWithAccess(String selectedDeploymentId) {
        List<PlatformUserEntity> users = filterVisibleUsers(platformUserRepository.findAllByOrderByCreatedAtDesc());
        List<DeploymentAssignmentEntity> assignments = deploymentAssignmentRepository.findAllByOrderByCreatedAtAsc();
        Map<String, DeploymentEntity> deploymentsById = filterVisibleDeployments(deploymentRepository.findAllById(
            assignments.stream().map(DeploymentAssignmentEntity::getDeploymentId).distinct().toList()
        )).stream().collect(Collectors.toMap(DeploymentEntity::getId, Function.identity()));
        Map<String, List<DeploymentAssignmentEntity>> assignmentsByUserId = assignments.stream()
            .filter(assignment -> deploymentsById.containsKey(assignment.getDeploymentId()))
            .collect(Collectors.groupingBy(DeploymentAssignmentEntity::getUserId));
        Map<String, PlatformCustomerEntity> customersById = loadCustomersById(users);

        return users.stream()
            .map(user -> toAccessSummary(
                user,
                assignmentsByUserId.getOrDefault(user.getId(), List.of()),
                deploymentsById,
                selectedDeploymentId,
                customersById.get(user.getCustomerId())
            ))
            .toList();
    }

    @Transactional
    public PlatformUserSummary createUser(CreatePlatformUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (platformUserRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "A platform user already exists for that email.");
        }

        PlatformRole role = normalizeRoleForActor(request.role());
        PlatformCustomerEntity customer = resolveCustomerBinding(role, request.customerId());

        PlatformUserEntity user = new PlatformUserEntity();
        Instant now = Instant.now();
        user.setId("usr-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(normalizedEmail);
        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setPasswordHash(passwordEncoder.encode(normalizePassword(request.password())));
        user.setRole(role.name());
        user.setCustomerId(customer == null ? null : customer.getId());
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
                "status", user.getStatus(),
                "customerId", user.getCustomerId() == null ? "" : user.getCustomerId()
            )
        );
        return toSummary(user, customer);
    }

    @Transactional
    public PlatformUserSummary updateUser(String userId, UpdatePlatformUserRequest request) {
        PlatformUserEntity user = platformUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Platform user not found: " + userId));
        requireManagedUserAccess(user);

        PlatformRole nextRole = normalizeRoleForActor(request.role());
        String nextStatus = normalizeStatus(request.status());
        PlatformCustomerEntity customer = resolveCustomerBinding(nextRole, request.customerId());
        enforceAdminGuardrails(user, nextRole, nextStatus, customer);

        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setRole(nextRole.name());
        user.setCustomerId(customer == null ? null : customer.getId());
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
                "status", user.getStatus(),
                "customerId", user.getCustomerId() == null ? "" : user.getCustomerId()
            )
        );
        return toSummary(user, customer);
    }

    @Transactional
    public PlatformUserSummary resetPassword(String userId, String password) {
        PlatformUserEntity user = platformUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Platform user not found: " + userId));
        requireManagedUserAccess(user);

        user.setPasswordHash(passwordEncoder.encode(normalizePassword(password)));
        user.setUpdatedAt(Instant.now());
        platformUserRepository.save(user);

        platformAuditService.record(
            "PLATFORM_USER_PASSWORD_RESET",
            "PLATFORM_USER",
            user.getId(),
            Map.of("email", user.getEmail())
        );
        PlatformCustomerEntity customer = user.getCustomerId() == null
            ? null
            : platformCustomerRepository.findById(user.getCustomerId()).orElse(null);
        return toSummary(user, customer);
    }

    private PlatformUserSummary toSummary(PlatformUserEntity user, PlatformCustomerEntity customer) {
        return new PlatformUserSummary(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            customer == null ? null : customer.getId(),
            customer == null ? null : customer.getName(),
            customer == null ? null : customer.getSlug(),
            user.getStatus(),
            user.getLastLoginAt(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private PlatformUserAccessSummary toAccessSummary(PlatformUserEntity user,
                                                      List<DeploymentAssignmentEntity> assignments,
                                                      Map<String, DeploymentEntity> deploymentsById,
                                                      String selectedDeploymentId,
                                                      PlatformCustomerEntity customer) {
        List<PlatformUserDeploymentAccessSummary> assignedDeployments = assignments.stream()
            .map(assignment -> toDeploymentAccessSummary(assignment, deploymentsById.get(assignment.getDeploymentId())))
            .sorted(Comparator
                .comparing(PlatformUserDeploymentAccessSummary::deploymentName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlatformUserDeploymentAccessSummary::deploymentEnvironment, String.CASE_INSENSITIVE_ORDER))
            .toList();

        PlatformUserDeploymentAccessSummary selectedDeploymentAssignment = assignedDeployments.stream()
            .filter(assignment -> assignment.deploymentId().equals(selectedDeploymentId))
            .findFirst()
            .orElse(null);

        return new PlatformUserAccessSummary(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            customer == null ? null : customer.getId(),
            customer == null ? null : customer.getName(),
            customer == null ? null : customer.getSlug(),
            user.getStatus(),
            user.getLastLoginAt(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            assignedDeployments.size(),
            countAssignments(assignedDeployments, "DEPLOYMENT_ADMIN"),
            countAssignments(assignedDeployments, "DEPLOYMENT_EDITOR"),
            countAssignments(assignedDeployments, "DEPLOYMENT_OPERATOR"),
            countAssignments(assignedDeployments, "DEPLOYMENT_VIEWER"),
            selectedDeploymentAssignment,
            assignedDeployments
        );
    }

    private PlatformUserDeploymentAccessSummary toDeploymentAccessSummary(DeploymentAssignmentEntity assignment,
                                                                          DeploymentEntity deployment) {
        return new PlatformUserDeploymentAccessSummary(
            assignment.getId(),
            assignment.getDeploymentId(),
            deployment == null ? "Unknown deployment" : deployment.getName(),
            deployment == null ? "unknown" : deployment.getEnvironmentName(),
            deployment == null ? "UNKNOWN" : deployment.getStatus(),
            assignment.getAssignmentRole(),
            assignment.getCreatedAt(),
            assignment.getUpdatedAt()
        );
    }

    private int countAssignments(List<PlatformUserDeploymentAccessSummary> assignments, String role) {
        return (int) assignments.stream()
            .filter(assignment -> role.equalsIgnoreCase(assignment.assignmentRole()))
            .count();
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

    private PlatformRole normalizeRoleForActor(String role) {
        PlatformRole normalized = normalizeRole(role);
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() == PlatformRole.PLATFORM_ADMIN) {
            return normalized;
        }
        if (principal.role() != PlatformRole.CUSTOMER_ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Only platform administrators can manage this role.");
        }
        if (normalized != PlatformRole.CUSTOMER_ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer administrators can only manage CUSTOMER_ADMIN users.");
        }
        return normalized;
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

    private PlatformCustomerEntity resolveCustomerBinding(PlatformRole role, String customerId) {
        if (role != PlatformRole.CUSTOMER_ADMIN) {
            if (StringUtils.hasText(customerId)) {
                throw new ResponseStatusException(BAD_REQUEST, "customerId is only supported for CUSTOMER_ADMIN users.");
            }
            return null;
        }
        String resolvedCustomerId = requestedOrScopedCustomerId(customerId);
        if (!StringUtils.hasText(resolvedCustomerId)) {
            throw new ResponseStatusException(BAD_REQUEST, "customerId is required for CUSTOMER_ADMIN users.");
        }
        PlatformCustomerEntity customer = platformCustomerRepository.findById(resolvedCustomerId.trim())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer not found: " + customerId));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer is not active: " + resolvedCustomerId);
        }
        if (customer.isPlatformManaged()) {
            throw new ResponseStatusException(BAD_REQUEST, "Platform-managed customers cannot be assigned to CUSTOMER_ADMIN users.");
        }
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
        return customer;
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
                                        String nextStatus,
                                        PlatformCustomerEntity nextCustomer) {
        boolean isCurrentlyActiveAdmin = PlatformRole.PLATFORM_ADMIN.name().equals(user.getRole())
            && "ACTIVE".equalsIgnoreCase(user.getStatus());
        boolean willRemainActiveAdmin = nextRole == PlatformRole.PLATFORM_ADMIN
            && "ACTIVE".equalsIgnoreCase(nextStatus);

        if (isCurrentlyActiveAdmin && !willRemainActiveAdmin) {
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

        enforceCustomerAdminGuardrails(user, nextRole, nextStatus, nextCustomer);
    }

    private void enforceCustomerAdminGuardrails(PlatformUserEntity user,
                                                PlatformRole nextRole,
                                                String nextStatus,
                                                PlatformCustomerEntity nextCustomer) {
        boolean isCurrentlyActiveCustomerAdmin = PlatformRole.CUSTOMER_ADMIN.name().equals(user.getRole())
            && "ACTIVE".equalsIgnoreCase(user.getStatus())
            && StringUtils.hasText(user.getCustomerId());
        boolean willRemainActiveCustomerAdmin = nextRole == PlatformRole.CUSTOMER_ADMIN
            && "ACTIVE".equalsIgnoreCase(nextStatus)
            && nextCustomer != null
            && nextCustomer.getId().equals(user.getCustomerId());

        if (!isCurrentlyActiveCustomerAdmin || willRemainActiveCustomerAdmin) {
            return;
        }

        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal != null && principal.actorId().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot remove your own active customer admin access.");
        }

        long activeCustomerAdminCount = platformUserRepository.countByRoleIgnoreCaseAndStatusIgnoreCaseAndCustomerId(
            PlatformRole.CUSTOMER_ADMIN.name(),
            "ACTIVE",
            user.getCustomerId()
        );
        if (activeCustomerAdminCount <= 1) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one active customer admin must remain for this customer.");
        }
    }

    private Map<String, PlatformCustomerEntity> loadCustomersById(List<PlatformUserEntity> users) {
        List<String> customerIds = users.stream()
            .map(PlatformUserEntity::getCustomerId)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return platformCustomerRepository.findAllById(customerIds).stream()
            .collect(Collectors.toMap(PlatformCustomerEntity::getId, Function.identity()));
    }

    private List<PlatformUserEntity> filterVisibleUsers(List<PlatformUserEntity> users) {
        PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
        if (scope == null) {
            return users;
        }
        return users.stream()
            .filter(user -> scope.customerId().equals(user.getCustomerId()))
            .toList();
    }

    private List<DeploymentEntity> filterVisibleDeployments(List<DeploymentEntity> deployments) {
        PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
        if (scope == null) {
            return deployments;
        }
        return deployments.stream()
            .filter(deployment -> scope.customerId().equals(deployment.getCustomerId()))
            .toList();
    }

    private void requireManagedUserAccess(PlatformUserEntity user) {
        PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
        if (scope == null) {
            return;
        }
        if (!scope.customerId().equals(user.getCustomerId())) {
            throw new ResponseStatusException(NOT_FOUND, "Platform user not found: " + user.getId());
        }
    }

    private String requestedOrScopedCustomerId(String customerId) {
        PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
        if (scope == null) {
            return StringUtils.hasText(customerId) ? customerId.trim() : null;
        }
        if (!StringUtils.hasText(customerId)) {
            return scope.customerId();
        }
        if (!scope.customerId().equals(customerId.trim())) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer admins can only manage users inside their own customer boundary.");
        }
        return scope.customerId();
    }
}
