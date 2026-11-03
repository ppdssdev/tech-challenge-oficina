package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.adapters.in.web.CustomerWorkOrderController;
import br.com.fiap.techchallenge.oficina.adapters.in.web.PublicWorkOrderController;
import br.com.fiap.techchallenge.oficina.adapters.out.security.*;
import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.configuration.CustomerSecurityConfig;
import br.com.fiap.techchallenge.oficina.configuration.SecurityConfig;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static br.com.fiap.techchallenge.oficina.support.CustomerJwtFixtures.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real filter chains, signatures and method authorization; only persistence/JWKS IO are mocked. */
@WebMvcTest({PublicWorkOrderController.class, CustomerWorkOrderController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, CustomerSecurityConfig.class, JwtAuthenticationFilter.class,
    JwtTokenSecurityAdapter.class, CustomerJwtVerifier.class, WorkOrderAuthorization.class,
    ReconciledSecurityTest.Transactions.class})
class ReconciledSecurityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    io.micrometer.tracing.Tracer tracer;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenSecurityAdapter hmac;
    @MockitoBean UserDetailsService users;
    @MockitoBean GetWorkOrderUseCase orders;
    @MockitoBean ExternalBudgetDecisionUseCase budgets;
    @MockitoBean WorkOrderRepositoryPort repository;
    @MockitoBean JwtDecoder customerDecoder;
    final UUID owner = UUID.randomUUID();

    @TestConfiguration
    static class Transactions {
        @Bean TransactionPort transactions() { return TransactionPort.direct(); }
    }

    @DynamicPropertySource
    static void pemTrust(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.customer.enabled", () -> true);
        registry.add("app.jwt.customer.issuer", () -> ISSUER);
        registry.add("app.jwt.customer.audience", () -> AUDIENCE);
        registry.add("app.jwt.customer.public-keys.current", () -> CURRENT_FILE);
    }

    @BeforeEach
    void setup() {
        var customer = mock(Customer.class);
        when(customer.getId()).thenReturn(owner);
        var order = mock(WorkOrder.class);
        when(order.getCustomer()).thenReturn(customer);
        when(repository.findByCode("OS-TEST")).thenReturn(Optional.of(order));
        when(orders.getPublicStatus("OS-TEST", "52998224725"))
            .thenThrow(new NotFoundException("Synthetic application response"));
        when(users.loadUserByUsername("admin"))
            .thenReturn(User.withUsername("admin").password("unused").roles("ADMIN").build());
        when(customerDecoder.decode(anyString())).thenThrow(new BadJwtException("Untrusted JWKS token"));
    }

    @Test
    void postRequiresAuthenticatedOwnerAndKeepsDocumentInBody() throws Exception {
        String path = "/api/v1/public/work-orders/OS-TEST/status";
        mvc.perform(post(path).contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer " + token(UUID.randomUUID().toString()))
            .contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(orders);
        mvc.perform(post(path).header("Authorization", "Bearer " + token(owner.toString()))
            .contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isNotFound());
        verify(orders).getPublicStatus("OS-TEST", "52998224725");
        mvc.perform(get(path).param("document", "52998224725")
            .header("Authorization", "Bearer " + token(owner.toString())))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(users, customerDecoder);
    }

    @Test
    void adminHmacStillReachesPublicOperationWithoutOwnershipLookup() throws Exception {
        String token = hmac.generateToken(users.loadUserByUsername("admin"));
        mvc.perform(post("/api/v1/public/work-orders/OS-TEST/status").header("Authorization", "Bearer " + token)
            .contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isNotFound());
        verify(orders).getPublicStatus("OS-TEST", "52998224725");
        verifyNoInteractions(repository, customerDecoder);
    }

    @Test
    void customerPemCannotReachAdminOrAuthenticateThroughJwksChain() throws Exception {
        String token = token(owner.toString());
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customer/work-orders/" + UUID.randomUUID())
            .header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verify(customerDecoder).decode(token);
        verifyNoInteractions(users, orders);
    }

    @Test
    void jwksIdentityRetainsTypedPrincipalAndCustomerOwnershipArgument() throws Exception {
        UUID id = UUID.randomUUID();
        String token = "trusted-jwks-token";
        doReturn(Jwt.withTokenValue(token).header("alg", "RS256")
            .subject(owner.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build())
            .when(customerDecoder).decode(token);
        when(orders.getForCustomer(id, owner)).thenThrow(new NotFoundException("Synthetic application response"));
        mvc.perform(get("/api/v1/customer/work-orders/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
        verify(orders).getForCustomer(id, owner);
        verifyNoInteractions(users, repository);
    }

    @Test
    void malformedBearerNeverReachesApplication() throws Exception {
        mvc.perform(post("/api/v1/public/work-orders/OS-TEST/status").header("Authorization", "Bearer invalid")
            .contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders, repository, users, customerDecoder);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"approve", "reject"})
    void budgetDecisionsRequireOwnerBeforeCallingUseCase(String operation) throws Exception {
        String path = "/api/v1/public/work-orders/OS-TEST/budget/" + operation;
        var command = new ExternalBudgetDecisionUseCase.Command("OS-TEST", "52998224725");
        if (operation.equals("approve")) {
            when(budgets.approve(command)).thenThrow(new NotFoundException("Synthetic application response"));
        } else {
            when(budgets.reject(command)).thenThrow(new NotFoundException("Synthetic application response"));
        }
        mvc.perform(post(path).header("Authorization", "Bearer " + token(UUID.randomUUID().toString()))
            .contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(budgets);
        mvc.perform(post(path).header("Authorization", "Bearer " + token(owner.toString()))
            .contentType("application/json").content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isNotFound());
        if (operation.equals("approve")) verify(budgets).approve(command);
        else verify(budgets).reject(command);
    }
}
