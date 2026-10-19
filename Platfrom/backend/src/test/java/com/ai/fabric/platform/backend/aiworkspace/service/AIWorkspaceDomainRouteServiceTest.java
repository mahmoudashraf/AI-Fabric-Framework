package com.ai.fabric.platform.backend.aiworkspace.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AIWorkspaceDomainRouteServiceTest {
    @Test void preservesUnmanagedDomainsWithoutHostnameOwnershipHeuristics() {
        assertThat(AIWorkspaceDomainRouteService.merge(
            "https://shell.test,http://unmanaged.apps.test,https://old.customer.test",
            List.of("old.customer.test"), List.of(), List.of("new.apps.test")))
            .containsExactly("https://shell.test", "http://unmanaged.apps.test", "https://new.apps.test");
    }
    @Test void deletionRemovesInflightDomainEvenWithoutAnAppliedReceipt() {
        assertThat(AIWorkspaceDomainRouteService.merge("https://shell.test,https://deleted.apps.test",
            List.of(), List.of("deleted.apps.test"), List.of())).containsExactly("https://shell.test");
    }
    @Test void existingUnmanagedHostnameCannotBecomeOwnedFromDesiredRowsAlone() {
        assertThatThrownBy(() -> AIWorkspaceDomainRouteService.merge("https://customer.test",
            List.of(), List.of(), List.of("customer.test"))).hasMessageContaining("ownership collision");
    }
    @Test void refusesRemovingEveryFrontendRoute() {
        assertThatThrownBy(() -> AIWorkspaceDomainRouteService.merge("https://old.test",
            List.of("old.test"), List.of(), List.of())).hasMessageContaining("every");
    }
    @ParameterizedTest @ValueSource(strings = {"https://user:secret@host.test", "https://host.test/path",
        "https://host.test?token=secret", "https://host.test:8443", "ftp://host.test", "not a domain"})
    void malformedProviderDomainFailsClosed(String value) {
        assertThatThrownBy(() -> AIWorkspaceDomainRouteService.merge(value, List.of(), List.of(), List.of()))
            .isInstanceOf(RuntimeException.class);
    }
    @Test void fullUrlComparisonDoesNotIgnoreSchemeChanges() {
        assertThat(AIWorkspaceDomainRouteService.same("http://owned.test", List.of("https://owned.test"))).isFalse();
        assertThat(AIWorkspaceDomainRouteService.same("https://b.test,https://a.test",
            List.of("https://a.test", "https://b.test"))).isTrue();
    }
}
