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

@org.springframework.context.annotation.Import(CustomerJwtIntegrationTest.TimeConfiguration.class)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class CustomerJwtIntegrationTest extends PostgresIntegrationTestSupport {
    static final java.util.concurrent.atomic.AtomicLong TIME = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicInteger REQUESTS = new java.util.concurrent.atomic.AtomicInteger();
    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.github.benmanes.caffeine.cache.Ticker testJwksTicker() { return TIME::get; }
    }
    static void advance(long seconds) { TIME.addAndGet(java.time.Duration.ofSeconds(seconds).toNanos()); }
    static final HttpServer JWKS;
    static volatile String keys = "{\"keys\":[]}";
    static volatile int jwksStatus = 200;
    static {
        try {
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                REQUESTS.incrementAndGet();
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
        r.add("app.customer-auth.cache-seconds", () -> 30);
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
        advance(31); // Expire the preceding test snapshot and refresh cooldown.
        REQUESTS.set(0);
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
        advance(5);
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        jwksStatus = 503;
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        advance(30);
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(401);
        jwksStatus = 200; keys = fixture.get("new").toString();
        advance(5);
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(401);
    }
    @Test void emptyOrUnavailableJwksFailsClosed() {
        keys = "{\"keys\":[]}";
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(401);
        jwksStatus = 503;
        advance(5);
        var unavailable = get(path, token("valid"));
        assertThat(unavailable.getStatusCode().value()).isEqualTo(401);
        assertThat(unavailable.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
    }
    @Test void boundsRandomKidsAndRecognizesPublishedKeyAfterCooldown() throws Exception {
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        advance(5);
        for (int i = 0; i < 40; i++)
            assertThat(get(path, randomKidToken()).getStatusCode().value()).isEqualTo(401);
        assertThat(REQUESTS.get()).isEqualTo(2);
        keys = fixture.get("both").toString();
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(401);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        advance(5);
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        assertThat(REQUESTS.get()).isEqualTo(3);
    }

    @Test void concurrentRandomKidsShareOneRefreshAttempt() throws Exception {
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        advance(5);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 24; i++) {
                String jwt = randomKidToken();
                tasks.add(executor.submit(() -> {
                    assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    return get(path, jwt).getStatusCode().value();
                }));
            }
            start.countDown();
            for (var task : tasks) assertThat(task.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(401);
        }
        assertThat(REQUESTS.get()).isEqualTo(2);
    }

    @Test void removedKeyExpiresEvenWhenContinuouslyUsed() {
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        keys = fixture.get("new").toString();
        advance(29);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        advance(1);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(401);
        assertThat(get(path, token("rotated")).getStatusCode().value()).isEqualTo(200);
    }

    @Test void unknownKidDuringOutageDoesNotEvictValidKeysOrExtendTheirTtl() {
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        jwksStatus = 503;
        advance(5);
        assertThat(get(path, token("unknown")).getStatusCode().value()).isEqualTo(401);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(200);
        advance(25);
        assertThat(get(path, token("valid")).getStatusCode().value()).isEqualTo(401);
        assertThat(REQUESTS.get()).isEqualTo(3);
    }

    @Test void characterizesUnthrottledSpringNimbusBuilder() throws Exception {
        // Regression evidence for the original builder configuration, independent of our decoder.
        var original = org.springframework.security.oauth2.jwt.NimbusJwtDecoder
            .withJwkSetUri("http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks")
            .cache(new org.springframework.cache.caffeine.CaffeineCache("original-jwks",
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(1)
                    .expireAfterWrite(java.time.Duration.ofSeconds(300)).build()))
            .build();
        original.decode(token("valid"));
        for (int i = 0; i < 10; i++) {
            String jwt = randomKidToken();
            assertThatThrownBy(() -> original.decode(jwt)).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        }
        assertThat(REQUESTS.get()).isEqualTo(11);
    }

    private String randomKidToken() throws Exception {
        String[] pieces = token("valid").split("\\.");
        var header = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Base64.getUrlDecoder().decode(pieces[0]));
        header.put("kid", UUID.randomUUID().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(header))
            + "." + pieces[1] + "." + pieces[2];
    }
    @AfterAll static void close() { JWKS.stop(0); }
}
