package br.com.fiap.techchallenge.oficina.integration;

import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import br.com.fiap.techchallenge.oficina.application.port.out.*;
import br.com.fiap.techchallenge.oficina.domain.model.customer.*;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

class CustomerJwtIntegrationTest extends PostgresIntegrationTestSupport {
    static final HttpServer JWKS;
    static volatile String keys = "{\"keys\":[]}";
    static volatile int jwksStatus = 200;
    static {
        try {
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = keys.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(jwksStatus, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            JWKS.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void trust(DynamicPropertyRegistry r) {
        r.add("app.customer-auth.jwks-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        r.add("app.customer-auth.issuer", () -> "https://issuer.test");
        r.add("app.customer-auth.audience", () -> "oficina-api-test");
        r.add("app.customer-auth.cache-seconds", () -> 2);
    }
    @Autowired TestRestTemplate http;
    @Autowired CustomerRepositoryPort customers;
    @Autowired VehicleRepositoryPort vehicles;
    @Autowired WorkOrderRepositoryPort orders;
    @Autowired TransactionPort tx;
    @Autowired ObjectMapper mapper;
    JsonNode fixture;
    String path;
    @BeforeEach void seed() throws Exception {
        var order = tx.required(() -> {
            var customer = customers.save(new Customer("Maria", DocumentType.CPF, "52998224725", null, null));
            var vehicle = vehicles.save(new Vehicle(customer, "ABC1D23", "Fiat", "Uno", 2020));
            return orders.save(new WorkOrder("OS-" + UUID.randomUUID().toString().substring(0, 8), customer, vehicle, "private notes"));
        });
        path = "/api/v1/customer/work-orders/" + order.getId();
        String python = System.getenv().getOrDefault("CUSTOMER_TEST_PYTHON", "python3");
        var process = new ProcessBuilder(python, "src/test/python/tokens.py", order.getCustomer().getId().toString())
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        fixture = mapper.readTree(process.getInputStream());
        assertThat(process.waitFor()).isZero();
        keys = fixture.get("old").toString(); jwksStatus = 200;
    }
    String token(String name) { return fixture.get("tokens").get(name).asText(); }
    ResponseEntity<String> get(String url, String token) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    @Test void pythonIssuerAuthenticatesOwnerAndHidesOtherOrders() {
        var own = get(path, token("valid"));
        assertThat(own.getStatusCode().value()).isEqualTo(200);
        assertThat(own.getBody()).doesNotContain("Maria", "private notes", "52998224725", "ABC1D23");
        assertThat(get(path, token("other")).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/v1/customer/work-orders/" + UUID.randomUUID(), token("valid")).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/v1/admin/customers", token("valid")).getStatusCode().value()).isEqualTo(401);
    }
    @Test void rejectsInvalidTokens() {
        fixture.get("tokens").fields().forEachRemaining(entry -> {
            if (!Set.of("valid", "rotated", "other").contains(entry.getKey()))
                assertThat(get(path, entry.getValue().asText()).getStatusCode().value()).as(entry.getKey()).isEqualTo(401);
        });
        assertThat(http.getForEntity(path, String.class).getStatusCode().value()).isEqualTo(401);
    }
    @Test void adminLoginAndPublicEndpointsRemainAvailable() throws Exception {
        var login = http.postForEntity("/api/v1/auth/login", Map.of("username", "admin", "password", "admin123"), String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String admin = mapper.readTree(login.getBody()).get("accessToken").asText();
        assertThat(get("/api/v1/admin/customers", admin).getStatusCode().value()).isEqualTo(200);
        assertThat(get(path, admin).getStatusCode().value()).isEqualTo(401);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(200);
    }
    @Test void rotatesAndNeverUsesExpiredCacheDuringOutage() throws Exception {
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        keys = fixture.get("both").toString();
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        jwksStatus = 503;
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        Thread.sleep(2200);
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(401);
        jwksStatus = 200; keys = fixture.get("new").toString();
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(401);
    }
    @Test void emptyOrUnavailableJwksFailsClosed() {
        keys = "{\"keys\":[]}";
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(401);
        jwksStatus = 503;
        var unavailable = get(path, token("valid"));
        assertThat(unavailable.getStatusCode().value()).isEqualTo(401);
        assertThat(unavailable.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
    }
    @AfterAll static void close() { JWKS.stop(0); }
}
