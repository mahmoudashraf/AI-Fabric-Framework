package com.ai.fabric.platform.backend.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.api-key-enabled=false",
    "platform.auth.session-enabled=true",
    "platform.auth.session-cookie-name=platform_session",
    "platform.auth.session-cookie-secure=false",
    "platform.auth.bootstrap-admin-enabled=true",
    "platform.auth.bootstrap-admin-email=admin@example.com",
    "platform.auth.bootstrap-admin-password=AdminPass123!",
    "platform.auth.bootstrap-admin-display-name=Platform Admin",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformSessionAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapAdminCanLoginAndUseSessionCookie() throws Exception {
        mockMvc.perform(get("/api/platform/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.authenticated", is(false)))
            .andExpect(jsonPath("$.sessionAuthEnabled", is(true)))
            .andExpect(jsonPath("$.apiKeyAuthEnabled", is(false)));

        var loginResult = mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin@example.com",
                      "password": "AdminPass123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("platform_session=")))
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.actorId", is("admin@example.com")))
            .andExpect(jsonPath("$.displayName", is("Platform Admin")))
            .andExpect(jsonPath("$.role", is("PLATFORM_ADMIN")))
            .andExpect(jsonPath("$.authenticationMode", is("SESSION")))
            .andReturn();

        String cookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        String sessionValue = cookieHeader
            .substring(0, cookieHeader.indexOf(';'))
            .replace("platform_session=", "");
        Cookie sessionCookie = new Cookie("platform_session", sessionValue);

        mockMvc.perform(get("/api/platform/overview")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentPhase", notNullValue()));

        mockMvc.perform(post("/api/platform/auth/logout")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(false)))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(get("/api/platform/overview")
                .cookie(sessionCookie))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin@example.com",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }
}
