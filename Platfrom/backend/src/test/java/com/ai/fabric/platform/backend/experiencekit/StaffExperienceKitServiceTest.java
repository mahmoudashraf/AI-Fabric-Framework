package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitTestFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class StaffExperienceKitServiceTest {
    private ExperienceKitTestFixture f;
    private PlatformPrincipal staff;

    @BeforeEach void setup() {
        f = new ExperienceKitTestFixture();
        staff = new PlatformPrincipal("engineer@example.test", PlatformRole.PLATFORM_ADMIN, "Engineer", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(staff, null,
            List.of(new SimpleGrantedAuthority(staff.role().authority()))));
        when(f.catalogue.offerForCustomer(CUSTOMER, OFFER)).thenReturn(
            f.offer(OFFER, "template-version", f.profile, TARGET, TargetIntent.CUSTOMER_STAGING));
        when(f.bootstrap.bootstrapForCustomer(eq(CUSTOMER), eq(OFFER), any())).thenReturn(
            new CustomerDeploymentBootstrapContracts.Result("kit-deployment", "kit-tenant", OFFER,
                "offer-fingerprint", TARGET, 7, "CONFIGURATION_ONLY", "CUSTOMER_POSTGRES"));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void staffCreatesCanonicalCustomerOwnedKitWithoutImpersonatingCustomer() {
        var result = f.service.createForStaff(CUSTOMER, f.request("both", true));

        assertThat(f.saved.getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(result.workspace().customerId()).isEqualTo(CUSTOMER);
        assertThat(result.embeddedWorkspace().customerId()).isEqualTo(CUSTOMER);
        assertThat(result.workspace().status()).isEqualTo("DRAFT");
        assertThat(PlatformSecurityContext.currentPrincipal()).isSameAs(staff);
        verify(f.access).requireStaffCustomerAccess(CUSTOMER);
        verify(f.access).requireActiveCustomer(CUSTOMER);
        verify(f.access, never()).requireCustomerActor();
        verify(f.catalogue).offerForCustomer(CUSTOMER, OFFER);
        verify(f.bootstrap).bootstrapForCustomer(eq(CUSTOMER), eq(OFFER), any());
        verify(f.staging).configureForCustomer(eq(CUSTOMER), eq(OFFER), eq("kit-deployment"), any());
        verify(f.workspaces, times(2)).create(eq(CUSTOMER), any(), anyString());
        verify(f.audit).record(eq("CUSTOMER_EXPERIENCE_KIT_CREATED"), eq("EXPERIENCE_KIT"), eq(result.id()),
            argThat(value -> CUSTOMER.equals(value.get("customerId")) && Boolean.TRUE.equals(value.get("assisted"))));
        verify(f.bootstrap, never()).bootstrap(anyString(), any());
        verify(f.staging, never()).configure(anyString(), anyString(), any());
        verify(f.workspaces, never()).activate(anyString(), anyString());
    }

    @Test void identicalStaffReplayDoesNotCreateSecondStack() {
        var request = f.request("standalone", false);
        var first = f.service.createForStaff(CUSTOMER, request);
        var replay = f.service.createForStaff(CUSTOMER, request);

        assertThat(replay).isEqualTo(first);
        assertThat(f.saved.getRequestFingerprint()).isEqualTo(f.codec.hash(Map.of(
            "request", request, "ownerUserId", "staff:" + staff.actorId())));
        verify(f.bootstrap, times(1)).bootstrapForCustomer(eq(CUSTOMER), eq(OFFER), any());
        verify(f.consumers, times(1)).createConsumer(eq(CUSTOMER), any());
    }

    @Test void differentStaffCannotReplayAnotherActorsCreateOperation() {
        var request = f.request("standalone", false);
        f.service.createForStaff(CUSTOMER, request);
        var other = new PlatformPrincipal("other@example.test", PlatformRole.PLATFORM_ADMIN, "Other", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other, null,
            List.of(new SimpleGrantedAuthority(other.role().authority()))));

        assertThatThrownBy(() -> f.service.createForStaff(CUSTOMER, request))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(CONFLICT));
        verify(f.bootstrap, times(1)).bootstrapForCustomer(eq(CUSTOMER), eq(OFFER), any());
    }

    @Test void staffAccessIsRequiredBeforeAnyResourcesAreReadOrCreated() {
        doThrow(new ResponseStatusException(FORBIDDEN)).when(f.access).requireStaffCustomerAccess(CUSTOMER);

        assertThatThrownBy(() -> f.service.createForStaff(CUSTOMER, f.request("standalone", false)))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(f.entityManager, f.bootstrap, f.consumers, f.staging, f.workspaces, f.kits);
    }

    @Test void staffGetStillUsesSelectedCustomerOwnershipNotUnscopedId() {
        when(f.kits.findByCustomerIdAndId(CUSTOMER, "foreign-kit")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> f.service.getForStaff(CUSTOMER, "foreign-kit"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(NOT_FOUND));
        verify(f.kits).findByCustomerIdAndId(CUSTOMER, "foreign-kit");
        verify(f.kits, never()).findById(anyString());
        verifyNoInteractions(f.installations, f.workspaces);
    }

    @Test void customerCanSeeStaffCreatedKitUsingOwnExistingLoginScope() {
        var created = f.service.createForStaff(CUSTOMER, f.request("standalone", false));
        when(f.kits.findByCustomerIdAndId(CUSTOMER, created.id())).thenReturn(Optional.of(f.saved));
        when(f.kits.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER)).thenReturn(List.of(f.saved));

        assertThat(f.service.get(created.id())).isEqualTo(created);
        assertThat(f.service.list()).containsExactly(created);
        verify(f.access, times(2)).requireCustomerActor();
    }
}
