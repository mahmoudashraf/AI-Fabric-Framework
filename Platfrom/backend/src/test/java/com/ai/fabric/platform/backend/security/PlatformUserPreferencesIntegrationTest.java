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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class PlatformUserPreferencesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preferencesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/platform/preferences"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanReadAndPersistPreferences() throws Exception {
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        mockMvc.perform(get("/api/platform/preferences")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentListView.showArchived", is(false)))
            .andExpect(jsonPath("$.deploymentListView.searchTerm", is("")))
            .andExpect(jsonPath("$.deploymentListView.healthFilter", is("ALL")))
            .andExpect(jsonPath("$.deploymentListView.roleFilter", is("ALL")))
            .andExpect(jsonPath("$.deploymentListView.templateFilter", is("ALL")))
            .andExpect(jsonPath("$.deploymentWorkspace.lastDeploymentId", nullValue()));

        mockMvc.perform(put("/api/platform/preferences")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deploymentListView": {
                        "showArchived": true,
                        "searchTerm": "priority",
                        "healthFilter": "HEALTHY",
                        "roleFilter": "DEPLOYMENT_OPERATOR",
                        "templateFilter": "dev-openai-lucene"
                      },
                      "deploymentWorkspace": {
                        "lastDeploymentId": "dep-preference",
                        "lastSection": "/overview"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentListView.showArchived", is(true)))
            .andExpect(jsonPath("$.deploymentListView.searchTerm", is("priority")))
            .andExpect(jsonPath("$.deploymentListView.healthFilter", is("HEALTHY")))
            .andExpect(jsonPath("$.deploymentListView.roleFilter", is("DEPLOYMENT_OPERATOR")))
            .andExpect(jsonPath("$.deploymentListView.templateFilter", is("dev-openai-lucene")))
            .andExpect(jsonPath("$.deploymentWorkspace.lastDeploymentId", is("dep-preference")))
            .andExpect(jsonPath("$.deploymentWorkspace.lastSection", is("/overview")));

        mockMvc.perform(get("/api/platform/preferences")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentListView.showArchived", is(true)))
            .andExpect(jsonPath("$.deploymentListView.searchTerm", is("priority")))
            .andExpect(jsonPath("$.deploymentWorkspace.lastDeploymentId", is("dep-preference")));
    }

    private Cookie login(String email, String password) throws Exception {
        String cookieHeader = mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        String sessionValue = cookieHeader
            .substring(0, cookieHeader.indexOf(';'))
            .replace("platform_session=", "");
        return new Cookie("platform_session", sessionValue);
    }
}
