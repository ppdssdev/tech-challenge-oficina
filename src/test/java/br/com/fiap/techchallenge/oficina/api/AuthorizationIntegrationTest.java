package br.com.fiap.techchallenge.oficina.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthorizationIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RequestMappingHandlerMapping requestMappingHandlerMapping;
    @Value("${app.admin.default-username}") String adminUsername;
    @Value("${app.admin.default-password}") String adminPassword;

    @Test
    void allAdministrativeMappingsRequireAdmin() throws Exception {
        int checked = 0;
        for (var mapping : requestMappingHandlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : mapping.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/admin/")) continue;
                String path = pattern.replace("{id}", UUID.randomUUID().toString());
                for (var method : mapping.getMethodsCondition().getMethods()) {
                    mvc.perform(request(HttpMethod.valueOf(method.name()), path))
                        .andExpect(status().isUnauthorized());
                    mvc.perform(request(HttpMethod.valueOf(method.name()), path)
                            .with(user(UUID.randomUUID().toString()).roles("CUSTOMER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(30);
    }

    @Test
    void administrativeHmacLoginAndOperationsStillWork() throws Exception {
        String token = login(adminPassword).get("accessToken").asText();
        for (String path : new String[]{"customers", "vehicles", "services", "parts", "work-orders",
                "metrics/average-execution-time"}) {
            mvc.perform(get("/api/v1/admin/" + path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/admin/notifications/outbox/process")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", adminUsername, "password", UUID.randomUUID().toString()))))
            .andExpect(status().isUnauthorized());
        // Tamper with the signed payload while retaining the original signature.
        String[] parts = token.split("\\.");
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            json.writeValueAsBytes(Map.of("sub", adminUsername, "roles", new String[]{"ROLE_ADMIN"})));
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + String.join(".", parts)))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "budget/approve", "budget/reject"})
    void customerOperationsRequireAuthenticatedOwner(String operation) throws Exception {
        JsonNode order = createOrder();
        String owner = order.get("customerId").asText();
        String path = "/api/v1/public/work-orders/" + order.get("code").asText() + "/" + operation;
        HttpMethod method = HttpMethod.POST;
        var payload = json.writeValueAsString(Map.of("document", "52998224725", "customerId", owner,
            "username", owner, "role", "ADMIN"));
        mvc.perform(request(method, path).param("document", "52998224725")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(request(method, path).header("Authorization", "Bearer invalid")
                .param("document", "52998224725").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized());
        for (String principal : new String[]{UUID.randomUUID().toString(), "customer-without-uuid"}) {
            mvc.perform(request(method, path).with(user(principal).roles("CUSTOMER"))
                    .param("document", "52998224725").param("customerId", owner).param("role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        }
        mvc.perform(request(method, path).with(user(owner).roles("CUSTOMER"))
                .param("document", "52998224725").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("status").value(operation.equals("status") ? "WAITING_APPROVAL"
                : operation.equals("budget/approve") ? "IN_EXECUTION" : "BUDGET_REJECTED"));
    }

    @Test
    void customerTokensAreUnauthorizedWhenValidationIsDisabled() throws Exception {
        String token = br.com.fiap.techchallenge.oficina.support.CustomerJwtFixtures.token(UUID.randomUUID().toString());
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownResourceAndUnrecognizedAuthoritiesAreDenied() throws Exception {
        mvc.perform(post("/api/v1/public/work-orders/UNKNOWN/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"document\":\"52998224725\"}")
                .with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/customers").with(user("unknown").roles("OTHER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/unknown").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }

    private JsonNode login(String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", adminUsername, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode createOrder() throws Exception {
        String token = login(adminPassword).get("accessToken").asText();
        String service = mvc.perform(post("/api/v1/admin/services").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"Authorization test", "description":"Test", "basePrice":10,
                     "estimatedMinutes":10, "active":true}
                    """))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String body = """
            {"customer":{"fullName":"Maria Silva", "documentType":"CPF", "documentNumber":"52998224725",
              "email":"maria@example.com", "phone":"31999999999"},
             "vehicle":{"plate":"ABC1D23", "brand":"Fiat", "model":"Argo", "manufacturingYear":2020},
             "services":[{"serviceId":"%s", "quantity":1}], "parts":[], "diagnosticNotes":"Test"}
            """.formatted(json.readTree(service).get("id").asText());
        return json.readTree(mvc.perform(post("/api/v1/admin/work-orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
