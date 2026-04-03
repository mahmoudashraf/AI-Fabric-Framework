package com.ai.fabric.platform.backend.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerAdminAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerAdminSessionAndCustomerManagementStayScoped() throws Exception {
        String customerA = createCustomer("Scoped Customer A");
        String customerB = createCustomer("Scoped Customer B");
        String tenantB = createTenant(customerB, "Scoped Tenant B");
        createCustomerAdminUser("customer-admin-a@example.com", "Scoped Customer Admin A", customerA);

        Cookie sessionCookie = login("customer-admin-a@example.com", "CustomerAdmin123!");

        mockMvc.perform(get("/api/platform/auth/session").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.role", is("CUSTOMER_ADMIN")))
            .andExpect(jsonPath("$.canManageUsers", is(false)))
            .andExpect(jsonPath("$.canManageUserDirectory", is(true)))
            .andExpect(jsonPath("$.canManageCustomers", is(true)))
            .andExpect(jsonPath("$.canCreateCustomers", is(false)))
            .andExpect(jsonPath("$.customerId", is(customerA)));

        mockMvc.perform(get("/api/platform/customers").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(customerA)));

        mockMvc.perform(put("/api/platform/customers/{customerId}", customerA)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Scoped Customer A Updated",
                      "description": "Customer admin updated their own customer."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Scoped Customer A Updated")));

        mockMvc.perform(post("/api/platform/customers/{customerId}/tenants", customerA)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Scoped Tenant A",
                      "description": "Created by customer admin"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId", is(customerA)));

        mockMvc.perform(post("/api/platform/customers").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Illegal Customer",
                      "description": "Customer admin should not create customers."
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/platform/customers/tenants/{tenantId}", tenantB)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Wrong Tenant",
                      "description": "Should not be editable."
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerAdminDeploymentVisibilityAndCreationStayInsideCustomerBoundary() throws Exception {
        String customerA = createCustomer("Deployment Customer A");
        String tenantA = createTenant(customerA, "Deployment Tenant A");
        String customerB = createCustomer("Deployment Customer B");
        String tenantB = createTenant(customerB, "Deployment Tenant B");
        createCustomerAdminUser("customer-admin-deploy@example.com", "Scoped Deployment Admin", customerA);

        mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Foreign Customer Deployment",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene",
                      "customerId": "%s",
                      "tenantId": "%s"
                    }
                    """.formatted(customerB, tenantB)))
            .andExpect(status().isCreated());

        Cookie sessionCookie = login("customer-admin-deploy@example.com", "CustomerAdmin123!");

        mockMvc.perform(post("/api/deployments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Scoped Auto Tenant Deployment",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.binding.customerId", is(customerA)));

        mockMvc.perform(post("/api/deployments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Scoped Existing Tenant Deployment",
                      "environment": "stage",
                      "templateId": "dev-openai-lucene",
                      "customerId": "%s",
                      "tenantId": "%s"
                    }
                    """.formatted(customerA, tenantA)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.binding.customerId", is(customerA)))
            .andExpect(jsonPath("$.binding.tenantId", is(tenantA)));

        mockMvc.perform(post("/api/deployments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Illegal Foreign Deployment",
                      "environment": "prod",
                      "templateId": "dev-openai-lucene",
                      "customerId": "%s"
                    }
                    """.formatted(customerB)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].binding.customerId", everyItem(is(customerA))));
    }

    @Test
    void customerAdminUserDirectoryStaysInsideCustomerBoundary() throws Exception {
        String customerA = createCustomer("User Customer A");
        String customerB = createCustomer("User Customer B");
        String scopedUserEmail = "customer-admin-users@example.com";
        createCustomerAdminUser(scopedUserEmail, "Scoped User Admin", customerA);
        createCustomerAdminUser("foreign-customer-admin@example.com", "Foreign User Admin", customerB);

        Cookie sessionCookie = login(scopedUserEmail, "CustomerAdmin123!");

        mockMvc.perform(get("/api/platform/users").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].customerId", is(customerA)))
            .andExpect(jsonPath("$[0].role", is("CUSTOMER_ADMIN")));

        mockMvc.perform(post("/api/platform/users")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "customer-admin-created@example.com",
                      "displayName": "Created By Customer Admin",
                      "password": "CustomerAdmin123!",
                      "role": "CUSTOMER_ADMIN"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role", is("CUSTOMER_ADMIN")))
            .andExpect(jsonPath("$.customerId", is(customerA)));

        mockMvc.perform(post("/api/platform/users")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "illegal-platform-operator@example.com",
                      "displayName": "Illegal Platform Operator",
                      "password": "CustomerAdmin123!",
                      "role": "PLATFORM_OPERATOR"
                    }
                    """))
            .andExpect(status().isBadRequest());

        String foreignUserId = createCustomerAdminUserReturningId(
            "foreign-edit-block@example.com",
            "Foreign Edit Block",
            customerB
        );

        mockMvc.perform(put("/api/platform/users/{userId}", foreignUserId)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "Should Not Work",
                      "role": "CUSTOMER_ADMIN",
                      "status": "ACTIVE",
                      "customerId": "%s"
                    }
                    """.formatted(customerB)))
            .andExpect(status().isNotFound());
    }

    private String createCustomer(String name) throws Exception {
        var result = mockMvc.perform(post("/api/platform/customers")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "description": "Created for customer-admin integration coverage."
                    }
                    """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTenant(String customerId, String name) throws Exception {
        var result = mockMvc.perform(post("/api/platform/customers/{customerId}/tenants", customerId)
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "description": "Created for customer-admin integration coverage."
                    }
                    """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createCustomerAdminUser(String email, String displayName, String customerId) throws Exception {
        createCustomerAdminUserReturningId(email, displayName, customerId);
    }

    private String createCustomerAdminUserReturningId(String email, String displayName, String customerId) throws Exception {
        var result = mockMvc.perform(post("/api/platform/users")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "displayName": "%s",
                      "password": "CustomerAdmin123!",
                      "role": "CUSTOMER_ADMIN",
                      "customerId": "%s"
                    }
                    """.formatted(email, displayName, customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role", is("CUSTOMER_ADMIN")))
            .andExpect(jsonPath("$.customerId", is(customerId)))
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private Cookie login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
        String cookieHeader = result.getResponse().getHeader("Set-Cookie");
        String sessionValue = cookieHeader
            .substring(0, cookieHeader.indexOf(';'))
            .replace("platform_session=", "");
        return new Cookie("platform_session", sessionValue);
    }
}
