package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class CustomerSupabaseUserVerifier {
    private final CustomerSupabaseAuthProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public CustomerSupabaseUserVerifier(CustomerSupabaseAuthProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    // Verification is provider-owned; user_metadata is user-editable and cannot prove a verified email.
    public void requireVerifiedUser(String accessToken, String expectedSubject) {
        verifiedUser(accessToken, expectedSubject);
    }

    public String requireVerifiedEmail(String accessToken, String expectedSubject) {
        JsonNode user = verifiedUser(accessToken, expectedSubject);
        JsonNode email = user.path("email");
        if (!email.isTextual() || email.asText().isBlank() || email.asText().length() > 255
            || !email.asText().contains("@") || !user.path("is_anonymous").isBoolean()) throw denied();
        return email.asText();
    }

    private JsonNode verifiedUser(String accessToken, String expectedSubject) {
        try {
            var request = HttpRequest.newBuilder(URI.create(properties.projectUrl().replaceAll("/+$", "") + "/auth/v1/user"))
                .timeout(Duration.ofSeconds(5)).header("apikey", properties.publicKey())
                .header("Authorization", "Bearer " + accessToken).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) throw denied();
            if (response.statusCode() != 200) throw unavailable();
            var user = mapper.readTree(response.body());
            if (user == null || expectedSubject == null || !expectedSubject.equals(user.path("id").asText()) || user.path("email_confirmed_at").asText("").isBlank()
                || user.path("is_anonymous").asBoolean()) throw denied();
            return user;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.io.IOException ex) {
            throw unavailable();
        }
    }

    private ResponseStatusException denied() { return new ResponseStatusException(UNAUTHORIZED, "Verified Supabase identity required."); }
    private ResponseStatusException unavailable() { return new ResponseStatusException(SERVICE_UNAVAILABLE, "Supabase identity verification is unavailable."); }
}
