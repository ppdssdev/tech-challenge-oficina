package br.com.fiap.techchallenge.oficina.adapters.out.security;

import br.com.fiap.techchallenge.oficina.configuration.CustomerJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CustomerJwtVerifier {
    public static final long CLOCK_SKEW_SECONDS = 30;
    private final JwtParser parser;
    private final String audience;

    public CustomerJwtVerifier(CustomerJwtProperties properties) {
        audience = properties.audience();
        if (!properties.enabled()) {
            parser = null;
            return;
        }
        if (blank(properties.issuer()) || blank(audience)
            || properties.publicKeys() == null || properties.publicKeys().isEmpty()) {
            throw new IllegalArgumentException("Customer JWT requires issuer, audience and trusted public keys.");
        }
        Map<String, RSAPublicKey> keys = new HashMap<>();
        properties.publicKeys().forEach((kid, file) -> {
            if (!kid.matches("[A-Za-z0-9._-]{1,128}")) {
                throw new IllegalArgumentException("Invalid customer JWT key id.");
            }
            keys.put(kid, readPublicKey(file));
        });
        var trustedKeys = Map.copyOf(keys);
        parser = Jwts.parser()
            .clockSkewSeconds(CLOCK_SKEW_SECONDS)
            .requireIssuer(properties.issuer()).requireAudience(audience)
            .keyLocator(header -> {
                if (!(header instanceof JwsHeader jws) || !"RS256".equals(jws.getAlgorithm())
                    || jws.getKeyId() == null || !trustedKeys.containsKey(jws.getKeyId())
                    || header.containsKey("jku") || header.containsKey("jwk")
                    || header.containsKey("x5u") || header.containsKey("zip")) {
                    throw new JwtException("Untrusted customer JWT header.");
                }
                return trustedKeys.get(jws.getKeyId());
            }).build();
    }

    public String verify(String token) {
        if (parser == null) throw new JwtException("Customer JWT is disabled.");
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (!"CUSTOMER".equals(claims.get("role")) || claims.containsKey("roles")
            || claims.containsKey("authorities") || !Set.of(audience).equals(claims.getAudience())) {
            throw new JwtException("Invalid customer JWT identity.");
        }
        var issued = claims.getIssuedAt();
        var notBefore = claims.getNotBefore();
        var expires = claims.getExpiration();
        if (issued == null || notBefore == null || expires == null
            || issued.toInstant().isAfter(Instant.now().plusSeconds(CLOCK_SKEW_SECONDS))
            || !issued.equals(notBefore) || expires.getTime() <= issued.getTime()
            || Duration.between(issued.toInstant(), expires.toInstant()).compareTo(Duration.ofSeconds(900)) > 0) {
            throw new JwtException("Invalid customer JWT lifetime.");
        }
        String subject = claims.getSubject();
        try {
            if (subject == null || !UUID.fromString(subject).toString().equals(subject)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new JwtException("Invalid customer JWT subject.");
        }
        return subject;
    }

    private static RSAPublicKey readPublicKey(String file) {
        try {
            String pem = Files.readString(Path.of(file)).trim();
            if (!pem.startsWith("-----BEGIN PUBLIC KEY-----") || !pem.endsWith("-----END PUBLIC KEY-----")) {
                throw new IllegalArgumentException("Expected an X.509 public key PEM.");
            }
            byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", ""));
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA public key must have at least 2048 bits.");
            }
            return rsa;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot load trusted customer RSA public key.", exception);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
