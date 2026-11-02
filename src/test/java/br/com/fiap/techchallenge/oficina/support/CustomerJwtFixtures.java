package br.com.fiap.techchallenge.oficina.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Synthetic signatures only. Private keys are ephemeral and never written to disk. */
public final class CustomerJwtFixtures {
    public static final KeyPair CURRENT = keyPair(2048);
    public static final KeyPair NEXT = keyPair(2048);
    public static final String CURRENT_FILE = publicFile(CURRENT);
    public static final String NEXT_FILE = publicFile(NEXT);
    public static final String ISSUER = "https://synthetic-issuer.invalid";
    public static final String AUDIENCE = "oficina-test";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static Map<String, Object> claims(String customerId) {
        long now = Instant.now().getEpochSecond();
        return new HashMap<>(Map.of("sub", customerId, "role", "CUSTOMER", "iss", ISSUER,
            "aud", AUDIENCE, "iat", now, "nbf", now, "exp", now + 900));
    }

    public static String token(String customerId) throws Exception {
        return sign(claims(customerId), Map.of("alg", "RS256", "typ", "JWT", "kid", "current"), CURRENT);
    }

    public static String sign(Map<String, Object> claims, Map<String, Object> header, KeyPair key) throws Exception {
        // Sign raw JSON NumericDate claims, as PyJWT does, independently of JJWT's encoder.
        String content = encode(JSON.writeValueAsBytes(header)) + "." + encode(JSON.writeValueAsBytes(claims));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.getPrivate());
        signature.update(content.getBytes(StandardCharsets.US_ASCII));
        return content + "." + encode(signature.sign());
    }

    public static KeyPair keyPair(int bits) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public static String publicFile(KeyPair key) {
        try {
            Path file = Files.createTempFile("oficina-jwt-public-", ".pem");
            Files.writeString(file, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
            file.toFile().deleteOnExit();
            return file.toString();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private CustomerJwtFixtures() { }
}
