package br.com.fiap.techchallenge.oficina.observability;

import br.com.fiap.techchallenge.oficina.adapters.in.web.RequestCompletionLoggingFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "management.tracing.sampling.probability=1.0",
    "management.otlp.tracing.export.enabled=true",
    "management.tracing.opentelemetry.export.schedule-delay=100ms",
    "management.health.mail.enabled=false"
})
@ActiveProfiles("it")
@AutoConfigureObservability
class TracingIntegrationTest extends br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport {
    static final LinkedBlockingQueue<byte[]> exports = new LinkedBlockingQueue<>();
    static final HttpServer receiver = receiver();
    static volatile int receiverStatus = 200;
    @LocalServerPort int port;
    @Autowired SdkTracerProvider provider;
    @Autowired io.micrometer.observation.ObservationRegistry observations;
    final ObjectMapper json = new ObjectMapper();

    static HttpServer receiver() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/traces", exchange -> {
                if ("application/x-protobuf".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                    exports.add(exchange.getRequestBody().readAllBytes());
                }
                exchange.sendResponseHeaders(receiverStatus, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("management.otlp.tracing.endpoint", () ->
            "http://127.0.0.1:" + receiver.getAddress().getPort() + "/v1/traces");
    }

    @AfterAll static void closeReceiver() { receiver.stop(0); }

    @Test
    void realHttpRequestExportsSpanMatchingJsonLogAndErrorResponseWithoutSecrets() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);
        var output = new ByteArrayOutputStream();
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var console = (OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE");
        assertThat(console.getEncoder()).isInstanceOf(StructuredLogEncoder.class);
        var appender = new OutputStreamAppender<ILoggingEvent>();
        appender.setContext(logger.getLoggerContext());
        appender.setEncoder(console.getEncoder());
        appender.setOutputStream(output);
        appender.start();
        logger.addAppender(appender);
        try {
            var client = HttpClient.newHttpClient();
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/api/v1/auth/login?cpf=52998224725&token=sensitive-query"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", "e53eeffe-36c7-46bb-a545-1a2056d243bf")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"\",\"password\":\"sensitive-password\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
            JsonNode log = json.readTree(output.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("HTTP request completed")).findFirst().orElseThrow());
            String traceId = log.path("traceId").asText();
            String spanId = log.path("spanId").asText();
            assertThat(traceId).matches("[0-9a-f]{32}");
            assertThat(spanId).matches("[0-9a-f]{16}");
            assertThat(json.readTree(response.body()).path("traceId").asText()).isEqualTo(traceId);
            var denied = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("Authorization", "Bearer sensitive-jwt").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isEqualTo(401);
            provider.forceFlush().join(5, TimeUnit.SECONDS);
            byte[] first = exports.poll(5, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            var exported = new ByteArrayOutputStream();
            exported.write(first);
            byte[] next;
            while ((next = exports.poll()) != null) exported.write(next);
            byte[] payload = exported.toByteArray();
            assertThat(payload).isNotNull();
            assertThat(HexFormat.of().formatHex(payload)).contains(traceId, spanId);
            assertThat(new String(payload, StandardCharsets.UTF_8)).contains("service.name", "oficina-api")
                .doesNotContain("52998224725", "sensitive-query", "sensitive-password", "sensitive-jwt");
            assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain(
                "52998224725", "sensitive-query", "sensitive-password", "sensitive-jwt", "e53eeffe");

            var receiverContext = new io.micrometer.observation.transport.ReceiverContext<String>((carrier, key) -> null);
            receiverContext.setCarrier("local-test");
            var observation = io.micrometer.observation.Observation.start("privacy.receiver", () -> receiverContext, observations);
            observation.error(new IllegalStateException("sensitive-exception 52998224725 sensitive-jwt"));
            observation.stop();
            provider.forceFlush().join(5, TimeUnit.SECONDS);
            byte[] errorPayload = exports.poll(5, TimeUnit.SECONDS);
            assertThat(errorPayload).isNotNull();
            assertThat(new String(errorPayload, StandardCharsets.UTF_8)).contains("IllegalStateException")
                .doesNotContain("sensitive-exception", "52998224725", "sensitive-jwt");

            receiverStatus = 503;
            var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            provider.forceFlush().join(5, TimeUnit.SECONDS);
            receiver.stop(0);
            var unavailable = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(unavailable.statusCode()).isEqualTo(200);
            provider.forceFlush().join(5, TimeUnit.SECONDS);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
