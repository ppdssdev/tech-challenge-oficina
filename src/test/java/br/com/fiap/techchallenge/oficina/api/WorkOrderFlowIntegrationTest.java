package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.OficinaApplication;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OficinaApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkOrderFlowIntegrationTest {

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

        ResponseEntity<Map> createOrderResponse = createOrder(adminHeaders, serviceId, partId);

        assertThat(createOrderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createOrderResponse.getBody()).isNotNull();
        assertThat(createOrderResponse.getBody().get("status")).isEqualTo("WAITING_APPROVAL");
        assertThat(new BigDecimal(createOrderResponse.getBody().get("totalAmount").toString())).isEqualByComparingTo("190.00");

        String orderId = createOrderResponse.getBody().get("id").toString();
        String code = createOrderResponse.getBody().get("code").toString();

        ResponseEntity<Map> approved = postWithoutBody("/api/v1/admin/work-orders/" + orderId + "/approve", adminHeaders);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("IN_EXECUTION");

        ResponseEntity<Map> finished = postWithoutBody("/api/v1/admin/work-orders/" + orderId + "/finish", adminHeaders);
        assertThat(finished.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finished.getBody().get("status")).isEqualTo("FINALIZED");

        ResponseEntity<Map> delivered = postWithoutBody("/api/v1/admin/work-orders/" + orderId + "/deliver", adminHeaders);
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(delivered.getBody().get("status")).isEqualTo("DELIVERED");

        ResponseEntity<Map> publicStatus = restTemplate.getForEntity(
            url("/api/v1/public/work-orders/" + code + "/status?document=52998224725"), Map.class
        );
        assertThat(publicStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicStatus.getBody().get("status")).isEqualTo("DELIVERED");
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
        ResponseEntity<Map> createOrderResponse = createOrder(adminHeaders, serviceId, partId);
        String code = createOrderResponse.getBody().get("code").toString();

        ResponseEntity<Map> publicStatus = restTemplate.getForEntity(
            url("/api/v1/public/work-orders/" + code + "/status?document=00000000000"), Map.class
        );

        assertThat(publicStatus.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.exchange(
            url("/api/v1/auth/login"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), headers),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("accessToken").toString();
    }

    private UUID createService(HttpHeaders headers) {
        ResponseEntity<Map> response = restTemplate.exchange(
            url("/api/v1/admin/services"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "name", "Troca de óleo",
                "description", "Troca de óleo do motor",
                "basePrice", 120.00,
                "estimatedMinutes", 40,
                "active", true
            ), headers),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private ResponseEntity<Map> createOrder(HttpHeaders headers, UUID serviceId, UUID partId) {
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
            url("/api/v1/admin/work-orders"), HttpMethod.POST, new HttpEntity<>(createOrderPayload, headers), Map.class
        );
    }

    private UUID createPart(HttpHeaders headers) {
        ResponseEntity<Map> response = restTemplate.exchange(
            url("/api/v1/admin/parts"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "name", "Filtro de óleo",
                "sku", "FILTER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "unitPrice", 35.00,
                "quantityInStock", 10,
                "minimumStock", 2,
                "active", true
            ), headers),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private ResponseEntity<Map> postWithoutBody(String path, HttpHeaders headers) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(headers), Map.class);
    }

    private HttpHeaders adminHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
