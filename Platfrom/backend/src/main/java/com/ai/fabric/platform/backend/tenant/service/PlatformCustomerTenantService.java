package com.ai.fabric.platform.backend.tenant.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTenantScopedVectorRegistryService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantBindingSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorHandleSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSummary;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesRequest;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformCustomerTenantService {

    public static final String INTERNAL_CUSTOMER_ID = "cust-internal";
    private static final String INTERNAL_CUSTOMER_SLUG = "platform-internal";

    private final PlatformCustomerRepository customerRepository;
    private final PlatformTenantRepository tenantRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService;
    private final PlatformAuditService platformAuditService;
    private final PlatformCustomerAccessService platformCustomerAccessService;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;

    public PlatformCustomerTenantService(PlatformCustomerRepository customerRepository,
                                         PlatformTenantRepository tenantRepository,
                                         DeploymentRepository deploymentRepository,
                                         DeploymentVersionRepository deploymentVersionRepository,
                                         DeploymentReleaseRepository deploymentReleaseRepository,
                                         DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService,
                                         PlatformAuditService platformAuditService,
                                         PlatformCustomerAccessService platformCustomerAccessService,
                                         PlatformCustomerConsumerService platformCustomerConsumerService) {
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentTenantScopedVectorRegistryService = deploymentTenantScopedVectorRegistryService;
        this.platformAuditService = platformAuditService;
        this.platformCustomerAccessService = platformCustomerAccessService;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
    }

    @Transactional
    public PlatformCustomerEntity ensureInternalCustomer() {
        return customerRepository.findById(INTERNAL_CUSTOMER_ID).orElseGet(() -> {
            Instant now = Instant.now();
            PlatformCustomerEntity customer = new PlatformCustomerEntity();
            customer.setId(INTERNAL_CUSTOMER_ID);
            customer.setName("Platform Internal");
            customer.setSlug(INTERNAL_CUSTOMER_SLUG);
            customer.setDescription("Platform-owned customer boundary for internal, legacy, and system-created deployments.");
            customer.setStatus("ACTIVE");
            customer.setPlatformManaged(true);
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            return customerRepository.save(customer);
        });
    }

    public List<PlatformCustomerSummary> listCustomers() {
        ensureInternalCustomer();
        return summarizeCustomers(
            platformCustomerAccessService.filterVisibleCustomers(customerRepository.findAllByOrderByPlatformManagedDescCreatedAtAsc())
        );
    }

    @Transactional
    public PlatformCustomerSummary createCustomer(CreatePlatformCustomerRequest request) {
        if (!platformCustomerAccessService.canCreateCustomers()) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer creation is restricted to platform administrators.");
        }
        Instant now = Instant.now();
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId(generateId("cus"));
        customer.setName(normalizeRequiredName(request.name(), "Customer name"));
        customer.setSlug(nextCustomerSlug(customer.getName(), null));
        customer.setDescription(normalizeDescription(request.description()));
        customer.setStatus("ACTIVE");
        customer.setPlatformManaged(false);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        platformAuditService.record(
            "CUSTOMER_CREATED",
            "PLATFORM_CUSTOMER",
            customer.getId(),
            Map.of(
                "customerId", customer.getId(),
                "customerName", customer.getName(),
                "customerSlug", customer.getSlug()
            )
        );

        return summarizeCustomer(customer);
    }

    @Transactional
    public PlatformCustomerSummary updateCustomer(String customerId, UpdatePlatformCustomerRequest request) {
        PlatformCustomerEntity customer = requireCustomer(customerId);
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
        assertCustomerEditable(customer);

        customer.setName(normalizeRequiredName(request.name(), "Customer name"));
        customer.setSlug(nextCustomerSlug(customer.getName(), customer.getId()));
        customer.setDescription(normalizeDescription(request.description()));
        customer.setUpdatedAt(Instant.now());
        customerRepository.save(customer);

        platformAuditService.record(
            "CUSTOMER_UPDATED",
            "PLATFORM_CUSTOMER",
            customer.getId(),
            Map.of(
                "customerId", customer.getId(),
                "customerName", customer.getName(),
                "customerSlug", customer.getSlug()
            )
        );

        return summarizeCustomer(customer);
    }

    @Transactional
    public PlatformTenantSummary createTenant(String customerId, CreatePlatformTenantRequest request) {
        PlatformCustomerEntity customer = requireActiveCustomer(customerId);
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
        Instant now = Instant.now();
        PlatformTenantEntity tenant = new PlatformTenantEntity();
        tenant.setId(generateId("ten"));
        tenant.setCustomerId(customer.getId());
        tenant.setName(normalizeRequiredName(request.name(), "Tenant name"));
        tenant.setSlug(nextTenantSlug(customer.getId(), tenant.getName(), null));
        tenant.setDescription(normalizeDescription(request.description()));
        tenant.setStatus("ACTIVE");
        tenant.setPlatformManaged(false);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantRepository.save(tenant);

        platformAuditService.record(
            "TENANT_CREATED",
            "PLATFORM_TENANT",
            tenant.getId(),
            Map.of(
                "customerId", customer.getId(),
                "tenantId", tenant.getId(),
                "tenantName", tenant.getName(),
                "tenantSlug", tenant.getSlug()
            )
        );

        return summarizeTenant(tenant, customer, null, deploymentTenantScopedVectorRegistryService.summarizeTenant(tenant.getId()));
    }

    @Transactional
    public PlatformTenantSummary updateTenant(String tenantId, UpdatePlatformTenantRequest request) {
        PlatformTenantEntity tenant = requireTenant(tenantId);
        assertTenantEditable(tenant);
        PlatformCustomerEntity customer = requireCustomer(tenant.getCustomerId());
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());

        tenant.setName(normalizeRequiredName(request.name(), "Tenant name"));
        tenant.setSlug(nextTenantSlug(customer.getId(), tenant.getName(), tenant.getId()));
        tenant.setDescription(normalizeDescription(request.description()));
        tenant.setUpdatedAt(Instant.now());
        tenantRepository.save(tenant);

        platformAuditService.record(
            "TENANT_UPDATED",
            "PLATFORM_TENANT",
            tenant.getId(),
            Map.of(
                "customerId", customer.getId(),
                "tenantId", tenant.getId(),
                "tenantName", tenant.getName(),
                "tenantSlug", tenant.getSlug()
            )
        );

        DeploymentEntity deployment = deploymentRepository.findByTenantId(tenant.getId()).orElse(null);
        return summarizeTenant(tenant, customer, deployment, deploymentTenantScopedVectorRegistryService.summarizeTenant(tenant.getId()));
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantSharedVectorHandleSummary> listTenantSharedVectorHandles(String tenantId) {
        PlatformTenantEntity tenant = requireTenant(tenantId);
        platformCustomerAccessService.requireCustomerManagementAccess(tenant.getCustomerId());
        return deploymentTenantScopedVectorRegistryService.listTenantHandles(tenant.getId());
    }

    @Transactional
    public PurgePlatformTenantSharedVectorHandlesSummary purgeTenantSharedVectorHandles(String tenantId,
                                                                                        PurgePlatformTenantSharedVectorHandlesRequest request) {
        PlatformTenantEntity tenant = requireActiveTenant(tenantId);
        platformCustomerAccessService.requireCustomerManagementAccess(tenant.getCustomerId());
        return deploymentTenantScopedVectorRegistryService.purgeDetachedHandleHistory(tenant.getId(), tenant.getName(), request);
    }

    @Transactional
    public ResolvedDeploymentBinding resolveBindingForNewDeployment(String deploymentName,
                                                                   String environmentName,
                                                                   String requestedCustomerId,
                                                                   String requestedTenantId) {
        if (StringUtils.hasText(requestedTenantId)) {
            PlatformTenantEntity tenant = requireActiveTenant(requestedTenantId.trim());
            PlatformCustomerEntity customer = requireActiveCustomer(tenant.getCustomerId());
            platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
            validateTenantMatchesRequestedCustomer(customer, requestedCustomerId);
            ensureTenantIsUnbound(tenant.getId(), null);
            return new ResolvedDeploymentBinding(customer, tenant, false);
        }

        PlatformCustomerEntity customer = resolveRequestedOrDefaultCustomer(requestedCustomerId);
        PlatformTenantEntity tenant = createAutoTenant(customer, deploymentName, environmentName);
        return new ResolvedDeploymentBinding(customer, tenant, true);
    }

    @Transactional(readOnly = true)
    public DeploymentBindingPreview previewBindingForNewDeployment(String requestedCustomerId, String requestedTenantId) {
        if (StringUtils.hasText(requestedTenantId)) {
            PlatformTenantEntity tenant = requireActiveTenant(requestedTenantId.trim());
            PlatformCustomerEntity customer = requireActiveCustomer(tenant.getCustomerId());
            platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
            validateTenantMatchesRequestedCustomer(customer, requestedCustomerId);
            ensureTenantIsUnbound(tenant.getId(), null);
            return new DeploymentBindingPreview(
                customer.getId(),
                customer.getName(),
                customer.getSlug(),
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                false
            );
        }

        if (!StringUtils.hasText(requestedCustomerId)) {
            PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
            if (scope == null) {
                throw new ResponseStatusException(BAD_REQUEST, "customerId is required when tenantId is not provided.");
            }
            return new DeploymentBindingPreview(
                scope.customerId(),
                scope.customerName(),
                scope.customerSlug(),
                null,
                null,
                null,
                true
            );
        }

        PlatformCustomerEntity customer = requireActiveCustomer(requestedCustomerId.trim());
        platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
        return new DeploymentBindingPreview(
            customer.getId(),
            customer.getName(),
            customer.getSlug(),
            null,
            null,
            null,
            true
        );
    }

    @Transactional
    public ResolvedDeploymentBinding resolveBindingForExistingDeployment(DeploymentEntity deployment,
                                                                        String requestedCustomerId,
                                                                        String requestedTenantId) {
        if (StringUtils.hasText(requestedTenantId)) {
            PlatformTenantEntity tenant = requireActiveTenant(requestedTenantId.trim());
            PlatformCustomerEntity customer = requireActiveCustomer(tenant.getCustomerId());
            platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
            validateTenantMatchesRequestedCustomer(customer, requestedCustomerId);
            ensureTenantIsUnbound(tenant.getId(), deployment.getId());
            return new ResolvedDeploymentBinding(customer, tenant, false);
        }

        PlatformCustomerEntity customer = resolveRequestedOrDefaultCustomer(requestedCustomerId);
        PlatformTenantEntity tenant = createAutoTenant(customer, deployment.getName(), deployment.getEnvironmentName());
        return new ResolvedDeploymentBinding(customer, tenant, true);
    }

    private PlatformCustomerEntity resolveRequestedOrDefaultCustomer(String requestedCustomerId) {
        if (StringUtils.hasText(requestedCustomerId)) {
            PlatformCustomerEntity customer = requireActiveCustomer(requestedCustomerId.trim());
            platformCustomerAccessService.requireCustomerManagementAccess(customer.getId());
            return customer;
        }
        PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
        if (scope != null) {
            return requireActiveCustomer(scope.customerId());
        }
        return ensureInternalCustomer();
    }

    public DeploymentTenantBindingSummary summarizeBinding(DeploymentEntity deployment) {
        if (deployment == null) {
            return null;
        }
        return summarizeBindings(List.of(deployment)).get(deployment.getId());
    }

    public Map<String, DeploymentTenantBindingSummary> summarizeBindings(Collection<DeploymentEntity> deployments) {
        if (deployments == null || deployments.isEmpty()) {
            return Map.of();
        }

        Map<String, PlatformCustomerEntity> customersById = customerRepository.findAllById(
            deployments.stream().map(DeploymentEntity::getCustomerId).distinct().toList()
        ).stream().collect(Collectors.toMap(PlatformCustomerEntity::getId, entity -> entity));

        Map<String, PlatformTenantEntity> tenantsById = tenantRepository.findAllById(
            deployments.stream().map(DeploymentEntity::getTenantId).distinct().toList()
        ).stream().collect(Collectors.toMap(PlatformTenantEntity::getId, entity -> entity));

        Map<String, DeploymentTenantBindingSummary> summaries = new LinkedHashMap<>();
        for (DeploymentEntity deployment : deployments) {
            PlatformCustomerEntity customer = customersById.get(deployment.getCustomerId());
            PlatformTenantEntity tenant = tenantsById.get(deployment.getTenantId());
            int publishedVersionCount = (int) deploymentVersionRepository.countByDeploymentId(deployment.getId());
            int releaseCount = (int) deploymentReleaseRepository.countByDeploymentId(deployment.getId());
            boolean mutable = publishedVersionCount == 0 && releaseCount == 0;
            summaries.put(deployment.getId(), toBindingSummary(customer, tenant, mutable, publishedVersionCount, releaseCount));
        }
        return summaries;
    }

    private List<PlatformCustomerSummary> summarizeCustomers(List<PlatformCustomerEntity> customers) {
        java.util.Set<String> customerIds = customers.stream()
            .map(PlatformCustomerEntity::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PlatformTenantEntity> tenants = tenantRepository.findAllByOrderByCreatedAtAsc().stream()
            .filter(tenant -> customerIds.contains(tenant.getCustomerId()))
            .toList();
        Map<String, List<PlatformTenantEntity>> tenantsByCustomerId = tenants.stream()
            .collect(Collectors.groupingBy(PlatformTenantEntity::getCustomerId, LinkedHashMap::new, Collectors.toList()));
        Map<String, DeploymentEntity> deploymentByTenantId = deploymentRepository.findAll().stream()
            .filter(deployment -> StringUtils.hasText(deployment.getTenantId()))
            .collect(Collectors.toMap(DeploymentEntity::getTenantId, deployment -> deployment, (left, right) -> left, LinkedHashMap::new));
        Map<String, PlatformCustomerEntity> customerById = customers.stream()
            .collect(Collectors.toMap(PlatformCustomerEntity::getId, customer -> customer, (left, right) -> left, LinkedHashMap::new));
        Map<String, PlatformTenantSharedVectorSummary> sharedVectorByTenantId =
            deploymentTenantScopedVectorRegistryService.summarizeTenants(
                tenants.stream().map(PlatformTenantEntity::getId).toList()
            );
        Map<String, PlatformTenantSummary> tenantSummariesById = tenants.stream()
            .collect(Collectors.toMap(
                PlatformTenantEntity::getId,
                tenant -> summarizeTenant(
                    tenant,
                    customerById.get(tenant.getCustomerId()),
                    deploymentByTenantId.get(tenant.getId()),
                    sharedVectorByTenantId.get(tenant.getId())
                ),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, List<PlatformConsumerSummary>> consumersByCustomerId =
            platformCustomerConsumerService.summarizeConsumersByCustomerIds(customerIds);

        return customers.stream()
            .map(customer -> {
                List<PlatformTenantSummary> tenantSummaries = tenantsByCustomerId
                    .getOrDefault(customer.getId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(PlatformTenantEntity::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(tenant -> tenantSummariesById.get(tenant.getId()))
                    .toList();
                int deploymentCount = (int) tenantSummaries.stream()
                    .filter(tenant -> tenant.boundDeploymentId() != null)
                    .count();
                List<PlatformConsumerSummary> consumerSummaries = consumersByCustomerId.getOrDefault(customer.getId(), List.of());
                return new PlatformCustomerSummary(
                    customer.getId(),
                    customer.getName(),
                    customer.getSlug(),
                    customer.getDescription(),
                    customer.getStatus(),
                    customer.isPlatformManaged(),
                    tenantSummaries.size(),
                    deploymentCount,
                    consumerSummaries.size(),
                    customer.getCreatedAt(),
                    customer.getUpdatedAt(),
                    tenantSummaries,
                    consumerSummaries
                );
            })
            .sorted(Comparator
                .comparing(PlatformCustomerSummary::platformManaged).reversed()
                .thenComparing(PlatformCustomerSummary::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private PlatformCustomerSummary summarizeCustomer(PlatformCustomerEntity customer) {
        return summarizeCustomers(List.of(customer)).stream().findFirst().orElseThrow();
    }

    private PlatformTenantSummary summarizeTenant(PlatformTenantEntity tenant,
                                                  PlatformCustomerEntity customer,
                                                  DeploymentEntity deployment,
                                                  PlatformTenantSharedVectorSummary sharedVector) {
        return new PlatformTenantSummary(
            tenant.getId(),
            customer.getId(),
            customer.getName(),
            tenant.getName(),
            tenant.getSlug(),
            tenant.getDescription(),
            tenant.getStatus(),
            tenant.isPlatformManaged(),
            deployment == null ? null : deployment.getId(),
            deployment == null ? null : deployment.getName(),
            deployment == null ? null : deployment.getEnvironmentName(),
            sharedVector,
            tenant.getCreatedAt(),
            tenant.getUpdatedAt()
        );
    }

    private PlatformCustomerEntity requireCustomer(String customerId) {
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer not found: " + customerId));
    }

    private PlatformCustomerEntity requireActiveCustomer(String customerId) {
        PlatformCustomerEntity customer = requireCustomer(customerId);
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer is not active: " + customerId);
        }
        return customer;
    }

    private PlatformTenantEntity requireTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant not found: " + tenantId));
    }

    private PlatformTenantEntity requireActiveTenant(String tenantId) {
        PlatformTenantEntity tenant = requireTenant(tenantId);
        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Tenant is not active: " + tenantId);
        }
        return tenant;
    }

    private void assertCustomerEditable(PlatformCustomerEntity customer) {
        if (customer.isPlatformManaged()) {
            throw new ResponseStatusException(BAD_REQUEST, "Platform-managed customers cannot be edited.");
        }
    }

    private void assertTenantEditable(PlatformTenantEntity tenant) {
        if (tenant.isPlatformManaged()) {
            throw new ResponseStatusException(BAD_REQUEST, "Platform-managed tenants cannot be edited.");
        }
    }

    private void validateTenantMatchesRequestedCustomer(PlatformCustomerEntity actualCustomer, String requestedCustomerId) {
        if (StringUtils.hasText(requestedCustomerId)
            && !actualCustomer.getId().equals(requestedCustomerId.trim())) {
            throw new ResponseStatusException(BAD_REQUEST, "tenantId does not belong to customerId.");
        }
    }

    private void ensureTenantIsUnbound(String tenantId, String allowedDeploymentId) {
        deploymentRepository.findByTenantId(tenantId).ifPresent(existing -> {
            if (allowedDeploymentId == null || !allowedDeploymentId.equals(existing.getId())) {
                throw new ResponseStatusException(CONFLICT, "Tenant is already bound to deployment: " + existing.getId());
            }
        });
    }

    private PlatformTenantEntity createAutoTenant(PlatformCustomerEntity customer,
                                                  String deploymentName,
                                                  String environmentName) {
        Instant now = Instant.now();
        PlatformTenantEntity tenant = new PlatformTenantEntity();
        tenant.setId(generateId("ten"));
        tenant.setCustomerId(customer.getId());
        tenant.setName(buildAutoTenantName(deploymentName, environmentName));
        tenant.setSlug(nextTenantSlug(customer.getId(), tenant.getName(), null));
        tenant.setDescription("Auto-created dedicated tenant binding for deployment " + deploymentName.trim() + ".");
        tenant.setStatus("ACTIVE");
        tenant.setPlatformManaged(customer.isPlatformManaged());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        return tenantRepository.save(tenant);
    }

    private String buildAutoTenantName(String deploymentName, String environmentName) {
        String baseName = normalizeRequiredName(deploymentName, "Deployment name");
        String environment = StringUtils.hasText(environmentName) ? environmentName.trim() : "";
        if (environment.isEmpty()) {
            return baseName;
        }
        return baseName + " " + environment;
    }

    private String nextCustomerSlug(String name, String existingCustomerId) {
        String baseSlug = slugify(name);
        boolean baseAvailable = customerRepository.findBySlugIgnoreCase(baseSlug)
            .map(existing -> existingCustomerId != null && existing.getId().equals(existingCustomerId))
            .orElse(true);
        return nextUniqueSlug(
            baseSlug,
            baseAvailable,
            candidate -> customerRepository.findBySlugIgnoreCase(candidate)
                .map(existing -> existingCustomerId != null && existing.getId().equals(existingCustomerId))
                .orElse(true)
        );
    }

    private String nextTenantSlug(String customerId, String name, String existingTenantId) {
        String baseSlug = slugify(name);
        if (tenantRepository.findByCustomerIdAndSlugIgnoreCase(customerId, baseSlug)
            .map(existing -> existingTenantId != null && existing.getId().equals(existingTenantId))
            .orElse(true)) {
            return baseSlug;
        }

        int suffix = 2;
        while (true) {
            String candidate = baseSlug + "-" + suffix;
            boolean available = tenantRepository.findByCustomerIdAndSlugIgnoreCase(customerId, candidate)
                .map(existing -> existingTenantId != null && existing.getId().equals(existingTenantId))
                .orElse(true);
            if (available) {
                return candidate;
            }
            suffix += 1;
        }
    }

    private String nextUniqueSlug(String baseSlug,
                                  boolean baseAvailable,
                                  java.util.function.Function<String, Boolean> existingMatchesCurrent) {
        if (baseAvailable) {
            return baseSlug;
        }
        int suffix = 2;
        while (true) {
            String candidate = baseSlug + "-" + suffix;
            if (existingMatchesCurrent.apply(candidate)) {
                return candidate;
            }
            suffix += 1;
        }
    }

    private String slugify(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "A non-empty name is required to generate a slug.");
        }
        String ascii = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (slug.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "The supplied name cannot be normalized into a valid slug.");
        }
        return slug;
    }

    private String normalizeRequiredName(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(BAD_REQUEST, label + " is required.");
        }
        return value.trim();
    }

    private String normalizeDescription(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DeploymentTenantBindingSummary toBindingSummary(PlatformCustomerEntity customer,
                                                            PlatformTenantEntity tenant,
                                                            boolean mutable,
                                                            int publishedVersionCount,
                                                            int releaseCount) {
        String bindingChangeStatus = mutable ? "EDITABLE" : "MIGRATION_REQUIRED";
        String bindingChangeMessage = mutable
            ? "Binding can be changed directly because this deployment has no published versions or releases yet."
            : "Binding is locked because this deployment already has "
                + publishedVersionCount
                + " published version(s) and "
                + releaseCount
                + " release(s). Tenant reassignment now requires a governed migration flow.";
        return new DeploymentTenantBindingSummary(
            customer == null ? null : customer.getId(),
            customer == null ? "Unknown customer" : customer.getName(),
            customer == null ? null : customer.getSlug(),
            customer == null ? "UNKNOWN" : customer.getStatus(),
            customer != null && customer.isPlatformManaged(),
            tenant == null ? null : tenant.getId(),
            tenant == null ? "Unknown tenant" : tenant.getName(),
            tenant == null ? null : tenant.getSlug(),
            tenant == null ? "UNKNOWN" : tenant.getStatus(),
            tenant != null && tenant.isPlatformManaged(),
            mutable,
            publishedVersionCount,
            releaseCount,
            bindingChangeStatus,
            bindingChangeMessage
        );
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public record ResolvedDeploymentBinding(
        PlatformCustomerEntity customer,
        PlatformTenantEntity tenant,
        boolean autoCreatedTenant
    ) {
    }

    public record DeploymentBindingPreview(
        String customerId,
        String customerName,
        String customerSlug,
        String tenantId,
        String tenantName,
        String tenantSlug,
        boolean autoCreatedTenant
    ) {
    }
}
