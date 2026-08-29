package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.OficinaApplication;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OficinaApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkOrderFlowIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
        new ParameterizedTypeReference<>() { };

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldRunMainWorkOrderFlowThroughRestApi() {
        String token = login();
        HttpHeaders adminHeaders = adminHeaders(token);

        UUID serviceId = createService(adminHeaders);
        UUID partId = createPart(adminHeaders);

        ResponseEntity<Map<String, Object>> createOrderResponse = createOrder(adminHeaders, serviceId, partId);

        assertThat(createOrderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(requiredValue(createOrderResponse, "status")).isEqualTo("WAITING_APPROVAL");
        assertThat(new BigDecimal(requiredValue(createOrderResponse, "totalAmount").toString())).isEqualByComparingTo("190.00");

        String orderId = requiredValue(createOrderResponse, "id").toString();
        String code = requiredValue(createOrderResponse, "code").toString();

        ResponseEntity<Map<String, Object>> approved = postWithoutBody("/api/v1/admin/work-orders/" + orderId + "/approve", adminHeaders);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(approved, "status")).isEqualTo("IN_EXECUTION");

        ResponseEntity<Map<String, Object>> finished = postWithoutBody("/api/v1/admin/work-orders/" + orderId + "/finish", adminHeaders);
        assertThat(finished.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(finished, "status")).isEqualTo("FINALIZED");

        ResponseEntity<Map<String, Object>> delivered = postWithoutBody("/api/v1/admin/work-orders/" + orderId + "/deliver", adminHeaders);
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(delivered, "status")).isEqualTo("DELIVERED");

        ResponseEntity<Map<String, Object>> publicStatus = restTemplate.exchange(
            url("/api/v1/public/work-orders/" + code + "/status?document=52998224725"),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JSON_OBJECT
        );
        assertThat(publicStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(publicStatus, "status")).isEqualTo("DELIVERED");
    }

    @Test
    void shouldRequireJwtForAdministrativeWorkOrderEndpoints() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            url("/api/v1/admin/work-orders"), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldNotExposePublicWorkOrderStatusForDifferentDocument() {
        String token = login();
        HttpHeaders adminHeaders = adminHeaders(token);
        UUID serviceId = createService(adminHeaders);
        UUID partId = createPart(adminHeaders);
        ResponseEntity<Map<String, Object>> createOrderResponse = createOrder(adminHeaders, serviceId, partId);
        String code = requiredValue(createOrderResponse, "code").toString();

        ResponseEntity<Map<String, Object>> publicStatus = restTemplate.exchange(
            url("/api/v1/public/work-orders/" + code + "/status?document=00000000000"),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JSON_OBJECT
        );

        assertThat(publicStatus.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReadApprovedWorkOrderAfterConsumingAllPartStock() {
        String token = login();
        HttpHeaders adminHeaders = adminHeaders(token);
        UUID serviceId = createService(adminHeaders);
        UUID partId = createPart(adminHeaders, 2);
        ResponseEntity<Map<String, Object>> created = createOrder(adminHeaders, serviceId, partId);
        String orderId = requiredValue(created, "id").toString();

        ResponseEntity<Map<String, Object>> approved = postWithoutBody(
            "/api/v1/admin/work-orders/" + orderId + "/approve", adminHeaders
        );

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(approved, "status")).isEqualTo("IN_EXECUTION");

        ResponseEntity<Map<String, Object>> detail = restTemplate.exchange(
            url("/api/v1/admin/work-orders/" + orderId),
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            JSON_OBJECT
        );
        ResponseEntity<Map<String, Object>> part = restTemplate.exchange(
            url("/api/v1/admin/parts/" + partId),
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            JSON_OBJECT
        );

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(detail, "status")).isEqualTo("IN_EXECUTION");
        assertThat(requiredValue(part, "quantityInStock")).isEqualTo(0);
    }

    @Test
    void shouldNotifyAndApproveBudgetThroughPublicEndpointWithoutJwt() {
        String token = login();
        HttpHeaders adminHeaders = adminHeaders(token);
        UUID serviceId = createService(adminHeaders);
        UUID partId = createPart(adminHeaders);
        ResponseEntity<Map<String, Object>> created = createOrder(adminHeaders, serviceId, partId);
        String orderId = requiredValue(created, "id").toString();
        String code = requiredValue(created, "code").toString();

        ResponseEntity<Map<String, Object>> notification = postWithoutBody(
            "/api/v1/admin/work-orders/" + orderId + "/budget/notify", adminHeaders
        );
        assertThat(notification.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(notification, "channel")).isEqualTo("SIMULATED_EMAIL");
        assertThat(requiredValue(notification, "recipient")).isEqualTo("maria@email.com");
        assertThat(requiredValue(notification, "subject").toString()).contains(code);
        assertThat(requiredValue(notification, "body").toString())
            .contains("\"document\": \"seu CPF ou CNPJ\"");
        assertThat(requiredValue(notification, "rejectUrl").toString())
            .endsWith("/api/v1/public/work-orders/" + code + "/budget/reject");
        String approvePath = URI.create(requiredValue(notification, "approveUrl").toString()).getPath();

        ResponseEntity<Map<String, Object>> approved = postPublicBudgetDecision(
            approvePath,
            Map.of("document", "529.982.247-25")
        );

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(approved, "status")).isEqualTo("IN_EXECUTION");

        ResponseEntity<Map<String, Object>> publicStatus = restTemplate.exchange(
            url("/api/v1/public/work-orders/" + code + "/status?document=52998224725"),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JSON_OBJECT
        );
        assertThat(publicStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(publicStatus, "status")).isEqualTo("IN_EXECUTION");
    }

    @Test
    void shouldRequireJwtToNotifyBudget() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/v1/admin/work-orders/" + UUID.randomUUID() + "/budget/notify"),
            HttpMethod.POST,
            HttpEntity.EMPTY,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectBudgetThroughPublicEndpointWithoutJwt() {
        String token = login();
        HttpHeaders adminHeaders = adminHeaders(token);
        UUID serviceId = createService(adminHeaders);
        UUID partId = createPart(adminHeaders);
        ResponseEntity<Map<String, Object>> created = createOrder(adminHeaders, serviceId, partId);
        String code = requiredValue(created, "code").toString();

        ResponseEntity<Map<String, Object>> rejected = postPublicBudgetDecision(
            "/api/v1/public/work-orders/" + code + "/budget/reject",
            Map.of("document", "52998224725")
        );

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requiredValue(rejected, "status")).isEqualTo("BUDGET_REJECTED");
    }

    @Test
    void shouldHideBudgetDecisionForDifferentDocument() {
        String token = login();
        HttpHeaders adminHeaders = adminHeaders(token);
        UUID serviceId = createService(adminHeaders);
        UUID partId = createPart(adminHeaders);
        ResponseEntity<Map<String, Object>> created = createOrder(adminHeaders, serviceId, partId);
        String code = requiredValue(created, "code").toString();

        ResponseEntity<Map<String, Object>> response = postPublicBudgetDecision(
            "/api/v1/public/work-orders/" + code + "/budget/approve",
            Map.of("document", "00000000000")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectPublicBudgetDecisionPayloadWithoutDocument() {
        ResponseEntity<Map<String, Object>> response = postPublicBudgetDecision(
            "/api/v1/public/work-orders/OS-INEXISTENTE/budget/approve",
            Map.of()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url("/api/v1/auth/login"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), headers),
            JSON_OBJECT
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return requiredValue(response, "accessToken").toString();
    }

    private UUID createService(HttpHeaders headers) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url("/api/v1/admin/services"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "name", "Troca de óleo",
                "description", "Troca de óleo do motor",
                "basePrice", 120.00,
                "estimatedMinutes", 40,
                "active", true
            ), headers),
            JSON_OBJECT
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(requiredValue(response, "id").toString());
    }

    private ResponseEntity<Map<String, Object>> createOrder(HttpHeaders headers, UUID serviceId, UUID partId) {
        Map<String, Object> createOrderPayload = Map.of(
            "customer", Map.of(
                "fullName", "Maria Silva",
                "documentType", "CPF",
                "documentNumber", "529.982.247-25",
                "email", "maria@email.com",
                "phone", "31999999999"
            ),
            "vehicle", Map.of(
                "plate", "ABC1D23",
                "brand", "Fiat",
                "model", "Argo",
                "manufacturingYear", 2020
            ),
            "services", List.of(Map.of("serviceId", serviceId.toString(), "quantity", 1)),
            "parts", List.of(Map.of("partId", partId.toString(), "quantity", 2)),
            "diagnosticNotes", "Troca preventiva solicitada pelo cliente."
        );

        return restTemplate.exchange(
            url("/api/v1/admin/work-orders"), HttpMethod.POST, new HttpEntity<>(createOrderPayload, headers), JSON_OBJECT
        );
    }

    private UUID createPart(HttpHeaders headers) {
        return createPart(headers, 10);
    }

    private UUID createPart(HttpHeaders headers, int quantityInStock) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url("/api/v1/admin/parts"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "name", "Filtro de óleo",
                "sku", "FILTER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "unitPrice", 35.00,
                "quantityInStock", quantityInStock,
                "minimumStock", 2,
                "active", true
            ), headers),
            JSON_OBJECT
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(requiredValue(response, "id").toString());
    }

    private ResponseEntity<Map<String, Object>> postWithoutBody(String path, HttpHeaders headers) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(headers), JSON_OBJECT);
    }

    private ResponseEntity<Map<String, Object>> postPublicBudgetDecision(
        String path,
        Map<String, Object> payload
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
            url(path), HttpMethod.POST, new HttpEntity<>(payload, headers), JSON_OBJECT
        );
    }

    private HttpHeaders adminHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private static Object requiredValue(ResponseEntity<Map<String, Object>> response, String field) {
        Map<String, Object> body = Objects.requireNonNull(response.getBody(), "Resposta sem corpo");
        return Objects.requireNonNull(body.get(field), "Campo ausente na resposta: " + field);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
