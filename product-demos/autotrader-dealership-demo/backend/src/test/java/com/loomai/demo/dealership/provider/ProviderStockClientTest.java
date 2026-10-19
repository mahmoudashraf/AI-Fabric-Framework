package com.loomai.demo.dealership.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ProviderStockClientTest {
    private static final String STOCK_URL = "https://provider.example/stock?advertiserId=advertiser-1&stockId=PROVIDER-ONLY-99&page=1&pageSize=2";

    @Test
    void authenticatesWithServerCredentialsAndValidatesOnlyTheExactScopedStock() {
        var fixture = fixture();
        authenticate(fixture.server());
        fixture.server().expect(requestTo(STOCK_URL)).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer provider-token"))
            .andRespond(withSuccess(stockResponse().toString(), MediaType.APPLICATION_JSON));
        var stock = fixture.client().requireAvailableStock("PROVIDER-ONLY-99");
        assertThat(stock.stockId()).isEqualTo("PROVIDER-ONLY-99");
        assertThat(stock.advertiserId()).isEqualTo("advertiser-1");
        assertThat(stock.displayName()).isEqualTo("2025 Meridian E7");
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-stock", "wrong-advertiser", "duplicate", "missing-count", "string-count", "missing-results", "missing-date"})
    void rejectsUnverifiableOrAmbiguousProviderResponses(String variant) {
        var fixture = fixture();
        authenticate(fixture.server());
        ObjectNode body = stockResponse();
        ObjectNode record = (ObjectNode) body.path("results").get(0);
        switch (variant) {
            case "wrong-stock" -> ((ObjectNode) record.path("metadata")).put("stockId", "other-stock");
            case "wrong-advertiser" -> ((ObjectNode) record.path("advertiser")).put("advertiserId", "other-advertiser");
            case "duplicate" -> body.put("totalResults", 2);
            case "missing-count" -> body.remove("totalResults");
            case "string-count" -> body.put("totalResults", "1");
            case "missing-results" -> body.remove("results");
            case "missing-date" -> ((ObjectNode) record.path("metadata")).remove("lastUpdated");
            default -> throw new AssertionError(variant);
        }
        fixture.server().expect(requestTo(STOCK_URL)).andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> fixture.client().requireAvailableStock("PROVIDER-ONLY-99"))
            .hasMessageContaining("verifiable scoped stock record");
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "sold", "deleted", "unpublished", "reserved"})
    void missingOrUnavailableProviderStockCannotBeUsedForANewLead(String variant) {
        var fixture = fixture();
        authenticate(fixture.server());
        ObjectNode body = stockResponse();
        ObjectNode record = (ObjectNode) body.path("results").get(0);
        switch (variant) {
            case "missing" -> { body.putArray("results"); body.put("totalResults", 0); }
            case "sold" -> ((ObjectNode) record.path("metadata")).put("lifecycleState", "SOLD");
            case "deleted" -> ((ObjectNode) record.path("metadata")).put("lifecycleState", "DELETED");
            case "unpublished" -> ((ObjectNode) record.path("adverts").path("retailAdverts").path("advertiserAdvert")).put("status", "NOT_PUBLISHED");
            case "reserved" -> ((ObjectNode) record.path("adverts")).put("reservationStatus", "Reserved");
            default -> throw new AssertionError(variant);
        }
        fixture.server().expect(requestTo(STOCK_URL)).andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> fixture.client().requireAvailableStock("PROVIDER-ONLY-99"))
            .hasMessageContaining("not currently available");
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {206, 302, 401, 403, 429, 503})
    void unsuccessfulOrPartialReadsFailClosedWithoutASecondarySource(int status) {
        var fixture = fixture();
        authenticate(fixture.server());
        fixture.server().expect(requestTo(STOCK_URL)).andRespond(withStatus(HttpStatus.valueOf(status))
            .contentType(MediaType.APPLICATION_JSON).body(stockResponse().toString()));
        assertThatThrownBy(() -> fixture.client().requireAvailableStock("PROVIDER-ONLY-99"))
            .hasMessageContaining("validation is unavailable");
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Meridian E7", "https://other.example/stock", "../PROVIDER-ONLY-99", "dealer-vehicle:PROVIDER-ONLY-99", ""})
    void doesNotSemanticallyResolveVehicleNamesOrAcceptCallerSelectedEndpoints(String reference) {
        var fixture = fixture();
        assertThatThrownBy(() -> fixture.client().requireAvailableStock(reference))
            .hasMessageContaining("canonical provider stock ID");
        fixture.server().verify();
    }

    @Test
    void anUnconfiguredProviderCannotUseSimulatorControlsOrWebsiteInventoryAsFallback() {
        var client = new ProviderStockClient(RestClient.create(), "", "", "", "");
        assertThatThrownBy(() -> client.requireAvailableStock("PROVIDER-ONLY-99"))
            .hasMessageContaining("validation is unavailable");
    }

    @Test
    void authenticationFailureNeverSendsAStockRequestOrExposesProviderSecrets() {
        var fixture = fixture();
        fixture.server().expect(requestTo("https://provider.example/authenticate"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider-secret"));
        assertThatThrownBy(() -> fixture.client().requireAvailableStock("PROVIDER-ONLY-99"))
            .hasMessageNotContaining("provider-secret");
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"string", "number"})
    void preservesTheYearFromBothSupportedJsonTypes(String type) {
        var fixture = fixture();
        authenticate(fixture.server());
        ObjectNode body = stockResponse();
        ObjectNode vehicle = (ObjectNode) body.path("results").get(0).path("vehicle");
        if ("number".equals(type)) vehicle.put("yearOfManufacture", 2025);
        fixture.server().expect(requestTo(STOCK_URL)).andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
        assertThat(fixture.client().requireAvailableStock("PROVIDER-ONLY-99").displayName()).isEqualTo("2025 Meridian E7");
        fixture.server().verify();
    }

    private void authenticate(MockRestServiceServer server) {
        server.expect(requestTo("https://provider.example/authenticate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string("key=provider-key&secret=provider-secret"))
            .andRespond(withSuccess("{\"access_token\":\"provider-token\",\"expires_at\":\""
                + Instant.now().plusSeconds(3600) + "\"}", MediaType.APPLICATION_JSON));
    }

    private ObjectNode stockResponse() {
        var mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode().put("totalResults", 1);
        ObjectNode record = body.putArray("results").addObject();
        record.putObject("advertiser").put("advertiserId", "advertiser-1");
        record.putObject("metadata").put("stockId", "PROVIDER-ONLY-99").put("lifecycleState", "FORECOURT")
            .put("lastUpdated", Instant.now().minusSeconds(60).toString());
        record.putObject("vehicle").put("yearOfManufacture", "2025").put("make", "Meridian").put("model", "E7");
        record.putObject("adverts").putNull("reservationStatus").putObject("retailAdverts")
            .putObject("advertiserAdvert").put("status", "PUBLISHED");
        return body;
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new ProviderStockClient(builder.build(), "https://provider.example", "provider-key", "provider-secret", "advertiser-1"), server);
    }

    private record Fixture(ProviderStockClient client, MockRestServiceServer server) { }
}
