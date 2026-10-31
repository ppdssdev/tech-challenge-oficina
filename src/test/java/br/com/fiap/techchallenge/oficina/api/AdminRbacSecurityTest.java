package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.adapters.out.security.JwtAuthenticationFilter;
import br.com.fiap.techchallenge.oficina.adapters.in.web.MetricsController;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase.ExecutionTimeMetrics;
import br.com.fiap.techchallenge.oficina.configuration.SecurityConfig;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@WebMvcTest({MetricsController.class, br.com.fiap.techchallenge.oficina.adapters.in.web.CustomerController.class})
@Import(SecurityConfig.class)
class AdminRbacSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    CalculateWorkOrderMetricsUseCase metrics;

    @MockitoBean
    br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase customers;

    @BeforeEach
    void letRequestsContinueThroughTheMockedJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        when(metrics.averageExecutionTime(null, null)).thenReturn(new ExecutionTimeMetrics(0, null, null));
    }

    @Test
    void shouldAuthorizeCustomerStatusChangesOnlyForAdmins() throws Exception {
        var id = java.util.UUID.randomUUID();
        String path = "/api/v1/admin/customers/" + id + "/status";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
            .contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
            .with(user("customer").roles("CUSTOMER")).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isForbidden());
        when(customers.setActive(id, false)).thenReturn(new br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerResult(
            id, "Maria", br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType.CPF,
            "52998224725", null, null, null, false, null));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
            .with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.active").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
            .with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/average-execution-time"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenForAuthenticatedCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/average-execution-time").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdministratorToReachApplication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/average-execution-time").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }
}
