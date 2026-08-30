package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorSecurityIntegrationTest extends PostgresIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldExposeHealthWithoutJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldExposePrometheusMetricsWithoutJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .contains("jvm_memory_used_bytes")
            .contains("process_cpu_usage")
            .contains("oficina_notification_outbox_pending")
            .contains("oficina_notification_outbox_processing")
            .contains("oficina_notification_outbox_failed");
    }

    @Test
    void shouldKeepAdministrativeEndpointsProtected() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            url("/api/v1/admin/work-orders"), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
