package br.com.fiap.techchallenge.oficina.configuration;

import br.com.fiap.techchallenge.oficina.adapters.out.security.CustomerIdentity;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CustomerSecurityConfig {
    @Bean
    JwtDecoder customerDecoder(Environment environment,
        @Value("${app.customer-auth.jwks-uri:}") String jwks,
        @Value("${app.customer-auth.issuer:}") String issuer,
        @Value("${app.customer-auth.audience:}") String audience,
        @Value("${app.customer-auth.clock-skew-seconds:60}") long skew,
        @Value("${app.customer-auth.cache-seconds:300}") long cacheSeconds) {
        boolean local = environment.acceptsProfiles(Profiles.of("local", "test", "it"))
            && !environment.acceptsProfiles(Profiles.of("production", "staging", "cloud"));
        if (jwks.isBlank() && issuer.isBlank() && audience.isBlank() && local) {
            return token -> { throw new BadJwtException("CUSTOMER authentication not configured"); };
        }
        if (audience.isBlank() || skew < 0 || skew > 60 || cacheSeconds < 1 || cacheSeconds > 300)
            throw new IllegalArgumentException("Invalid CUSTOMER trust configuration");
        validateUri(jwks, local);
        validateUri(issuer, local);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(new RestTemplate(factory))
            .cache(new CaffeineCache("customer-jwks", Caffeine.newBuilder().maximumSize(1)
                .expireAfterWrite(Duration.ofSeconds(cacheSeconds)).build()))
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(skew)), new JwtIssuerValidator(issuer),
            jwt -> validateContract(jwt, audience, skew)));
        // Treat inability to obtain a trusted key as invalid credentials, without error dispatch
        // into the legacy chain or exposing upstream JWKS response bodies to the client.
        return token -> {
            try { return decoder.decode(token); }
            catch (JwtException failure) { throw new BadJwtException("Unable to validate CUSTOMER token", failure); }
        };
    }

    private static void validateUri(String value, boolean local) {
        var uri = URI.create(value);
        boolean loopback = uri.getHost() != null && List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
            || !("https".equals(uri.getScheme()) || local && loopback && "http".equals(uri.getScheme())))
            throw new IllegalArgumentException("Explicit trusted HTTPS issuer and JWKS URI required");
    }

    private static OAuth2TokenValidatorResult validateContract(Jwt jwt, String audience, long skew) {
        try {
            String sub = jwt.getSubject();
            String kid = (String) jwt.getHeaders().get("kid");
            if (!"RS256".equals(jwt.getHeaders().get("alg")) || kid == null || !kid.matches("[A-Za-z0-9._-]{1,128}")
                || sub == null || !UUID.fromString(sub).toString().equals(sub)
                || !"CUSTOMER".equals(jwt.getClaims().get("role")) || !jwt.getAudience().contains(audience)
                || jwt.getIssuedAt() == null || jwt.getNotBefore() == null || jwt.getExpiresAt() == null
                || jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(skew))
                || jwt.getNotBefore().isBefore(jwt.getIssuedAt())
                || !jwt.getExpiresAt().isAfter(jwt.getNotBefore())
                || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() > 900)
                throw new IllegalArgumentException();
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException invalid) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid CUSTOMER token", null));
        }
    }

    @Bean
    @Order(1)
    SecurityFilterChain customerChain(HttpSecurity http, JwtDecoder customerDecoder) throws Exception {
        return http.securityMatcher("/api/v1/customer/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().hasRole("CUSTOMER"))
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(customerDecoder).jwtAuthenticationConverter(jwt ->
                UsernamePasswordAuthenticationToken.authenticated(
                    new CustomerIdentity(UUID.fromString(jwt.getSubject())), null,
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
            .build();
    }
}
