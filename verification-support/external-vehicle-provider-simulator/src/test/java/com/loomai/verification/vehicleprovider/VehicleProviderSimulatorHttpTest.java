package com.loomai.verification.vehicleprovider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:vehicle-provider-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "simulator.fixture-version=contract-fixture-v1",
        "simulator.control-api-key=control-test-key-0123456789",
        "simulator.token-signing-secret=token-signing-test-secret-0123456789",
        "simulator.token-ttl-seconds=300",
        "simulator.allowed-webhook-hosts=127.0.0.1,localhost",
        "simulator.profile-a.account-id=account-alpha",
        "simulator.profile-a.key=profile-a-key",
        "simulator.profile-a.secret=profile-a-secret",
        "simulator.profile-a.webhook-secret=profile-a-webhook-secret",
        "simulator.profile-b.account-id=account-bravo",
        "simulator.profile-b.api-key=profile-b-api-key",
        "simulator.profile-b.webhook-secret=profile-b-webhook-secret"
    }
)
class VehicleProviderSimulatorHttpTest {

    private static final String CONTROL_KEY = "control-test-key-0123456789";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer webhookServer;

    @BeforeEach
    void resetFixture() {
        ResponseEntity<String> reset = exchange(
            HttpMethod.POST,
            "/internal/control/reset",
            controlEntity(null)
        );
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @AfterEach
    void stopWebhookServer() {
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
    }

    @Test
    void profileAUsesFormTokenPagePaginationAndQueryBoundAccount() throws Exception {
        String token = profileAToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> first = exchange(
            HttpMethod.GET,
            "/api/profile-a/vehicles?account=account-alpha&page=1&pageSize=2",
            new HttpEntity<>(headers)
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(first.getBody());
        assertThat(body.path("items").size()).isEqualTo(2);
        assertThat(body.path("page").asInt()).isEqualTo(1);
        assertThat(body.path("totalRecords").asInt()).isEqualTo(4);
        assertThat(body.path("items").get(0).path("accountId").asText()).isEqualTo("account-alpha");
        assertThat(first.getHeaders().getFirst("X-Simulator-Request")).startsWith("sim-");

        ResponseEntity<String> denied = exchange(
            HttpMethod.GET,
            "/api/profile-a/vehicles?account=account-other&page=1&pageSize=2",
            new HttpEntity<>(headers)
        );
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(denied.getBody()).path("code").asText())
            .isEqualTo("RESOURCE_ACCESS_DENIED");
    }

    @Test
    void profileBUsesApiKeyCursorPaginationAndPathBoundAccount() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Simulator-Api-Key", "profile-b-api-key");

        ResponseEntity<String> first = exchange(
            HttpMethod.GET,
            "/api/profile-b/accounts/account-bravo/vehicles?limit=2",
            new HttpEntity<>(headers)
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstBody = objectMapper.readTree(first.getBody());
        assertThat(firstBody.path("data").path("vehicles").size()).isEqualTo(2);
        String cursor = firstBody.path("paging").path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        ResponseEntity<String> second = exchange(
            HttpMethod.GET,
            "/api/profile-b/accounts/account-bravo/vehicles?limit=2&cursor=" + cursor,
            new HttpEntity<>(headers)
        );
        JsonNode secondBody = objectMapper.readTree(second.getBody());
        assertThat(secondBody.path("data").path("vehicles").size()).isEqualTo(2);
        String resumeCursor = secondBody.path("paging").path("nextCursor").asText();
        assertThat(resumeCursor).isNotBlank().isNotEqualTo(cursor);

        ResponseEntity<String> resumed = exchange(
            HttpMethod.GET,
            "/api/profile-b/accounts/account-bravo/vehicles?limit=2&cursor=" + resumeCursor,
            new HttpEntity<>(headers)
        );
        JsonNode resumedBody = objectMapper.readTree(resumed.getBody());
        assertThat(resumedBody.path("data").path("vehicles").size()).isZero();
        assertThat(resumedBody.path("paging").path("nextCursor").asText()).isEqualTo(resumeCursor);

        String changedVehicle = """
            {
              "id":"veh-b-2099",
              "make":"Northstar",
              "model":"Delta",
              "derivative":"Cursor verification",
              "year":2026,
              "priceMinor":3899000,
              "currency":"GBP",
              "fuelType":"electric",
              "bodyStyle":"hatchback",
              "transmission":"automatic",
              "mileage":20,
              "state":"active"
            }
            """;
        ResponseEntity<String> mutation = exchange(
            HttpMethod.PUT,
            "/internal/control/accounts/profile-b/account-bravo/vehicles/veh-b-2099",
            controlEntity(changedVehicle)
        );
        assertThat(mutation.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> delta = exchange(
            HttpMethod.GET,
            "/api/profile-b/accounts/account-bravo/vehicles?limit=2&cursor=" + resumeCursor,
            new HttpEntity<>(headers)
        );
        JsonNode deltaBody = objectMapper.readTree(delta.getBody());
        assertThat(deltaBody.path("data").path("vehicles").size()).isEqualTo(1);
        assertThat(deltaBody.path("data").path("vehicles").get(0).path("id").asText()).isEqualTo("veh-b-2099");
        assertThat(deltaBody.path("paging").path("nextCursor").asText()).isNotEqualTo(resumeCursor);

        ResponseEntity<String> denied = exchange(
            HttpMethod.GET,
            "/api/profile-b/accounts/account-other/vehicles?limit=2",
            new HttpEntity<>(headers)
        );
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void controlMutationFaultAndResetAreDeterministicAndProtected() throws Exception {
        ResponseEntity<String> anonymous = exchange(
            HttpMethod.GET,
            "/internal/control/status",
            HttpEntity.EMPTY
        );
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String payload = """
            {
              "id":"veh-a-1099",
              "make":"Northstar",
              "model":"Nova",
              "derivative":"Verification",
              "year":2026,
              "priceMinor":4999000,
              "currency":"GBP",
              "fuelType":"electric",
              "bodyStyle":"suv",
              "transmission":"automatic",
              "mileage":12,
              "state":"active"
            }
            """;
        ResponseEntity<String> upsert = exchange(
            HttpMethod.PUT,
            "/internal/control/accounts/profile-a/account-alpha/vehicles/veh-a-1099",
            controlEntity(payload)
        );
        assertThat(upsert.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(upsert.getBody()).path("sourceVersion").asLong()).isEqualTo(2);

        ResponseEntity<String> fault = exchange(
            HttpMethod.PUT,
            "/internal/control/accounts/profile-a/account-alpha/fault",
            controlEntity("{\"mode\":\"RATE_LIMITED\",\"remaining\":1,\"delayMs\":0}")
        );
        assertThat(fault.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpHeaders providerHeaders = new HttpHeaders();
        providerHeaders.setBearerAuth(profileAToken());
        ResponseEntity<String> limited = exchange(
            HttpMethod.GET,
            "/api/profile-a/vehicles?account=account-alpha&page=1&pageSize=10",
            new HttpEntity<>(providerHeaders)
        );
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        ResponseEntity<String> recovered = exchange(
            HttpMethod.GET,
            "/api/profile-a/vehicles?account=account-alpha&page=1&pageSize=10",
            new HttpEntity<>(providerHeaders)
        );
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(recovered.getBody()).path("items").size()).isEqualTo(5);

        ResponseEntity<String> reset = exchange(HttpMethod.POST, "/internal/control/reset", controlEntity(null));
        String resetId = objectMapper.readTree(reset.getBody()).path("resetId").asText();
        assertThat(resetId).isNotBlank();
        ResponseEntity<String> afterReset = exchange(
            HttpMethod.GET,
            "/api/profile-a/vehicles?account=account-alpha&page=1&pageSize=10",
            new HttpEntity<>(providerHeaders)
        );
        assertThat(objectMapper.readTree(afterReset.getBody()).path("items").size()).isEqualTo(4);
    }

    @Test
    void controlCanEmitValidAndDuplicateSignedEvents() throws Exception {
        LinkedBlockingQueue<CapturedRequest> captured = new LinkedBlockingQueue<>();
        webhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhookServer.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            captured.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestHeaders().getFirst("X-Simulator-A-Signature"),
                body
            ));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        webhookServer.start();

        int webhookPort = webhookServer.getAddress().getPort();
        String eventPayload = """
            {
              "variant":"DUPLICATE",
              "targetUrl":"http://127.0.0.1:%d/hook",
              "vehicleId":"veh-a-1001",
              "eventId":"evt-contract-1"
            }
            """.formatted(webhookPort);
        ResponseEntity<String> response = exchange(
            HttpMethod.POST,
            "/internal/control/accounts/profile-a/account-alpha/events",
            controlEntity(eventPayload)
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode delivery = objectMapper.readTree(response.getBody());
        assertThat(delivery.path("attempts").size()).isEqualTo(2);
        assertThat(delivery.path("attempts").get(0).path("status").asInt()).isEqualTo(202);

        CapturedRequest first = captured.poll(3, TimeUnit.SECONDS);
        CapturedRequest second = captured.poll(3, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.method()).isEqualTo("POST");
        assertThat(first.body()).isEqualTo(second.body());
        assertThat(signatureValid("profile-a-webhook-secret", first.signature(), first.body())).isTrue();
        JsonNode event = objectMapper.readTree(first.body());
        assertThat(event.path("accountId").asText()).isEqualTo("account-alpha");
        assertThat(event.path("eventType").asText()).isEqualTo("vehicle.changed");
    }

    private String profileAToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", "profile-a-key");
        form.add("secret", "profile-a-secret");
        ResponseEntity<String> response = exchange(
            HttpMethod.POST,
            "/api/profile-a/authenticate",
            new HttpEntity<>(form, headers)
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).path("access_token").asText();
    }

    private HttpEntity<String> controlEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Simulator-Control-Key", CONTROL_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, HttpEntity<?> entity) {
        return http.exchange("http://127.0.0.1:" + port + path, method, entity, String.class);
    }

    private static boolean signatureValid(String secret, String header, byte[] body) throws Exception {
        assertThat(header).startsWith("t=").contains(",v1=");
        String[] components = header.split(",");
        long timestamp = Long.parseLong(components[0].substring(2));
        String actual = components[1].substring(3);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        String expected = HexFormat.of().formatHex(mac.doFinal(body));
        return expected.equals(actual) && Math.abs(Instant.now().getEpochSecond() - timestamp) < 30;
    }

    private record CapturedRequest(String method, String signature, byte[] body) {
    }
}
