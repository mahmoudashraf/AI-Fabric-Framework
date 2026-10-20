import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Owned synthetic callback endpoint, never a proxy or connector. */
public class InboxLocalCallbackStub {
    public static void main(String[] args) throws Exception {
        String control = System.getenv("STUB_CONTROL_KEY");
        String signing = System.getenv("STUB_SIGNING_KEY");
        if (control == null || signing == null) throw new IllegalStateException("synthetic keys required");
        List<String> events = new ArrayList<>();
        int[] status = {200};
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8090), 16);
        server.createContext("/", exchange -> {
            synchronized (events) {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String result;
                    int response = 200;
                    if ("/callback".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                        byte[] body = exchange.getRequestBody().readNBytes(131073);
                        if (body.length > 131072) throw new IllegalArgumentException("bounded body required");
                        var headers = exchange.getRequestHeaders();
                        String timestamp = headers.getFirst("X-LoomAI-Provider-Timestamp");
                        String nonce = headers.getFirst("X-LoomAI-Provider-Nonce");
                        String signature = headers.getFirst("X-LoomAI-Provider-Signature");
                        Mac mac = Mac.getInstance("HmacSHA256");
                        mac.init(new SecretKeySpec(signing.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                        String expected = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + nonce + "."
                            + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
                        boolean valid = signature != null && nonce != null && nonce.matches("[a-f0-9-]{36}")
                            && Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) < 120
                            && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
                        response = valid ? status[0] : 401;
                        events.add("{\"payloadBase64\":\"" + Base64.getEncoder().encodeToString(body)
                            + "\",\"signatureValid\":" + valid + ",\"responseStatus\":" + response
                            + ",\"observedAt\":\"" + Instant.now() + "\"}");
                        result = "{\"controlVersion\":2}";
                    } else if (MessageDigest.isEqual(control.getBytes(StandardCharsets.UTF_8),
                            String.valueOf(exchange.getRequestHeaders().getFirst("X-Proof-Control")).getBytes(StandardCharsets.UTF_8))) {
                        if ("/events".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                            result = "[" + String.join(",", events) + "]";
                        } else if ("/mode".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                            String query = exchange.getRequestURI().getRawQuery();
                            if (!List.of("status=200", "status=400", "status=503").contains(query)) throw new IllegalArgumentException("fixed modes only");
                            status[0] = Integer.parseInt(query.substring(7));
                            result = "{\"status\":" + status[0] + "}";
                        } else { response = 404; result = "{}"; }
                    } else { response = 401; result = "{}"; }
                    byte[] data = result.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(response, data.length);
                    exchange.getResponseBody().write(data);
                } catch (Exception failure) {
                    exchange.sendResponseHeaders(500, -1);
                } finally { exchange.close(); }
            }
        });
        server.start();
    }
}

class InboxLocalHttpClient {
    public static void main(String[] args) throws Exception {
        var input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
        String method = input.readLine();
        var uri = java.net.URI.create(input.readLine());
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getUserInfo() != null
            || (uri.getPort() != 8085 && uri.getPort() != 8090)) throw new IllegalArgumentException("owned loopback only");
        var request = java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5));
        int count = Integer.parseInt(input.readLine());
        if (count < 0 || count > 8) throw new IllegalArgumentException("bounded headers only");
        for (int index = 0; index < count; index++) request.header(input.readLine(), input.readLine());
        byte[] body = Base64.getDecoder().decode(input.readLine());
        request.method(method, body.length == 0 ? java.net.http.HttpRequest.BodyPublishers.noBody()
            : java.net.http.HttpRequest.BodyPublishers.ofByteArray(body));
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var response = client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.print(response.body());
    }
}
