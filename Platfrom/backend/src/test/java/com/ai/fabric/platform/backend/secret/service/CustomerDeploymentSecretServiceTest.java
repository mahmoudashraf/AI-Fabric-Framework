package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.entity.*;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretContracts;
import com.ai.fabric.platform.backend.secret.model.UpsertDeploymentProviderSecretBindingRequest;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerDeploymentSecretServiceTest {
    private final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    private final DeploymentProviderSecretBindingRepository bindings = mock(DeploymentProviderSecretBindingRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final PlatformCustomerAccessService customers = mock(PlatformCustomerAccessService.class);
    private final DeploymentProviderSecretOverrideService overrides = mock(DeploymentProviderSecretOverrideService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final CustomerDeploymentSecretService service = new CustomerDeploymentSecretService(secrets, bindings, deployments,
        access, customers, new DeploymentSecretPurposeCatalog(), overrides, audit);
    private final DeploymentEntity brain = new DeploymentEntity();

    @BeforeEach void setup() {
        signIn(PlatformRole.CUSTOMER_ADMIN);
        brain.setId("dep-one"); brain.setCustomerId("customer-one"); brain.setStatus("DRAFT");
        when(deployments.findById(brain.getId())).thenReturn(Optional.of(brain));
        when(access.requireDeploymentAdminAccess(brain)).thenReturn(brain);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void createCustomKeyUsesServerOwnedScopeAndReturnsNoValue() throws Exception {
        var result = service.create(brain.getId(), create("STOCK_API", CustomerDeploymentSecretService.INTEGRATION, "test-only-private-value", null));
        var entity = ArgumentCaptor.forClass(PlatformSecretEntity.class); verify(secrets).save(entity.capture());
        assertThat(entity.getValue().getDeploymentId()).isEqualTo(brain.getId());
        assertThat(entity.getValue().getScopeType()).isEqualTo(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE);
        assertThat(entity.getValue().getOwnerType()).isEqualTo(PlatformSecretOwnerType.DEPLOYMENT);
        assertThat(entity.getValue().getTargetProfileId()).isNull();
        assertThat(entity.getValue().getCleanupPolicy()).isEqualTo(PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        assertThat(result.getFirst().label()).isEqualTo("STOCK_API");
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("test-only-private-value", "secretValue");
        verifyNoInteractions(overrides);
        verify(audit).record(eq("CUSTOMER_SECRET_CREATED"), eq("DEPLOYMENT"), eq(brain.getId()), argThat(details -> !details.toString().contains("test-only-private-value")));
    }

    @Test void providerKeyUsesExistingBindingAndRequiresOwnValueWithoutFallback() {
        var result = service.create(brain.getId(), create("MY_VECTOR", "QDRANT_API_KEY", "test-only-value", null));
        verify(overrides).upsertBinding(brain.getId(), new UpsertDeploymentProviderSecretBindingRequest(
            "QDRANT_API_KEY", "REQUIRE_OVERRIDE", result.getFirst().name(), null));
    }

    @Test void pairedCredentialsAreBoundTogetherAndMissingPasswordWritesNothing() {
        assertBad(() -> service.create(brain.getId(), create("MY_MILVUS", "MILVUS_RUNTIME_CREDENTIALS", "test-user", null)));
        verify(secrets, never()).save(any());
        var result = service.create(brain.getId(), create("MY_MILVUS", "MILVUS_RUNTIME_CREDENTIALS", "test-user", "test-password"));
        assertThat(result).hasSize(2);
        verify(overrides).upsertBinding(brain.getId(), new UpsertDeploymentProviderSecretBindingRequest(
            "MILVUS_RUNTIME_CREDENTIALS", "REQUIRE_OVERRIDE", result.get(0).name(), result.get(1).name()));
    }

    @Test void sameAliasOnDifferentBrainsHasDifferentReferences() {
        var first = service.create(brain.getId(), create("API", CustomerDeploymentSecretService.INTEGRATION, "value-one", null));
        var second = new DeploymentEntity(); second.setId("dep-other"); second.setStatus("DRAFT");
        when(deployments.findById(second.getId())).thenReturn(Optional.of(second));
        when(access.requireDeploymentAdminAccess(second)).thenReturn(second);
        var other = service.create(second.getId(), create("API", CustomerDeploymentSecretService.INTEGRATION, "value-two", null));
        assertThat(first.getFirst().name()).isNotEqualTo(other.getFirst().name());
    }

    @Test void creationRejectsUnsupportedPurposeNamesAndBlankValuesBeforePersistence() {
        for (String name : List.of("", "lowercase", "A/B", "X".repeat(65))) assertBad(() -> service.create(brain.getId(), create(name, "OPENAI_API_KEY", "test-only", null)));
        assertBad(() -> service.create(brain.getId(), create("KEY", "PLATFORM_ARTIFACT_SIGNING_KEY", "test-only", null)));
        assertBad(() -> service.create(brain.getId(), create("KEY", CustomerDeploymentSecretService.INTEGRATION, " ", null)));
        assertBad(() -> service.create(brain.getId(), create("KEY", CustomerDeploymentSecretService.INTEGRATION, "x".repeat(16385), null)));
        verify(secrets, never()).save(any()); verifyNoInteractions(overrides, audit);
    }

    @Test void duplicateNameCannotOverwriteExistingKey() {
        when(secrets.existsById(anyString())).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.create(brain.getId(), create("API", CustomerDeploymentSecretService.INTEGRATION, "new-value", null)));
        verify(secrets, never()).save(any());
    }

    @Test void valuesNeverAppearInListAndForeignOrPlatformManagedRowsAreExcluded() throws Exception {
        var own = key("OWN", brain.getId()); var foreign = key("FOREIGN", "dep-other");
        var managed = key("MANAGED_KEY", brain.getId()); managed.setManagedByPlatform(true);
        when(secrets.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE, brain.getId())).thenReturn(List.of(own, foreign, managed));
        var result = service.list(brain.getId());
        assertThat(result.secrets()).extracting(DeploymentSecretContracts.Secret::name).containsExactly("OWN");
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("test-only-private-value");
    }

    @Test void ownRotationRetainsScopeAndBindingAndClearsNoOtherKey() {
        var own = key("OWN", brain.getId()); when(secrets.findById("OWN")).thenReturn(Optional.of(own));
        service.rotate(brain.getId(), "OWN", new DeploymentSecretContracts.Rotate("replacement-value"));
        assertThat(own.getSecretValue()).isEqualTo("replacement-value");
        assertThat(own.getDeploymentId()).isEqualTo(brain.getId());
        verifyNoInteractions(overrides); verify(bindings).countBySecretName("OWN");
    }

    @Test void foreignGlobalTargetBoundAndManagedValuesCannotBeRotatedOrDeleted() {
        var foreign = key("FOREIGN", "dep-other"); var global = key("GLOBAL", null); global.setScopeType(PlatformSecretScopeType.GLOBAL_PLATFORM);
        var target = key("TARGET", brain.getId()); target.setTargetProfileId("target-prod");
        var managed = key("MANAGED", brain.getId()); managed.setManagedByPlatform(true);
        for (var entity : List.of(foreign, global, target, managed)) {
            when(secrets.findById(entity.getName())).thenReturn(Optional.of(entity));
            assertStatus(HttpStatus.NOT_FOUND, () -> service.rotate(brain.getId(), entity.getName(), new DeploymentSecretContracts.Rotate("replacement")));
            assertStatus(HttpStatus.NOT_FOUND, () -> service.delete(brain.getId(), entity.getName()));
        }
        verify(secrets, never()).save(any()); verify(secrets, never()).delete(any()); verifyNoInteractions(audit);
    }

    @Test void boundProviderKeyCannotBeDeletedButUnusedKeyCan() {
        var own = key("OWN", brain.getId()); when(secrets.findById("OWN")).thenReturn(Optional.of(own));
        when(bindings.countBySecretName("OWN")).thenReturn(1L);
        assertStatus(HttpStatus.CONFLICT, () -> service.delete(brain.getId(), "OWN"));
        verify(secrets, never()).delete(any());
        when(bindings.countBySecretName("OWN")).thenReturn(0L); service.delete(brain.getId(), "OWN"); verify(secrets).delete(own);
    }

    @Test void ownerDenialAndUnauthenticatedAccessStopBeforeReadingOrWritingSecrets() {
        when(access.requireDeploymentAdminAccess(brain)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.list(brain.getId()));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.create(brain.getId(), create("API", "OPENAI_API_KEY", "test-only", null)));
        SecurityContextHolder.clearContext();
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.list(brain.getId()));
        verifyNoInteractions(secrets, overrides, audit);
    }

    @Test void staffMustHaveCurrentCustomerDelegationEvenWithLegacyDeploymentAdminAssignment() {
        signIn(PlatformRole.PLATFORM_OPERATOR); service.list(brain.getId());
        verify(customers).requireStaffCustomerAccess("customer-one");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(customers).requireStaffCustomerAccess("customer-one");
        assertStatus(HttpStatus.FORBIDDEN, () -> service.create(brain.getId(), create("API", "OPENAI_API_KEY", "test-only", null)));
        verify(secrets, never()).save(any());
    }

    private static DeploymentSecretContracts.Create create(String name, String purpose, String value, String secondary) { return new DeploymentSecretContracts.Create(name, purpose, value, secondary); }
    private static PlatformSecretEntity key(String name, String id) {
        var key = new PlatformSecretEntity(); key.setName(name); key.setDeploymentId(id); key.setSecretPurpose(CustomerDeploymentSecretService.INTEGRATION);
        key.setSecretValue("test-only-private-value"); key.setScopeType(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE);
        key.setOwnerType(PlatformSecretOwnerType.DEPLOYMENT); key.setCleanupPolicy(PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE); key.setUpdatedAt(Instant.now()); return key;
    }
    private static void signIn(PlatformRole role) {
        var principal = new PlatformPrincipal("user-one", role, "Customer", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }
    private static void assertBad(Runnable action) { assertStatus(HttpStatus.BAD_REQUEST, action); }
    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }
}
