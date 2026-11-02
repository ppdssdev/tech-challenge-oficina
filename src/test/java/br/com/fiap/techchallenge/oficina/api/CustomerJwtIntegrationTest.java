package br.com.fiap.techchallenge.oficina.api;

import br.com.fiap.techchallenge.oficina.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static br.com.fiap.techchallenge.oficina.support.CustomerJwtFixtures.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class CustomerJwtIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    br.com.fiap.techchallenge.oficina.adapters.out.security.WorkOrderAuthorization ownership;
    @Value("${app.jwt.secret}") String adminSecret;
    @Value("${app.admin.default-username}") String adminUsername;
    @Value("${app.admin.default-password}") String adminPassword;

    @DynamicPropertySource
    static void customerConfiguration(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.customer.enabled", () -> true);
        registry.add("app.jwt.customer.issuer", () -> ISSUER);
        registry.add("app.jwt.customer.audience", () -> AUDIENCE);
        registry.add("app.jwt.customer.public-keys.current", () -> CURRENT_FILE);
        registry.add("app.jwt.customer.public-keys.next", () -> NEXT_FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "budget/approve", "budget/reject"})
    void realCustomerTokenOnlyAccessesOwnWorkOrder(String operation) throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", adminUsername, "password", adminPassword))))
            .andExpect(status().isOk()).andReturn();
        String adminToken = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        var service = mvc.perform(post("/api/v1/admin/services").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Synthetic JWT test", "basePrice":10, "estimatedMinutes":10, "active":true}
                """)).andExpect(status().isCreated()).andReturn();
        String serviceId = json.readTree(service.getResponse().getContentAsString()).get("id").asText();
        var created = mvc.perform(post("/api/v1/admin/work-orders").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"customer":{"fullName":"Synthetic customer", "documentType":"CPF", "documentNumber":"52998224725"},
                 "vehicle":{"plate":"ABC1D23", "brand":"Test", "model":"Test", "manufacturingYear":2020},
                 "services":[{"serviceId":"%s", "quantity":1}], "parts":[]}
                """.formatted(serviceId))).andExpect(status().isCreated()).andReturn();
        var order = json.readTree(created.getResponse().getContentAsString());
        String customer = order.get("customerId").asText();
        String path = "/api/v1/public/work-orders/" + order.get("code").asText() + "/" + operation;
        var method = HttpMethod.POST;
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + token(customer)))
            .andExpect(status().isForbidden());
        mvc.perform(request(method, path).header("Authorization", "Bearer " + token(UUID.randomUUID().toString()))
            .param("document", "52998224725").param("customerId", customer).param("role", "ADMIN")
            .contentType(MediaType.APPLICATION_JSON).content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(request(method, path).header("Authorization", "Bearer " + token(customer))
            .param("document", "52998224725").contentType(MediaType.APPLICATION_JSON)
            .content("{\"document\":\"52998224725\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value(operation.equals("status") ? "WAITING_APPROVAL"
                : operation.equals("budget/approve") ? "IN_EXECUTION" : "BUDGET_REJECTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "roles", "authorities", "kid", "missing-kid", "issuer", "audience", "extra-audience",
        "expired", "nbf", "iat", "missing-exp", "missing-nbf", "missing-iat", "missing-role", "missing-sub",
        "missing-iss", "missing-aud", "subject-admin", "subject-short", "ttl", "reverse-time", "signature", "unsigned",
        "RS512", "PS256", "HS256", "jku", "jwk", "x5u", "tampered", "date-type"})
    void incompatibleTokensAreUnauthorized(String scenario) throws Exception {
        var claims = claims(UUID.randomUUID().toString());
        var header = new HashMap<String, Object>(Map.of("alg", "RS256", "kid", "current"));
        var key = CURRENT;
        long now = (long) claims.get("iat");
        switch (scenario) {
            case "role" -> claims.put("role", "ADMIN");
            case "roles" -> claims.put("roles", List.of("ROLE_ADMIN"));
            case "authorities" -> claims.put("authorities", List.of("ROLE_ADMIN"));
            case "kid" -> header.put("kid", "unknown");
            case "missing-kid" -> header.remove("kid");
            case "issuer" -> claims.put("iss", "https://other.invalid");
            case "audience" -> claims.put("aud", "other");
            case "extra-audience" -> claims.put("aud", List.of(AUDIENCE, "other"));
            case "expired" -> { claims.put("iat", now - 1000); claims.put("nbf", now - 1000); claims.put("exp", now - 100); }
            case "nbf" -> claims.put("nbf", now + 120);
            case "iat" -> claims.put("iat", now + 120);
            case "missing-exp", "missing-nbf", "missing-iat", "missing-role", "missing-sub", "missing-iss", "missing-aud" ->
                claims.remove(scenario.substring(8));
            case "subject-admin" -> claims.put("sub", adminUsername);
            case "subject-short" -> claims.put("sub", "1-1-1-1-1");
            case "ttl" -> claims.put("exp", now + 901);
            case "reverse-time" -> claims.put("exp", now);
            case "signature" -> key = NEXT;
            case "unsigned" -> header.put("alg", "none");
            case "RS512", "PS256", "HS256" -> header.put("alg", scenario);
            case "jku", "x5u" -> header.put(scenario, "https://untrusted.invalid/keys");
            case "jwk" -> header.put("jwk", Map.of("kty", "RSA"));
            case "date-type" -> claims.put("iat", "invalid-date");
            default -> { }
        }
        String token = sign(claims, header, key);
        if (scenario.equals("unsigned")) token = token.substring(0, token.lastIndexOf('.') + 1);
        if (scenario.equals("tampered")) {
            String[] pieces = token.split("\\.");
            claims.put("sub", UUID.randomUUID().toString());
            pieces[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(claims));
            token = String.join(".", pieces);
        }
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void knownRotationKeyAndClockSkewAreAcceptedWithoutAdministrativeLookup() throws Exception {
        var claims = claims(UUID.randomUUID().toString());
        long now = (long) claims.get("iat");
        claims.put("iat", now + 10); claims.put("nbf", now + 10); claims.put("exp", now + 910);
        String rotated = sign(claims, Map.of("alg", "RS256", "kid", "next"), NEXT);
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + rotated))
            .andExpect(status().isForbidden()); // Authenticated CUSTOMER, despite no app_users record.
    }

    @Test
    void realPythonSignerAuthenticatesAsCustomer() throws Exception {
        String repository = System.getProperty("lambda.repo",
            System.getProperty("user.home") + "/tech-challenge-oficina-auth-lambda");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(
            java.nio.file.Path.of(repository, "auth", "jwt.py")), "Lambda source unavailable; set -Dlambda.repo");
        var process = new ProcessBuilder(System.getProperty("lambda.python", "python3"), "-B",
            "scripts/staging/python-interop-token.py", repository).start();
        String subject = UUID.randomUUID().toString();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getEncoder()
            .encodeToString(CURRENT.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        try (var input = process.getOutputStream()) {
            input.write(json.writeValueAsBytes(Map.of("private_pem", pem, "issuer", ISSUER,
                "audience", AUDIENCE, "kid", "current", "customer_id", subject)));
        }
        if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Python signer timed out");
        }
        org.assertj.core.api.Assertions.assertThat(process.exitValue()).as("Python signer exit code").isZero();
        String token = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        // Inspect the real verified principal as well as authorization, without exposing tokens.
        var verifier = new br.com.fiap.techchallenge.oficina.adapters.out.security.CustomerJwtVerifier(
            new br.com.fiap.techchallenge.oficina.configuration.CustomerJwtProperties(
                true, ISSUER, AUDIENCE, Map.of("current", CURRENT_FILE)));
        org.assertj.core.api.Assertions.assertThat(verifier.verify(token)).isEqualTo(subject);
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/public/work-orders/UNKNOWN/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"document\":\"52998224725\"}")
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        var identity = org.mockito.ArgumentCaptor.forClass(org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.verify(ownership).canAccess(identity.capture(), org.mockito.ArgumentMatchers.eq("UNKNOWN"));
        org.assertj.core.api.Assertions.assertThat(identity.getValue().getName()).isEqualTo(subject);
        org.assertj.core.api.Assertions.assertThat(identity.getValue().getAuthorities())
            .extracting(org.springframework.security.core.GrantedAuthority::getAuthority).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void administrativeUsernameCollisionNeverChangesCustomerAuthority() throws Exception {
        String subject = UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO app_users(id, username, password_hash, role, created_at, updated_at)
            VALUES (?, ?, 'unused-synthetic-hash', 'ADMIN', now(), now())
            """, UUID.randomUUID(), subject);
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + token(subject)))
            .andExpect(status().isForbidden());
        // Even an administrative roles claim cannot promote a persisted CUSTOMER via HMAC.
        jdbcTemplate.update("UPDATE app_users SET role = 'CUSTOMER' WHERE username = ?", subject);
        String hmac = Jwts.builder().subject(subject).claim("roles", List.of("ROLE_ADMIN"))
            .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(adminSecret.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/v1/admin/customers").header("Authorization", "Bearer " + hmac))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void signedHmacCannotClaimCustomerOrUsePublicRsaBytesAsSecret() throws Exception {
        for (byte[] secret : new byte[][]{adminSecret.getBytes(StandardCharsets.UTF_8), CURRENT.getPublic().getEncoded()}) {
            String token = Jwts.builder().claims(claims(UUID.randomUUID().toString()))
                .signWith(Keys.hmacShaKeyFor(secret), Jwts.SIG.HS256).compact();
            mvc.perform(post("/api/v1/public/work-orders/UNKNOWN/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"document\":\"52998224725\"}")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }
}
