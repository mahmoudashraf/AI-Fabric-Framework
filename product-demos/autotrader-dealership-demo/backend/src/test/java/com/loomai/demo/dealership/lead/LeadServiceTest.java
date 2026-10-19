package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeadServiceTest {
    private LeadService service;
    private LeadRepository repository;
    private ProviderStockClient provider;
    private PiiEncryptionService encryption;
    private DealershipDemoProperties properties;
    private LeadRepository.LeadRecord saved;

    @BeforeEach
    void setUp() {
        properties = new DealershipDemoProperties();
        var access = properties.getRuntime().getPrivateAccess();
        access.setDeploymentId("deployment");
        access.setTenantId("tenant");
        access.setCustomerId("customer");
        properties.getPrivacy().setEncryptionKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        encryption = new PiiEncryptionService(properties, new ObjectMapper());
        repository = mock(LeadRepository.class);
        provider = mock(ProviderStockClient.class);
        when(provider.configuredAdvertiserId()).thenReturn("advertiser-1");
        when(repository.findByIdempotencyKey(anyString())).thenAnswer(invocation -> Optional.ofNullable(saved));
        when(repository.insert(any())).thenAnswer(invocation -> { saved = invocation.getArgument(0); return saved; });
        when(provider.requireAvailableStock("PROVIDER-ONLY-99")).thenReturn(new ProviderStockClient.Stock(
            "PROVIDER-ONLY-99", "advertiser-1", "2025 Meridian E7", Instant.now().minusSeconds(60), Instant.now()));
        service = new LeadService(repository, provider, encryption, properties);
    }

    @Test
    void validatesProviderOnlyStockAndRetainsAnEncryptedReceiptWithoutWebsiteInventory() {
        var receipt = service.execute(request("session", true));
        assertThat(saved.vehicleId()).isEqualTo("PROVIDER-ONLY-99");
        assertThat(saved.providerStockId()).isEqualTo("PROVIDER-ONLY-99");
        assertThat(saved.providerAdvertiserId()).isEqualTo("advertiser-1");
        assertThat(saved.encryptedContact()).startsWith("v1.").doesNotContain("buyer@example.test");
        assertThat(receipt.vehicle()).isEqualTo("2025 Meridian E7");
        assertThat(receipt.message()).contains("dealership review inbox").doesNotContain("Auto Trader deal");
        verify(provider).requireAvailableStock("PROVIDER-ONLY-99");
    }

    @Test
    void aConfirmedReceiptReplaysWithoutRevalidatingOrRewritingTheProviderRecord() {
        var first = service.execute(request("session", true));
        reset(provider);
        when(provider.configuredAdvertiserId()).thenReturn("advertiser-1");
        when(provider.requireAvailableStock(anyString())).thenThrow(new IllegalStateException("Provider is offline"));
        assertThat(service.execute(request("session", true))).isEqualTo(first);
        verify(provider, never()).requireAvailableStock(anyString());
        verify(repository, times(1)).insert(any());
    }

    @Test
    void replayStillRequiresConfirmationAndTheOriginalSession() {
        service.execute(request("session", true));
        assertThatThrownBy(() -> service.execute(request("other-session", true))).hasMessageContaining("different confirmed request");
        assertThatThrownBy(() -> service.execute(request("session", false))).hasMessageContaining("Confirmed action evidence");
        verify(repository, times(1)).insert(any());
    }

    @Test
    void aReusedKeyCannotChangeTheTargetOrContactPayload() {
        service.execute(request("session", true));
        var original = request("session", true);
        assertThatThrownBy(() -> service.execute(new LeadService.ActionExecutionRequest(original.actionId(), original.idempotencyKey(), true,
            "OTHER-STOCK", original.name(), original.email(), original.phone(), null, null, false, original.authContext())))
            .hasMessageContaining("different confirmed request");
        assertThatThrownBy(() -> service.execute(new LeadService.ActionExecutionRequest(original.actionId(), original.idempotencyKey(), true,
            original.vehicleReference(), "Different Buyer", original.email(), original.phone(), null, null, false, original.authContext())))
            .hasMessageContaining("different confirmed request");
        verify(repository, times(1)).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"deployment", "tenant", "customer"})
    void foreignTrustedContextCannotCreateOrReplayAReceipt(String foreign) {
        service.execute(request("session", true));
        var auth = new LeadService.VerifiedAuthContext("subject", "ANONYMOUS_SESSION", "PUBLIC_RUNTIME_TOKEN", "PUBLIC_BROWSER",
            "session", foreign.equals("deployment") ? "other" : "deployment", foreign.equals("customer") ? "other" : "customer",
            foreign.equals("tenant") ? "other" : "tenant", "runtime", List.of());
        var request = request("session", true);
        assertThatThrownBy(() -> service.execute(new LeadService.ActionExecutionRequest(request.actionId(), request.idempotencyKey(), true,
            request.vehicleReference(), request.name(), request.email(), request.phone(), null, null, false, auth)))
            .hasMessageContaining("does not own this dealership boundary");
        verify(repository, times(1)).insert(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 502, 503})
    void aProviderFailureOrUnavailableVehicleNeverCreatesALead(int code) {
        when(provider.requireAvailableStock(anyString())).thenThrow(new ResponseStatusException(HttpStatus.valueOf(code), "Provider rejected validation"));
        assertThatThrownBy(() -> service.execute(request("session", true))).hasMessageContaining("Provider rejected validation");
        verify(repository, never()).insert(any());
    }

    @Test
    void staffViewsUseTheReceiptSnapshotWithoutCallingOrJoiningInventory() {
        service.execute(request("session", true));
        when(repository.latest(10)).thenReturn(List.of(saved));
        when(repository.findById(saved.id())).thenReturn(Optional.of(saved));
        clearInvocations(provider);
        assertThat(service.latest(10).getFirst().vehicle()).isEqualTo("2025 Meridian E7");
        assertThat(service.detail(saved.id()).contact()).containsEntry("name", "Test Buyer").doesNotContainKey("_providerStock");
        verifyNoInteractions(provider);
    }

    @Test
    void migratedStorePersistsAndReplaysProviderOnlyStockWithoutWebsiteInventory() {
        var data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:lead-boundary-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__dealership_demo_schema.sql"),
            new ClassPathResource("db/migration/V2__managed_dependency_lifecycle.sql"),
            new ClassPathResource("db/migration/V3__managed_dependency_current_operation.sql"),
            new ClassPathResource("db/migration/V4__external_provider_lead_identity.sql")).execute(data);
        var jdbc = new JdbcTemplate(data);
        var migrated = new LeadService(new LeadRepository(jdbc), provider, encryption, properties);
        var first = migrated.execute(request("session", true));
        assertThat(migrated.execute(request("session", true))).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT provider_stock_id, provider_advertiser_id FROM dealership_lead_request"))
            .containsEntry("provider_stock_id", "PROVIDER-ONLY-99").containsEntry("provider_advertiser_id", "advertiser-1");
    }

    @Test
    void receiptReplayCannotCrossTheConfiguredAdvertiserBoundary() {
        service.execute(request("session", true));
        when(provider.configuredAdvertiserId()).thenReturn("other-advertiser");
        assertThatThrownBy(() -> service.execute(request("session", true))).hasMessageContaining("different confirmed request");
        verify(repository, times(1)).insert(any());
        verify(provider, times(1)).requireAvailableStock(anyString());
    }

    @Test
    void storeFailuresAreSanitizedAndDoNotReturnSuccessfulReceipts() {
        when(repository.insert(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException(
            "SQL failure with buyer@example.test and provider-secret"));
        assertThatThrownBy(() -> service.execute(request("session", true)))
            .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(failure.getReason()).isEqualTo("The dealership lead store is temporarily unavailable.");
                assertThat(failure.getCause()).isNull();
                assertThat(failure.getMessage()).doesNotContain("buyer@example.test", "provider-secret", "SQL failure");
            });
    }

    @Test
    void insertRaceReturnsThePersistedWinnerWithoutUsingTheLosingProviderSnapshot() {
        var original = service.execute(request("session", true));
        var winner = saved;
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        doReturn(winner).when(repository).insert(any());
        when(provider.requireAvailableStock(anyString())).thenReturn(new ProviderStockClient.Stock(
            "PROVIDER-ONLY-99", "advertiser-1", "Different provider snapshot", Instant.now(), Instant.now()));

        assertThat(service.execute(request("session", true))).isEqualTo(original);
        assertThat(saved).isSameAs(winner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"action", "stock", "provider-stock", "advertiser", "session", "consent", "name", "email", "phone", "date", "message"})
    void recoveredInsertWinnerStillRequiresTheExactConfirmedPayloadAndScope(String changed) {
        service.execute(request("session", true));
        var original = request("session", true);
        var winner = saved;
        if (changed.equals("provider-stock") || changed.equals("advertiser")) {
            winner = new LeadRepository.LeadRecord(winner.id(), winner.idempotencyKey(), winner.actionType(), winner.vehicleId(),
                winner.encryptedContact(), winner.status(), winner.consentRecorded(), winner.sourceSessionId(), winner.receiptCode(),
                winner.createdAt(), winner.updatedAt(), changed.equals("provider-stock") ? "OTHER-STOCK" : winner.providerStockId(),
                changed.equals("advertiser") ? "other-advertiser" : winner.providerAdvertiserId());
        }
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        doReturn(winner).when(repository).insert(any());
        var input = new LeadService.ActionExecutionRequest(
            changed.equals("action") ? "dealership_request_callback" : original.actionId(), original.idempotencyKey(), true,
            changed.equals("stock") ? "OTHER-STOCK" : original.vehicleReference(),
            changed.equals("name") ? "Another Buyer" : original.name(), changed.equals("email") ? "other@example.test" : original.email(),
            changed.equals("phone") ? "+44 7700 900456" : original.phone(), changed.equals("date") ? "2026-11-01" : null,
            changed.equals("message") ? "Different confirmed instructions" : null,
            changed.equals("consent") || changed.equals("action"),
            changed.equals("session") ? request("other-session", true).authContext() : original.authContext());
        when(provider.requireAvailableStock(anyString())).thenAnswer(call -> new ProviderStockClient.Stock(
            call.getArgument(0), "advertiser-1", "Candidate snapshot", Instant.now(), Instant.now()));

        assertThatThrownBy(() -> service.execute(input)).isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
            assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(failure.getReason()).isEqualTo("The idempotency key belongs to a different confirmed request.");
            assertThat(failure.getMessage()).doesNotContain("buyer@example.test", "other@example.test");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"session", "email", "phone", "date", "message"})
    void oversizedValuesAreRejectedRatherThanTruncatedIntoAnExistingRequest(String field) {
        var original = request("session", true);
        var input = new LeadService.ActionExecutionRequest("dealership_request_callback", "oversized", true, original.vehicleReference(),
            original.name(), field.equals("email") ? "x".repeat(201) + "@example.test" : original.email(),
            field.equals("phone") ? "1".repeat(81) : original.phone(), field.equals("date") ? "x".repeat(41) : null,
            field.equals("message") ? "x".repeat(501) : null, true,
            field.equals("session") ? request("x".repeat(161), true).authContext() : original.authContext());

        assertThatThrownBy(() -> service.execute(input)).isInstanceOfSatisfying(ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(provider, repository);
    }

    private LeadService.ActionExecutionRequest request(String session, boolean confirmed) {
        var auth = new LeadService.VerifiedAuthContext("subject", "ANONYMOUS_SESSION", "PUBLIC_RUNTIME_TOKEN", "PUBLIC_BROWSER",
            session, "deployment", "customer", "tenant", "runtime", List.of());
        return new LeadService.ActionExecutionRequest("dealership_request_test_drive", "request-1", confirmed,
            "PROVIDER-ONLY-99", "Test Buyer", "buyer@example.test", "+44 7700 900123", null, null, false, auth);
    }
}
